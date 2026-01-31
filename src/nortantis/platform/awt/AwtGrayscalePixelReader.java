package nortantis.platform.awt;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class AwtGrayscalePixelReader extends AwtPixelReader
{
	protected final byte[] cachedByteArray;
	protected final int imageSubType;

	AwtGrayscalePixelReader(AwtImage image, IntRectangle bounds)
	{
		super(image, bounds);
		imageSubType = bufferedImage.getType();
		if (imageSubType == BufferedImage.TYPE_BYTE_GRAY)
		{
			this.cachedByteArray = ((DataBufferByte) raster.getDataBuffer()).getData();
		}
		else
		{
			this.cachedByteArray = null;
		}
	}

	AwtGrayscalePixelReader(AwtImage image)
	{
		this(image, null);
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		if (cachedByteArray != null)
		{
			return cachedByteArray[(y * image.getWidth()) + x] & 0xFF;
		}
		return raster.getSample(x, y, 0);
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		if (band == 3)
		{
			return 255;
		}
		int gray = getGrayLevel(x, y);
		if (imageSubType == BufferedImage.TYPE_BYTE_BINARY)
		{
			return gray * 255;
		}
		return gray;
	}

	@Override
	public int getRGB(int x, int y)
	{
		int gray;
		if (cachedByteArray != null)
		{
			gray = cachedByteArray[(y * image.getWidth()) + x] & 0xFF;
		}
		else if (imageSubType == BufferedImage.TYPE_BYTE_BINARY)
		{
			gray = raster.getSample(x, y, 0) * 255;
		}
		else if (imageSubType == BufferedImage.TYPE_USHORT_GRAY)
		{
			gray = raster.getSample(x, y, 0) >> 8;
		}
		else
		{
			gray = raster.getSample(x, y, 0);
		}
		return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new AwtColor(getRGB(x, y), false);
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return 255;
	}
}
