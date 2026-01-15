package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReaderWriter;
import org.jetbrains.skia.IRect;

public class SkiaPixelReaderWriter extends SkiaPixelReader implements PixelReaderWriter
{
	public SkiaPixelReaderWriter(SkiaImage image)
	{
		this(image, null);
	}

	public SkiaPixelReaderWriter(SkiaImage image, IntRectangle bounds)
	{
		super(image, bounds);
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
		Color gray = Color.create(level, level, level, 255);
		setRGB(x, y, gray.getRGB());
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
			image.bitmap.erase(rgb, IRect.makeXYWH(x, y, 1, 1));
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
		if (cachedPixelArray != null)
		{
			if (bounds == null)
			{
				image.writePixelsFromIntArray(cachedPixelArray);
			}
			else
			{
				image.writePixelsToRegion(cachedPixelArray, bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
		image.invalidateCachedImage();
		super.close();
	}

}
