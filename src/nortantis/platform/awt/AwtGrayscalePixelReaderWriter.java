package nortantis.platform.awt;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.PixelReaderWriter;

import java.awt.image.BufferedImage;

public class AwtGrayscalePixelReaderWriter extends AwtGrayscalePixelReader implements PixelReaderWriter
{
	AwtGrayscalePixelReaderWriter(AwtImage image)
	{
		this(image, null);
	}

	AwtGrayscalePixelReaderWriter(AwtImage image, IntRectangle bounds)
	{
		super(image, bounds);
	}

	@Override
	public void setGrayLevel(int x, int y, int level)
	{
		if (cachedByteArray != null)
		{
			cachedByteArray[(y * image.getWidth()) + x] = (byte) level;
			return;
		}
		if (imageSubType == BufferedImage.TYPE_BYTE_BINARY)
		{
			raster.setSample(x, y, 0, Math.min(level, 1));
			return;
		}
		raster.setSample(x, y, 0, level);
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		if (band == 3)
		{
			return;
		}
		setGrayLevel(x, y, level);
	}

	@Override
	public void setPixelColor(int x, int y, Color color)
	{
		int gray = color.getRed();
		if (imageSubType == BufferedImage.TYPE_USHORT_GRAY)
		{
			gray = gray * 257;
		}
		setGrayLevel(x, y, gray);
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		int gray = (rgb >> 16) & 0xFF;
		if (imageSubType == BufferedImage.TYPE_USHORT_GRAY)
		{
			gray = gray * 257;
		}
		setGrayLevel(x, y, gray);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		int gray = red;
		if (imageSubType == BufferedImage.TYPE_USHORT_GRAY)
		{
			gray = gray * 257;
		}
		setGrayLevel(x, y, gray);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		int gray = red;
		if (imageSubType == BufferedImage.TYPE_USHORT_GRAY)
		{
			gray = gray * 257;
		}
		setGrayLevel(x, y, gray);
	}
}
