package nortantis.platform.web;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReaderWriter;

/**
 * Pixel access for Grayscale8Bit and Binary images. A binary pixel's gray level is 0 or 1, and writing any nonzero level to one stores 1.
 */
class WebBytePixels implements PixelReaderWriter
{
	private final byte[] pixels;
	private final int offset;
	private final int stride;
	private final boolean isBinary;

	WebBytePixels(WebImage image)
	{
		pixels = image.bytes;
		offset = image.offset;
		stride = image.stride;
		isBinary = image.getType() == ImageType.Binary;
	}

	@Override
	public void refreshRegion(IntRectangle bounds)
	{
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		return pixels[offset + y * stride + x] & 0xFF;
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		if (band == 3)
		{
			return 255;
		}
		int gray = pixels[offset + y * stride + x] & 0xFF;
		return isBinary ? gray * 255 : gray;
	}

	@Override
	public int getRGB(int x, int y)
	{
		int gray = pixels[offset + y * stride + x] & 0xFF;
		if (isBinary)
		{
			gray *= 255;
		}
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
		return (pixels[offset + y * stride + x] & 0xFF) / (isBinary ? 1f : 255f);
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return 255;
	}

	@Override
	public void setGrayLevel(int x, int y, int level)
	{
		pixels[offset + y * stride + x] = (byte) (isBinary ? Math.min(level, 1) : level);
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
		setGrayLevel(x, y, color.getRed());
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		setGrayLevel(x, y, (rgb >> 16) & 0xFF);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		setGrayLevel(x, y, red);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		setGrayLevel(x, y, red);
	}

	@Override
	public void close()
	{
	}
}
