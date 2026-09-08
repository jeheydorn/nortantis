package nortantis.platform;

import java.io.InputStream;
import java.util.List;

import nortantis.IconDrawTask;
import nortantis.geom.Rectangle;

/**
 * Abstracts the library used for drawing.
 */
public abstract class PlatformFactory
{
	private static PlatformFactory instance;

	public static PlatformFactory getInstance()
	{
		return instance;
	}

	/**
	 * Sets the default platform instance to use when creating new objects of the types in nortantis.platform. Objects can be created using
	 * a different platform by calling that platform's methods directly, but if you do that, you must be careful not to mix objects from
	 * different platforms. Mixing is not allowed. For example, a SkiaPainter cannot draw text using an AwtFont.
	 */
	public static void setInstance(PlatformFactory instance)
	{
		PlatformFactory.instance = instance;
		ImageHelper.setInstance(instance.createImageHelper());
	}

	/**
	 * Creates the ImageHelper for this platform. Called when this factory is set as the active platform via setInstance().
	 */
	protected abstract ImageHelper createImageHelper();

	/**
	 * Creates an image. Note - callers outside the nortantis.platform packages should call Image.create(...)
	 */
	public abstract Image createImage(int width, int height, ImageType type);

	/**
	 * Creates an image with an option to force CPU-only mode. Note - callers outside the nortantis.platform packages should call
	 * Image.create(...)
	 *
	 * @param forceCPU
	 *            If true, the image will not use GPU acceleration regardless of size. Not used in AWT implementations.
	 */
	@SuppressWarnings("unused")
	public abstract Image createImage(int width, int height, ImageType type, boolean forceCPU);

	/**
	 * Reads an image from a file. Note - callers outside the nortantis.platform packages should call Image.read(...)
	 */
	public abstract Image readImage(String filePath);

	/**
	 * Reads an image from a file. Note - callers outside the nortantis.platform packages should call Image.read(...)
	 */
	public abstract Image readImage(InputStream stream);

	/**
	 * Writes an image to a file. Note - callers outside the nortantis.platform packages should call Image.write(...)
	 */
	public abstract void writeImage(Image image, String filePath);

	/**
	 * Checks whether a font exists on the system. Note - callers outside the nortantis.platform packages should call Font.isInstalled(...)
	 */
	public abstract boolean isFontInstalled(String fontFamily);

	/**
	 * Creates a font. Note - callers outside the nortantis.platform packages should call Font.create(...)
	 */
	public abstract Font createFont(String name, FontStyle style, float size);

	/**
	 * Makes the font in the given asset file available to this platform, so that createFont can use it by family name. Returns the family
	 * name the platform assigned to it, or null if the file could not be loaded.
	 */
	public abstract String registerFont(String assetFilePath);

	/**
	 * The names of the font families this platform can draw with, including any made available by registerFont.
	 */
	public abstract List<String> listFontFamilies();

	/**
	 * Creates a color. Note - callers outside the nortantis.platform packages should call Color.create(...)
	 */
	public abstract Color createColor(int rgb, boolean hasAlpha);

	/**
	 * Creates a color. Alpha is assumed to be 255. Note - callers outside the nortantis.platform packages should call Color.create(...)
	 */
	public abstract Color createColor(int red, int green, int blue);

	/**
	 * Creates a color. Alpha is assumed to be 255. Note - callers outside the nortantis.platform packages should call Color.create(...)
	 */
	public abstract Color createColor(float red, float green, float blue);

	/**
	 * Creates a color. Note - callers outside the nortantis.platform packages should call Color.create(...)
	 */
	public abstract Color createColor(int red, int green, int blue, int alpha);

	/**
	 * Creates a Color from hue, saturation, and brightness. Note - callers outside the nortantis.platform packages should call
	 * Color.createFromHSB(...)
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

	/**
	 * GPU-accelerated drawing of non-decoration icons using shader-based blending. Called from {@link nortantis.IconDrawer#drawIcons} before
	 * the CPU pixel-loop path. Override in GPU backends to avoid expensive CPU↔GPU round-trips.
	 *
	 * @return The subset of {@code tasks} that were NOT handled (typically decoration icons, which need per-pixel water detection). Returns
	 *         {@code null} if the GPU path is not supported (causes the caller to use the CPU path for all tasks).
	 */
	public List<IconDrawTask> drawNonDecorationIconsGpu(List<IconDrawTask> tasks, Image mapImage, Image landBackground, Image landTexture,
			Rectangle drawBounds)
	{
		return null; // not supported; caller uses CPU path for all icons
	}
}
