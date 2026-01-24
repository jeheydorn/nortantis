package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReaderWriter;

/**
 * Optimized PixelReaderWriter for grayscale images that works directly with byte[] arrays, avoiding the overhead of int[] arrays,
 * byte-to-int conversions, and Color object allocations.
 */
public class SkiaGrayscalePixelReaderWriter extends SkiaGrayscalePixelReader implements PixelReaderWriter
{
	private boolean modified = false;

	public SkiaGrayscalePixelReaderWriter(SkiaImage image)
	{
		this(image, null);
	}

	public SkiaGrayscalePixelReaderWriter(SkiaImage image, IntRectangle bounds)
	{
		super(image, bounds);
	}

	@Override
	public void setGrayLevel(int x, int y, int level)
	{
		if (image.getType() == ImageType.Binary)
		{
			// Binary images use GRAY_8 storage but accept 0 or 1 as input
			level = level > 0 ? 255 : 0;
		}
		modified = true;
		int index = bounds != null ? (y - bounds.y) * width + (x - bounds.x) : y * image.getWidth() + x;
		cachedPixelArray[index] = (byte) level;
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		// For grayscale, setting any color band sets the gray level
		if (band < 3)
		{
			setGrayLevel(x, y, level);
		}
		// Ignore alpha band (band == 3) for grayscale images
	}

	@Override
	public void setPixelColor(int x, int y, Color color)
	{
		// Use red channel as gray value
		setGrayLevel(x, y, color.getRed());
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		// Extract red channel as gray value
		int gray = (rgb >> 16) & 0xFF;
		setGrayLevel(x, y, gray);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		// Use red as gray value (assumes gray pixels have R=G=B)
		setGrayLevel(x, y, red);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		// Use red as gray value, ignore alpha for grayscale
		setGrayLevel(x, y, red);
	}

	@Override
	public void close()
	{
		if (modified && cachedPixelArray != null)
		{
			if (bounds == null)
			{
				image.writeGrayscalePixels(cachedPixelArray);
				image.markCPUDirty();
			}
			else
			{
				image.writeGrayscalePixelsToRegion(cachedPixelArray, bounds);
				if (image.isGpuEnabled())
				{
					image.updateGPURegion(bounds.x, bounds.y, bounds.width, bounds.height);
				}
				else
				{
					image.markCPUDirty();
				}
			}
		}
	}
}
