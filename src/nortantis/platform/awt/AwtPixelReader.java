package nortantis.platform.awt;

import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.PixelReader;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public class AwtPixelReader implements PixelReader
{
	protected int[] cachedPixelArray;
	protected final BufferedImage bufferedImage;
	WritableRaster raster;
	Raster alphaRaster;
	protected final float maxPixelLevelAsFloat;
	protected final Image image;


	AwtPixelReader(AwtImage image)
	{
		this.image = image;
		maxPixelLevelAsFloat = image.getMaxPixelLevel();
		if (image.isCompatibleIntFormat())
		{
			this.cachedPixelArray =  ((DataBufferInt) raster.getDataBuffer()).getData();
		}
		bufferedImage = AwtFactory.unwrap(image);
	}

	private void createRastersIfNeeded()
	{
		raster = bufferedImage.getRaster();

		if (image.hasAlpha())
		{
			alphaRaster = bufferedImage.getAlphaRaster();
		}
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		return getBandLevel(x, y, 0);
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		return raster.getSample(x, y, band);
	}

	@Override
	public int getRGB(int x, int y)
	{
		if (cachedPixelArray != null)
		{
			return cachedPixelArray[(y * image.getWidth()) + x];
		}
		return bufferedImage.getRGB(x, y);
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new AwtColor(getRGB(x, y), image.hasAlpha());
	}

	@Override
	public int getAlpha(int x, int y)
	{
		if (image.hasAlpha())
		{
			return alphaRaster.getSample(x, y, 0);
		}

		return 0;
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / maxPixelLevelAsFloat;
	}

	@Override
	public void close() throws Exception
	{
		image.endPixelReadsOrWrites();
	}
}
