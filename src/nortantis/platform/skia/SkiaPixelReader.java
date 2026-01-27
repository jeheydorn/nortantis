package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReader;

/**
 * Reads pixels from a SkiaImage. Skia uses BGRA byte order (blue at offset 0, green at 1, red at 2, alpha at 3).
 */
public class SkiaPixelReader implements PixelReader
{
	protected final IntRectangle bounds;
	protected final byte[] cachedPixelBytes;
	protected final float maxPixelLevelAsFloat;
	protected final SkiaImage image;
	protected final int regionWidth;

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
		this.image = image;
		this.bounds = bounds;
		this.regionWidth = bounds != null ? bounds.width : image.getWidth();

		if (doInitialRead)
		{
			cachedPixelBytes = image.readPixelsToByteArray(bounds);
		}
		else
		{
			// Allocate empty array without reading (4 bytes per pixel)
			int arrayWidth = bounds != null ? bounds.width : image.getWidth();
			int arrayHeight = bounds != null ? bounds.height : image.getHeight();
			cachedPixelBytes = new byte[arrayWidth * arrayHeight * 4];
		}
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
		int idx = getByteIndex(x, y);
		// BGRA order: [0]=B, [1]=G, [2]=R, [3]=A
		if (band == 0)
			return cachedPixelBytes[idx + 2] & 0xFF; // R
		if (band == 1)
			return cachedPixelBytes[idx + 1] & 0xFF; // G
		if (band == 2)
			return cachedPixelBytes[idx] & 0xFF; // B
		return cachedPixelBytes[idx + 3] & 0xFF; // A
	}

	/**
	 * Gets the byte array index for pixel at (x, y).
	 */
	protected int getByteIndex(int x, int y)
	{
		if (bounds != null)
		{
			return ((y - bounds.y) * regionWidth + (x - bounds.x)) * 4;
		}
		return (y * regionWidth + x) * 4;
	}

	@Override
	public int getRGB(int x, int y)
	{
		int idx = getByteIndex(x, y);
		// BGRA order: [0]=B, [1]=G, [2]=R, [3]=A
		int b = cachedPixelBytes[idx] & 0xFF;
		int g = cachedPixelBytes[idx + 1] & 0xFF;
		int r = cachedPixelBytes[idx + 2] & 0xFF;
		int a = cachedPixelBytes[idx + 3] & 0xFF;
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
		int idx = getByteIndex(x, y);
		return cachedPixelBytes[idx + 3] & 0xFF;
	}


	@Override
	public IntRectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void close()
	{
	}
}
