package nortantis.platform.web;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.PixelReaderWriter;

/**
 * Pixel access for Grayscale16Bit images. Colors written to one are scaled from 8 bits up to 16.
 */
class WebShortPixels implements PixelReaderWriter
{
	private final short[] pixels;
	private final int offset;
	private final int stride;

	WebShortPixels(WebImage image)
	{
		pixels = image.shorts;
		offset = image.offset;
		stride = image.stride;
	}

	@Override
	public void refreshRegion(IntRectangle bounds)
	{
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		return pixels[offset + y * stride + x] & 0xFFFF;
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		return band == 3 ? 255 : getGrayLevel(x, y);
	}

	@Override
	public int getRGB(int x, int y)
	{
		int gray = getGrayLevel(x, y) >> 8;
		return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new WebColor(getRGB(x, y));
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / 65535f;
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return 255;
	}

	@Override
	public void setGrayLevel(int x, int y, int level)
	{
		pixels[offset + y * stride + x] = (short) level;
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		if (band != 3)
		{
			setGrayLevel(x, y, level);
		}
	}

	@Override
	public void setPixelColor(int x, int y, Color color)
	{
		setGrayLevel(x, y, color.getRed() * 257);
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		setGrayLevel(x, y, ((rgb >> 16) & 0xFF) * 257);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		setGrayLevel(x, y, red * 257);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		setGrayLevel(x, y, red * 257);
	}

	@Override
	public void close()
	{
	}
}
