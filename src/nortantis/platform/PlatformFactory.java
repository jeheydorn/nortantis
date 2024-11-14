package nortantis.platform;

import java.io.InputStream;

public abstract class PlatformFactory
{
	private static PlatformFactory instance;

	public static PlatformFactory getInstance()
	{
		return instance;
	}

	public static void setInstance(PlatformFactory instance)
	{
		PlatformFactory.instance = instance;
	}

	public abstract Image createImage(int width, int height, ImageType type);

	public abstract Image readImage(String filePath);

	public abstract Image readImage(InputStream stream);

	public abstract void writeImage(Image image, String filePath);

	public abstract boolean isFontInstalled(String fontFamily);

	public abstract Font createFont(String name, FontStyle style, float size);

	public abstract Color createColor(int rgb, boolean hasAlpha);

	public abstract Color createColor(int red, int green, int blue);

	public abstract Color createColor(float red, float green, float blue);

	public abstract Color createColor(int red, int green, int blue, int alpha);

	/**
	 * Creates a Color from hue, saturation, and brightness.
	 * 
	 * @param hue
	 *            In the range [0...1].
	 * @param saturation
	 *            In the range [0...1]
	 * @param brightness
	 *            In the range [0...1]
	 * @return a Color
	 */
	public abstract Color createColorFromHSB(float hue, float saturation, float brightness);

	public abstract <T> void doInBackgroundThread(BackgroundTask<T> task);

	public abstract void doInMainUIThreadAsynchronous(Runnable toRun);
}
