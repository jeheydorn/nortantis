package nortantis.platform.awt;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.PixelReaderWriter;

public class AwtIntPixelReaderWriter extends AwtIntPixelReader implements PixelReaderWriter
{
	AwtIntPixelReaderWriter(AwtImage image)
	{
		this(image, null);
	}

	AwtIntPixelReaderWriter(AwtImage image, IntRectangle bounds)
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
		cachedPixelArray[(y * image.getWidth()) + x] = rgb;
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
