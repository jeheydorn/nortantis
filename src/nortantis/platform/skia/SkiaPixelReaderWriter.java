package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReaderWriter;
import org.jetbrains.skia.IRect;

public class SkiaPixelReaderWriter extends SkiaPixelReader implements PixelReaderWriter
{
	private boolean modified = false;

	public SkiaPixelReaderWriter(SkiaImage image, IntRectangle bounds)
	{
		this(image, bounds, true);
	}

	/**
	 * Creates a pixel reader/writer for the given image.
	 *
	 * @param image
	 *            The image to read from and write to
	 * @param bounds
	 *            If not null, restricts access to these bounds. If null, accesses the whole image.
	 * @param doInitialRead
	 *            If true, reads existing pixels from the image into the array. If false, allocates an empty array without reading (useful
	 *            for write-only operations).
	 */
	public SkiaPixelReaderWriter(SkiaImage image, IntRectangle bounds, boolean doInitialRead)
	{
		super(image, bounds, doInitialRead);
	}

	@Override
	public void setGrayLevel(int x, int y, int level)
	{
		if (image.getType() == ImageType.Binary)
		{
			// Binary images use GRAY_8 storage but accept 0 or 1 as input
			// Any non-zero value becomes white (255)
			level = level > 0 ? 255 : 0;
		}
		// Compute gray RGB directly without Color object allocation
		int rgb = (255 << 24) | (level << 16) | (level << 8) | level;
		setRGB(x, y, rgb);
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		int rgb = getRGB(x, y);
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = (rgb >> 24) & 0xFF;

		if (band == 0)
			r = level;
		else if (band == 1)
			g = level;
		else if (band == 2)
			b = level;
		else if (band == 3)
			a = level;

		setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
	}

	@Override
	public void setPixelColor(int x, int y, Color color)
	{
		setRGB(x, y, color.getRGB());
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		modified = true;
		if (cachedPixelArray != null)
		{
			if (bounds != null)
			{
				cachedPixelArray[(y - bounds.y) * bounds.width + (x - bounds.x)] = rgb;
			}
			else
			{
				cachedPixelArray[y * width + x] = rgb;
			}
		}
		else
		{
			image.getBitmap().erase(rgb, IRect.makeXYWH(x, y, 1, 1));
		}
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		setRGB(x, y, (255 << 24) | (red << 16) | (green << 8) | blue);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
	}

	@Override
	public void close()
	{
		if (modified && cachedPixelArray != null)
		{
			if (bounds == null)
			{
				image.writePixelsFromIntArray(cachedPixelArray);
				image.markCPUDirty(); // Invalidate GPU copy since CPU was modified
			}
			else
			{
				image.writePixelsToRegion(cachedPixelArray, bounds.x, bounds.y, bounds.width, bounds.height);
				// If GPU is enabled, update only the modified region on the GPU
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
		else if (modified)
		{
			// Direct pixel modifications (no cached array) also need GPU invalidation
			image.markCPUDirty();
		}
		super.close();
	}

}
