package nortantis.platform.awt;

import nortantis.geom.IntRectangle;
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
	protected final IntRectangle bounds;

	AwtPixelReader(AwtImage image, IntRectangle bounds)
	{
		this.image = image;
		bufferedImage = AwtFactory.unwrap(image);
		maxPixelLevelAsFloat = image.getMaxPixelLevel();
		createRasters();
		if (image.isCompatibleIntFormat())
		{
			this.cachedPixelArray = ((DataBufferInt) raster.getDataBuffer()).getData();
		}
		this.bounds = bounds;
	}

	AwtPixelReader(AwtImage image)
	{
		this(image, null);
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
		if (cachedPixelArray != null)
		{
			int rgb = cachedPixelArray[(y * image.getWidth()) + x];
			// For non-alpha images (TYPE_INT_RGB), the high byte is undefined garbage.
			// Normalize it to 0xFF to match BufferedImage.getRGB() behavior.
			if (!image.hasAlpha())
			{
				rgb |= 0xFF000000;
			}
			return rgb;
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

		// Images without alpha are fully opaque
		return 255;
	}

	@Override
	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / maxPixelLevelAsFloat;
	}

	@Override
	public IntRectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void close()
	{
	}
}
