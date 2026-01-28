package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReader;

/**
 * Optimized PixelReader for grayscale images that works directly with byte[] arrays, avoiding the overhead of int[] arrays and byte-to-int
 * conversions.
 */
public class SkiaGrayscalePixelReader implements PixelReader
{
	protected final IntRectangle bounds;
	protected final byte[] cachedPixelArray;
	protected final SkiaImage image;
	protected final int width;

	public SkiaGrayscalePixelReader(SkiaImage image, IntRectangle bounds)
	{
		this(image, bounds, true);
	}

	/**
	 * Creates a grayscale pixel reader for the given image.
	 *
	 * @param image
	 *            The grayscale image to read from
	 * @param bounds
	 *            If not null, restricts reading to these bounds. If null, reads the whole image.
	 * @param doInitialRead
	 *            If true, reads existing pixels from the image into the array. If false, allocates an empty array without reading (useful
	 *            for write-only operations).
	 */
	public SkiaGrayscalePixelReader(SkiaImage image, IntRectangle bounds, boolean doInitialRead)
	{
		this.image = image;
		this.bounds = bounds;
		this.width = bounds != null ? bounds.width : image.getWidth();
		if (doInitialRead)
		{
			// Read bytes directly from Skia - no conversion needed
			this.cachedPixelArray = image.readGrayscalePixels(bounds);
		}
		else
		{
			// Allocate empty array without reading
			int arrayHeight = bounds != null ? bounds.height : image.getHeight();
			this.cachedPixelArray = new byte[width * arrayHeight];
		}
	}

	/**
	 * Gets the raw grayscale value (0-255) without Binary conversion. Used for getRGB and getBandLevel to preserve actual pixel values.
	 */
	protected int getRawGrayValue(int x, int y)
	{
		int index = bounds != null ? (y - bounds.y) * width + (x - bounds.x) : y * image.getWidth() + x;
		return cachedPixelArray[index] & 0xFF;
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		int level = getRawGrayValue(x, y);
		if (image.getType() == ImageType.Binary)
		{
			// Binary images use GRAY_8 storage (0 or 255) but should return 0 or 1
			return level > 127 ? 1 : 0;
		}
		return level;
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		// For grayscale, all color bands have the same value
		if (band == 3)
		{
			return 255; // Alpha is always fully opaque
		}
		// Use raw value to preserve actual pixel data (not logical 0/1 for Binary)
		return getRawGrayValue(x, y);
	}

	@Override
	public int getRGB(int x, int y)
	{
		// Use raw gray value to preserve actual pixel data
		int gray = getRawGrayValue(x, y);
		return (255 << 24) | (gray << 16) | (gray << 8) | gray;
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new SkiaColor(getRGB(x, y), false);
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / (float) image.getMaxPixelLevel();
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return 255; // Grayscale images are always fully opaque
	}

	@Override
	public void refreshRegion(IntRectangle refreshBounds)
	{
		if (refreshBounds == null)
		{
			return;
		}

		// Compute the intersection of refreshBounds with our cached region
		IntRectangle cachedRegion = bounds != null ? bounds : new IntRectangle(0, 0, image.getWidth(), image.getHeight());
		IntRectangle intersection = cachedRegion.findIntersection(refreshBounds);
		if (intersection == null || intersection.width <= 0 || intersection.height <= 0)
		{
			return;
		}

		// Read the intersection region from the image
		byte[] regionBytes = image.readGrayscalePixels(intersection);

		// Copy the bytes into the correct positions in cachedPixelArray
		for (int row = 0; row < intersection.height; row++)
		{
			int srcOffset = row * intersection.width;
			int destY = intersection.y - (bounds != null ? bounds.y : 0) + row;
			int destX = intersection.x - (bounds != null ? bounds.x : 0);
			int destOffset = destY * width + destX;
			System.arraycopy(regionBytes, srcOffset, cachedPixelArray, destOffset, intersection.width);
		}
	}

	@Override
	public void close()
	{
	}
}
