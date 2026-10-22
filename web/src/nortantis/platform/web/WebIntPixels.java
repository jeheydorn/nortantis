package nortantis.platform.web;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReaderWriter;

/**
 * Pixel access for ARGB and RGB images. Bands are numbered as in an AWT raster: 0 red, 1 green, 2 blue, 3 alpha.
 */
class WebIntPixels implements PixelReaderWriter
{
	private final int[] pixels;
	private final int offset;
	private final int stride;
	private final boolean hasAlpha;

	WebIntPixels(WebImage image)
	{
		pixels = image.ints;
		offset = image.offset;
		stride = image.stride;
		hasAlpha = image.getType() == ImageType.ARGB;
	}

	@Override
	public void refreshRegion(IntRectangle bounds)
	{
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		return (pixels[offset + y * stride + x] >> 16) & 0xFF;
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		int argb = pixels[offset + y * stride + x];
		switch (band)
		{
			case 0:
				return (argb >> 16) & 0xFF;
			case 1:
				return (argb >> 8) & 0xFF;
			case 2:
				return argb & 0xFF;
			default:
				return hasAlpha ? argb >>> 24 : 255;
		}
	}

	@Override
	public int getRGB(int x, int y)
	{
		return pixels[offset + y * stride + x];
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new WebColor(pixels[offset + y * stride + x]);
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / 255f;
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return hasAlpha ? pixels[offset + y * stride + x] >>> 24 : 255;
	}

	@Override
	public void setGrayLevel(int x, int y, int level)
	{
		setBandLevel(x, y, 0, level);
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		int i = offset + y * stride + x;
		int shift;
		switch (band)
		{
			case 0:
				shift = 16;
				break;
			case 1:
				shift = 8;
				break;
			case 2:
				shift = 0;
				break;
			default:
				if (!hasAlpha)
				{
					return;
				}
				shift = 24;
				break;
		}
		pixels[i] = (pixels[i] & ~(0xFF << shift)) | ((level & 0xFF) << shift);
	}

	@Override
	public void setPixelColor(int x, int y, Color color)
	{
		setRGB(x, y, color.getRGB());
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		pixels[offset + y * stride + x] = hasAlpha ? rgb : rgb | 0xFF000000;
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		pixels[offset + y * stride + x] = 0xFF000000 | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		int a = hasAlpha ? alpha & 0xFF : 255;
		pixels[offset + y * stride + x] = (a << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
	}

	@Override
	public void close()
	{
	}
}
