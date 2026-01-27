package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReaderWriter;

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
		// Set R=G=B=level for grayscale, with full alpha
		setRGB(x, y, level, level, level, 255);
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		int idx = getByteIndex(x, y);
		modified = true;

		// BGRA order: [0]=B, [1]=G, [2]=R, [3]=A
		if (band == 0)
			cachedPixelBytes[idx + 2] = (byte) level; // R
		else if (band == 1)
			cachedPixelBytes[idx + 1] = (byte) level; // G
		else if (band == 2)
			cachedPixelBytes[idx] = (byte) level; // B
		else if (band == 3)
			cachedPixelBytes[idx + 3] = (byte) level; // A
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
		int idx = getByteIndex(x, y);

		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = (rgb >> 24) & 0xFF;

		// BGRA order
		cachedPixelBytes[idx] = (byte) b;
		cachedPixelBytes[idx + 1] = (byte) g;
		cachedPixelBytes[idx + 2] = (byte) r;
		cachedPixelBytes[idx + 3] = (byte) a;
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		setRGB(x, y, red, green, blue, 255);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		modified = true;
		int idx = getByteIndex(x, y);

		// BGRA order
		cachedPixelBytes[idx] = (byte) blue;
		cachedPixelBytes[idx + 1] = (byte) green;
		cachedPixelBytes[idx + 2] = (byte) red;
		cachedPixelBytes[idx + 3] = (byte) alpha;
	}

	@Override
	public void close()
	{
		if (modified && cachedPixelBytes != null)
		{
			if (bounds == null)
			{
				image.writePixelsFromByteArray(cachedPixelBytes);
				image.markCPUDirty(); // Invalidate GPU copy since CPU was modified
			}
			else
			{
				image.writePixelsToRegionFromByteArray(cachedPixelBytes, bounds);
				// TODO remove commented out code here when I'm sure I won't use it. It does not help icon drawing now, and if I do keep
				// this, it should be optional.
				// If GPU is enabled, update only the modified region on the GPU
				// if (image.isGpuEnabled())
				// {
				// image.updateGPURegion(bounds.x, bounds.y, bounds.width, bounds.height);
				// }
				// else
				// {
				image.markCPUDirty();
				// }
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
