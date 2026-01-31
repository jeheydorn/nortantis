package nortantis.platform.awt;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.PixelReader;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public class AwtPixelReader implements PixelReader
{
	protected final BufferedImage bufferedImage;
	protected WritableRaster raster;
	Raster alphaRaster;
	protected final float maxPixelLevelAsFloat;
	protected final Image image;

	AwtPixelReader(AwtImage image, IntRectangle bounds)
	{
		this.image = image;
		bufferedImage = AwtFactory.unwrap(image);
		maxPixelLevelAsFloat = image.getMaxPixelLevel();
		createRasters();
	}

	private void createRasters()
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

		// Images without alpha are fully opaque
		return 255;
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / maxPixelLevelAsFloat;
	}

	@Override
	public void refreshRegion(IntRectangle bounds)
	{
		// No-op for AWT - the cached pixel array is a direct reference to the BufferedImage's
		// backing array, so changes to the image are automatically reflected.
	}

	@Override
	public void close()
	{
	}
}
