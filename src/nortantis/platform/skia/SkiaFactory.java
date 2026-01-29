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
	public nortantis.platform.Image createImage(int width, int height, ImageType type, boolean forceCPU)
	{
		return new SkiaImage(width, height, type, forceCPU);
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
			SkiaImage skiaImage = (SkiaImage) image;
			org.jetbrains.skia.Image skImage = skiaImage.makeEncodableImage();
			// TODO Consider making compression / quality an options, although they will need to be handled differently for JPEG vs PNG.
			byte[] bytes = skImage.encodeToData(EncodedImageFormat.PNG, 25).getBytes();
			Files.write(Paths.get(filePath), bytes);
			skImage.close();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
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
