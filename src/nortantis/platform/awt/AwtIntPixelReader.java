package nortantis.platform.awt;

import nortantis.geom.IntRectangle;

import java.awt.image.DataBufferInt;

public class AwtIntPixelReader extends AwtPixelReader
{
	protected final int[] cachedPixelArray;

	AwtIntPixelReader(AwtImage image, IntRectangle bounds)
	{
		super(image, bounds);
		this.cachedPixelArray = ((DataBufferInt) raster.getDataBuffer()).getData();
	}

	AwtIntPixelReader(AwtImage image)
	{
		this(image, null);
	}

	@Override
	public int getRGB(int x, int y)
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
}
