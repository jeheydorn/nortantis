package nortantis.platform.awt;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.PixelReaderWriter;

public class AwtPixelReaderWriter extends AwtPixelReader implements PixelReaderWriter
{
	AwtPixelReaderWriter(AwtImage image)
	{
		this(image, null);
	}

	AwtPixelReaderWriter(AwtImage image, IntRectangle bounds)
	{
		super(image, bounds);
	}

	@Override
	public void setGrayLevel(int x, int y, int level)
	{
		setBandLevel(x, y, 0, level);
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
			cachedPixelArray[(y * image.getWidth()) + x] = rgb;
			return;
		}
		bufferedImage.setRGB(x, y, rgb);
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
	public void setBandLevel(int x, int y, int band, int level)
	{
		raster.setSample(x, y, band, level);
	}
}
