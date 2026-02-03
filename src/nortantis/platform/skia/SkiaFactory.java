package nortantis.platform.skia;

import nortantis.platform.*;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.util.Logger;
import org.jetbrains.skia.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SkiaFactory extends PlatformFactory
{
	private final ExecutorService executor = Executors.newCachedThreadPool();
	private static Consumer<Runnable> mainThreadDispatcher;

	@Override
	protected nortantis.platform.ImageHelper createImageHelper()
	{
		return new SkiaImageHelper();
	}

	/**
	 * Sets the dispatcher used to run code on the main UI thread. Must be called before any code that uses
	 * doInMainUIThreadAsynchronous. On desktop with Swing, pass a lambda that delegates to SwingUtilities. On Android, pass a lambda that
	 * posts to the main looper Handler.
	 */
	public static void setMainThreadDispatcher(Consumer<Runnable> dispatcher)
	{
		mainThreadDispatcher = dispatcher;
	}

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
		return new SkiaColor(hsbToRGB(hue, saturation, brightness), false);
	}

	/**
	 * Pure Java HSB-to-RGB conversion equivalent to java.awt.Color.HSBtoRGB.
	 */
	static int hsbToRGB(float hue, float saturation, float brightness)
	{
		int r = 0, g = 0, b = 0;
		if (saturation == 0)
		{
			r = g = b = (int) (brightness * 255.0f + 0.5f);
		}
		else
		{
			float h = (hue - (float) Math.floor(hue)) * 6.0f;
			float f = h - (float) Math.floor(h);
			float p = brightness * (1.0f - saturation);
			float q = brightness * (1.0f - saturation * f);
			float t = brightness * (1.0f - (saturation * (1.0f - f)));
			switch ((int) h)
			{
			case 0:
				r = (int) (brightness * 255.0f + 0.5f);
				g = (int) (t * 255.0f + 0.5f);
				b = (int) (p * 255.0f + 0.5f);
				break;
			case 1:
				r = (int) (q * 255.0f + 0.5f);
				g = (int) (brightness * 255.0f + 0.5f);
				b = (int) (p * 255.0f + 0.5f);
				break;
			case 2:
				r = (int) (p * 255.0f + 0.5f);
				g = (int) (brightness * 255.0f + 0.5f);
				b = (int) (t * 255.0f + 0.5f);
				break;
			case 3:
				r = (int) (p * 255.0f + 0.5f);
				g = (int) (q * 255.0f + 0.5f);
				b = (int) (brightness * 255.0f + 0.5f);
				break;
			case 4:
				r = (int) (t * 255.0f + 0.5f);
				g = (int) (p * 255.0f + 0.5f);
				b = (int) (brightness * 255.0f + 0.5f);
				break;
			case 5:
				r = (int) (brightness * 255.0f + 0.5f);
				g = (int) (p * 255.0f + 0.5f);
				b = (int) (q * 255.0f + 0.5f);
				break;
			}
		}
		return 0xFF000000 | (r << 16) | (g << 8) | b;
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
		if (mainThreadDispatcher == null)
		{
			throw new IllegalStateException(
					"Main thread dispatcher not set. Call SkiaFactory.setMainThreadDispatcher() before using background tasks.");
		}
		mainThreadDispatcher.accept(toRun);
	}
}
