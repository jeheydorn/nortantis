package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReader;

public class SkiaPixelReader implements PixelReader
{
	protected final IntRectangle bounds;
	protected final int[] cachedPixelArray;
	protected final float maxPixelLevelAsFloat;
	protected final SkiaImage image;
	protected final int width;
	protected final boolean needsUnpremultiply;

	public SkiaPixelReader(SkiaImage image, IntRectangle bounds)
	{
		maxPixelLevelAsFloat = image.getMaxPixelLevel();
		if (bounds == null)
		{
			cachedPixelArray = image.readPixelsToIntArray();
		}
		else
		{
			cachedPixelArray = image.readPixelsToIntArray(bounds.x, bounds.y, bounds.width, bounds.height);
		}
		this.image = image;
		width = image.getWidth();
		this.bounds = bounds;
		// ARGB images use premultiplied alpha internally, so we need to unpremultiply when reading
		this.needsUnpremultiply = (image.getType() == ImageType.ARGB);
	}

	public SkiaPixelReader(SkiaImage image)
	{
		this(image, null);
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		int level = getBandLevel(x, y, 0);
		if (image.getType() == ImageType.Binary)
		{
			// Binary images use GRAY_8 storage (0 or 255) but should return 0 or 1
			// to match getMaxPixelLevel() which returns 1 for Binary
			return level > 127 ? 1 : 0;
		}
		return level;
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		int rgb = getRGB(x, y);
		if (band == 0)
			return (rgb >> 16) & 0xFF;
		if (band == 1)
			return (rgb >> 8) & 0xFF;
		if (band == 2)
			return rgb & 0xFF;
		return (rgb >> 24) & 0xFF;
	}

	@Override
	public int getRGB(int x, int y)
	{
		int argb;
		if (cachedPixelArray != null)
		{
			if (bounds != null)
			{
				argb = cachedPixelArray[(y - bounds.y) * bounds.width + (x - bounds.x)];
			}
			else
			{
				argb = cachedPixelArray[y * width + x];
			}
		}
		else
		{
			argb = image.getBitmap().getColor(x, y);
		}

		if (needsUnpremultiply)
		{
			return unpremultiply(argb);
		}
		return argb;
	}

	/**
	 * Converts a premultiplied ARGB value to unpremultiplied (straight) ARGB.
	 * In premultiplied format, RGB values are already multiplied by alpha.
	 * This reverses that operation to get the original RGB values.
	 */
	protected static int unpremultiply(int argb)
	{
		int a = (argb >> 24) & 0xFF;
		if (a == 255)
			return argb; // Fully opaque, no change needed
		if (a == 0)
			return 0; // Fully transparent

		int r = Math.min(255, ((argb >> 16) & 0xFF) * 255 / a);
		int g = Math.min(255, ((argb >> 8) & 0xFF) * 255 / a);
		int b = Math.min(255, (argb & 0xFF) * 255 / a);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new SkiaColor(getRGB(x, y), image.hasAlpha());
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / maxPixelLevelAsFloat;
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return (getRGB(x, y) >> 24) & 0xFF;
	}


	@Override
	public void close()
	{
	}
}
