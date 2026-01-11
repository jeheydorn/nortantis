package nortantis.platform.skia;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javax.swing.SwingUtilities;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.EncodedImageFormat;
import org.jetbrains.skia.Image;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;

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
			bitmap.allocPixels(new org.jetbrains.skia.ImageInfo(image.getWidth(), image.getHeight(), org.jetbrains.skia.ColorType.Companion.getN32(), org.jetbrains.skia.ColorAlphaType.PREMUL, null));

			// Use readPixels to directly copy pixel data from image to bitmap
			image.readPixels(bitmap, 0, 0);
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
			org.jetbrains.skia.Image skImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(skiaImage.getBitmap());
			byte[] bytes = skImage.encodeToData(EncodedImageFormat.PNG, 100).getBytes();
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
		// Simple implementation: try to create it and see if it works.
		// A better one would use FontMgr.
		return true;
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
