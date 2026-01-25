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

	public SkiaPixelReader(SkiaImage image, IntRectangle bounds)
	{
		this(image, bounds, true);
	}

	/**
	 * Creates a pixel reader for the given image.
	 *
	 * @param image
	 *            The image to read from
	 * @param bounds
	 *            If not null, restricts reading to these bounds. If null, reads the whole image.
	 * @param doInitialRead
	 *            If true, reads existing pixels from the image into the array. If false, allocates an empty array without reading (useful
	 *            for write-only operations).
	 */
	public SkiaPixelReader(SkiaImage image, IntRectangle bounds, boolean doInitialRead)
	{
		maxPixelLevelAsFloat = image.getMaxPixelLevel();
		if (doInitialRead)
		{
			if (bounds == null)
			{
				cachedPixelArray = image.readPixelsToIntArray();
			}
			else
			{
				cachedPixelArray = image.readPixelsToIntArray(bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
		else
		{
			// Allocate empty array without reading
			int arrayWidth = bounds != null ? bounds.width : image.getWidth();
			int arrayHeight = bounds != null ? bounds.height : image.getHeight();
			cachedPixelArray = new int[arrayWidth * arrayHeight];
		}
		this.image = image;
		width = image.getWidth();
		this.bounds = bounds;
	}

	public SkiaPixelReader(SkiaImage image)
	{
		this(image, null, true);
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
		if (cachedPixelArray != null)
		{
			if (bounds != null)
			{
				return cachedPixelArray[(y - bounds.y) * bounds.width + (x - bounds.x)];
			}
			return cachedPixelArray[y * width + x];
		}
		return image.getBitmap().getColor(x, y);
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
