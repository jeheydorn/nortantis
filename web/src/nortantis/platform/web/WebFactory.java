package nortantis.platform.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import nortantis.BackgroundImageParameters;
import nortantis.GrungeParameters;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;
import nortantis.util.Assets;
import nortantis.util.Logger;

/**
 * A platform that keeps images in Java arrays and draws them in Java, leaving only text to a {@link TextRasterizer}. It needs nothing from
 * AWT, so it can run in a web browser. Everything runs on the calling thread: background tasks run before this returns, and work meant for
 * the UI thread runs immediately. The exceptions are grunge and the images a background generates, which a {@link SeparateMemoryJobs}, if
 * given, creates elsewhere.
 */
public class WebFactory extends PlatformFactory
{
	private final TextRasterizer textRasterizer;
	private final long maxMemory;
	private final SeparateMemoryJobs separateMemoryJobs;
	private final Set<String> registeredFamilies = new LinkedHashSet<>();

	/**
	 * @param maxMemory
	 *            The most memory, in bytes, the program can use.
	 * @param separateMemoryJobs
	 *            Where to create grunge and the images a background generates, or null to create them on the calling thread.
	 */
	public WebFactory(TextRasterizer textRasterizer, long maxMemory, SeparateMemoryJobs separateMemoryJobs)
	{
		this.textRasterizer = textRasterizer;
		this.maxMemory = maxMemory;
		this.separateMemoryJobs = separateMemoryJobs;
		WebPainter.setTextRasterizer(textRasterizer);
	}

	@Override
	public Future<Image> startGrungeCreation(GrungeParameters parameters, Runnable checkForCancel)
	{
		if (separateMemoryJobs == null)
		{
			return super.startGrungeCreation(parameters, checkForCancel);
		}
		return separateMemoryJobs.startGrungeCreation(parameters);
	}

	@Override
	public Future<Image> startBackgroundImageCreation(BackgroundImageParameters parameters)
	{
		if (separateMemoryJobs == null)
		{
			return super.startBackgroundImageCreation(parameters);
		}
		return separateMemoryJobs.startBackgroundImageCreation(parameters);
	}

	@Override
	public boolean createsImagesInSeparateMemory()
	{
		return separateMemoryJobs != null;
	}

	@Override
	public long getMaxMemory()
	{
		return maxMemory;
	}

	/**
	 * True, so that this platform behaves like the packaged desktop app rather than a development build, and debug settings stay off.
	 */
	@Override
	public boolean isRunningFromJar()
	{
		return true;
	}

	@Override
	protected ImageHelper createImageHelper()
	{
		return new WebImageHelper();
	}

	@Override
	public Image createImage(int width, int height, ImageType type)
	{
		return new WebImage(width, height, type);
	}

	@Override
	public Image createImage(int width, int height, ImageType type, boolean forceCPU)
	{
		return new WebImage(width, height, type);
	}

	@Override
	public Image readImage(String filePath)
	{
		try (InputStream stream = Assets.openStream(filePath))
		{
			return readImage(stream);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Can't read the file " + filePath, e);
		}
	}

	@Override
	public Image readImage(InputStream stream)
	{
		if (stream == null)
		{
			throw new RuntimeException("Unable to read an image file stream because it is null.");
		}
		try
		{
			return PngDecoder.decode(stream);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public void writeImage(Image image, String filePath)
	{
		throw new UnsupportedOperationException("This platform can't write image files.");
	}

	@Override
	public boolean isFontInstalled(String fontFamily)
	{
		return registeredFamilies.contains(fontFamily);
	}

	@Override
	public Font createFont(String name, FontStyle style, float size)
	{
		// AWT's Font constructor takes a whole-number size.
		return new WebFont(name, style, (int) size);
	}

	@Override
	public String registerFont(String assetFilePath)
	{
		try (InputStream stream = Assets.openStream(assetFilePath))
		{
			byte[] data = stream.readAllBytes();
			FontFileInfo info = FontFileInfo.read(data);
			if (info == null)
			{
				Logger.printError("Unable to read the font file '" + assetFilePath + "'.", null);
				return null;
			}
			textRasterizer.registerFont(info.family, info.isBold, info.isItalic, info.isVariable, data);
			registeredFamilies.add(info.family);
			return info.family;
		}
		catch (IOException | RuntimeException e)
		{
			Logger.printError("Unable to load the font file '" + assetFilePath + "'.", e);
			return null;
		}
	}

	@Override
	public List<String> listFontFamilies()
	{
		return new ArrayList<>(registeredFamilies);
	}

	@Override
	public Color createColor(int rgb, boolean hasAlpha)
	{
		return new WebColor(hasAlpha ? rgb : rgb | 0xFF000000);
	}

	@Override
	public Color createColor(int red, int green, int blue)
	{
		return new WebColor(red, green, blue, 255);
	}

	@Override
	public Color createColor(float red, float green, float blue)
	{
		return new WebColor((int) (red * 255 + 0.5), (int) (green * 255 + 0.5), (int) (blue * 255 + 0.5), 255);
	}

	@Override
	public Color createColor(int red, int green, int blue, int alpha)
	{
		return new WebColor(red, green, blue, alpha);
	}

	@Override
	public Color createColorFromHSB(float hue, float saturation, float brightness)
	{
		return new WebColor(WebColor.hsbToRgb(hue, saturation, brightness));
	}

	@Override
	public <T> void doInBackgroundThread(BackgroundTask<T> task)
	{
		T result;
		try
		{
			result = task.doInBackground();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		task.done(result);
	}

	@Override
	public void doInMainUIThreadAsynchronous(Runnable toRun)
	{
		toRun.run();
	}
}
