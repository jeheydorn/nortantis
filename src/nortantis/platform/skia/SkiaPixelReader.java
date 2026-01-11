package nortantis.platform.skia;

import nortantis.platform.Color;
import nortantis.platform.PixelReader;

public class SkiaPixelReader implements PixelReader
{
	protected int[] cachedPixelArray;
	protected final float maxPixelLevelAsFloat;
	protected final SkiaImage image;
	protected final int width;

	public SkiaPixelReader(SkiaImage image)
	{
		maxPixelLevelAsFloat = image.getMaxPixelLevel();
		cachedPixelArray = image.readPixelsToIntArray();
		this.image = image;
		width = image.getWidth();
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		return getBandLevel(x, y, 0);
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		int rgb = getRGB(x, y);
		if (band == 0)
			return (rgb >> 16) & 0xFF;
		if (band == 1)
			return (rgb >> 8) & 0xFF;
		if (band == 2)
			return rgb & 0xFF;
		return (rgb >> 24) & 0xFF;
	}

	@Override
	public int getRGB(int x, int y)
	{
		if (cachedPixelArray != null)
		{
			return cachedPixelArray[y * width + x];
		}
		return image.bitmap.getColor(x, y);
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new SkiaColor(getRGB(x, y), image.hasAlpha());
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / maxPixelLevelAsFloat;
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return (getRGB(x, y) >> 24) & 0xFF;
	}


	@Override
	public void close() throws Exception
	{
		image.endPixelReadsOrWrites();
	}
}
