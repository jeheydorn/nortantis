package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.PixelReaderWriter;

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
		setBandLevel(x, y, 0, level);
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
		cachedPixelArray[y * width + x] = rgb;
		return;
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
	public void setAlpha(int x, int y, int alpha)
	{
		int rgb = getRGB(x, y);
		setRGB(x, y, (alpha << 24) | (rgb & 0xFFFFFF));
	}

	@Override
	public void close()
	{
		super.close();
		image.invalidateCachedImage();
	}

}
