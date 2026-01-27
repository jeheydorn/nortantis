package nortantis.platform.skia;

import nortantis.platform.*;
import nortantis.util.Logger;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.EncodedImageFormat;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.ImageInfo;
import org.jetbrains.skia.Typeface;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkiaFactory extends PlatformFactory
{
	private final ExecutorService executor = Executors.newCachedThreadPool();

	@Override
	public nortantis.platform.Image createImage(int width, int height, ImageType type)
	{
		return new SkiaImage(width, height, type);
	}

	@Override
	public nortantis.platform.Image readImage(String filePath)
	{
		try (InputStream stream = new FileInputStream(filePath))
		{
			return readImage(stream);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Can't read the file " + filePath, e);
		}
	}

	@Override
	public nortantis.platform.Image readImage(InputStream stream)
	{
		try
		{
			byte[] bytes = stream.readAllBytes();
			org.jetbrains.skia.Image image = org.jetbrains.skia.Image.Companion.makeFromEncoded(bytes);

			// Create a mutable bitmap with allocated pixels using Image.readPixels.
			// This is more reliable than drawing through a Canvas, which can leave
			// the bitmap in a state where Image.makeFromBitmap fails later.
			Bitmap bitmap = new Bitmap();
			ImageInfo imageInfo = SkiaImage.getImageInfoForType(ImageType.ARGB, image.getWidth(), image.getHeight());
			bitmap.allocPixels(imageInfo);

			// Use readPixels to directly copy pixel data from image to bitmap
			boolean readSuccess = image.readPixels(bitmap, 0, 0);
			if (!readSuccess)
			{
				Logger.printError("WARNING: image.readPixels failed for " + image.getWidth() + "x" + image.getHeight() + " image");
			}
			image.close();

			return new SkiaImage(bitmap, ImageType.ARGB);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public void writeImage(nortantis.platform.Image image, String filePath)
	{
		try
		{
			String extension = FilenameUtils.getExtension(filePath).toLowerCase();
			SkiaImage skiaImage = (SkiaImage) image;

			if (extension.equals("png"))
			{
				// Use Java ImageIO for PNG - Skia's PNG encoder is very slow
				writeImageWithImageIO(skiaImage, filePath, "png");
			}
			else if (extension.equals("jpg") || extension.equals("jpeg"))
			{
				// Use Skia for JPEG
				org.jetbrains.skia.Image skImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(skiaImage.getBitmap());
				byte[] bytes = skImage.encodeToData(EncodedImageFormat.JPEG, 95).getBytes();
				Files.write(Paths.get(filePath), bytes);
				skImage.close();
			}
			else
			{
				// Default: use ImageIO for other formats
				writeImageWithImageIO(skiaImage, filePath, extension);
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Writes a SkiaImage using Java's ImageIO. This is faster than Skia's encoder for PNG.
	 */
	private void writeImageWithImageIO(SkiaImage skiaImage, String filePath, String format) throws Exception
	{
		int width = skiaImage.getWidth();
		int height = skiaImage.getHeight();

		// Determine BufferedImage type based on image type
		int bufferedImageType = skiaImage.getType() == ImageType.ARGB ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage bufferedImage = new BufferedImage(width, height, bufferedImageType);

		// Read pixels from Skia bitmap as byte array (BGRA order)
		byte[] pixelBytes = skiaImage.readPixelsToByteArray(null);

		// Convert to int array for BufferedImage
		int[] pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();

		// Skia uses BGRA byte order, BufferedImage uses ARGB int order
		// We need to swizzle the bytes
		for (int i = 0; i < width * height; i++)
		{
			int byteIdx = i * 4;
			int b = pixelBytes[byteIdx] & 0xFF;
			int g = pixelBytes[byteIdx + 1] & 0xFF;
			int r = pixelBytes[byteIdx + 2] & 0xFF;
			int a = pixelBytes[byteIdx + 3] & 0xFF;
			pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}

		ImageIO.write(bufferedImage, format, new File(filePath));
	}

	@Override
	public boolean isFontInstalled(String fontFamily)
	{
		// Map logical font names to actual font names
		String mappedName = SkiaFont.mapFontName(fontFamily);

		// Try to find the font family in the system
		Typeface typeface = FontMgr.Companion.getDefault().matchFamilyStyle(mappedName, org.jetbrains.skia.FontStyle.Companion.getNORMAL());
		if (typeface == null)
		{
			return false;
		}

		// Check if we got a fallback font instead of the requested one.
		// If the family name doesn't match what we asked for, the font isn't truly installed.
		String actualFamily = typeface.getFamilyName();
		return actualFamily != null && actualFamily.equalsIgnoreCase(mappedName);
	}

	@Override
	public Font createFont(String name, FontStyle style, float size)
	{
		return new SkiaFont(name, style, size);
	}

	@Override
	public Color createColor(int rgb, boolean hasAlpha)
	{
		return new SkiaColor(rgb, hasAlpha);
	}

	@Override
	public Color createColor(int red, int green, int blue)
	{
		return new SkiaColor(red, green, blue);
	}

	@Override
	public Color createColor(float red, float green, float blue)
	{
		return new SkiaColor(red, green, blue);
	}

	@Override
	public Color createColor(int red, int green, int blue, int alpha)
	{
		return new SkiaColor(red, green, blue, alpha);
	}

	@Override
	public Color createColorFromHSB(float hue, float saturation, float brightness)
	{
		return new SkiaColor(java.awt.Color.HSBtoRGB(hue, saturation, brightness), false);
	}

	@Override
	public <T> void doInBackgroundThread(BackgroundTask<T> task)
	{
		executor.submit(() ->
		{
			try
			{
				T result = task.doInBackground();
				doInMainUIThreadAsynchronous(() -> task.done(result));
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});
	}

	@Override
	public void doInMainUIThreadAsynchronous(Runnable toRun)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			toRun.run();
		}
		else
		{
			SwingUtilities.invokeLater(toRun);
		}
	}
}
