package nortantis.platform.awt;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.MultiPixelPackedSampleModel;

public class AwtGrayscalePixelReader extends AwtPixelReader
{
	protected final byte[] cachedByteArray;
	/**
	 * For a binary image, its packed bits, eight pixels to a byte with the leftmost in the highest bit, or null if the image doesn't store
	 * them that way from the start of its data. Reading them directly is much faster than going through the raster.
	 */
	private final byte[] cachedBinaryArray;
	private final int binaryScanlineStride;
	protected final int imageSubType;

	AwtGrayscalePixelReader(AwtImage image)
	{
		super(image);
		imageSubType = bufferedImage.getType();
		if (imageSubType == BufferedImage.TYPE_BYTE_GRAY)
		{
			this.cachedByteArray = ((DataBufferByte) raster.getDataBuffer()).getData();
		}
		else
		{
			this.cachedByteArray = null;
		}

		if (imageSubType == BufferedImage.TYPE_BYTE_BINARY && raster.getSampleModel() instanceof MultiPixelPackedSampleModel sampleModel && sampleModel.getPixelBitStride() == 1
				&& sampleModel.getDataBitOffset() == 0 && raster.getSampleModelTranslateX() == 0 && raster.getSampleModelTranslateY() == 0
				&& raster.getDataBuffer() instanceof DataBufferByte dataBuffer && dataBuffer.getOffset() == 0)
		{
			cachedBinaryArray = dataBuffer.getData();
			binaryScanlineStride = sampleModel.getScanlineStride();
		}
		else
		{
			cachedBinaryArray = null;
			binaryScanlineStride = 0;
		}
	}

	@Override
	public int getGrayLevel(int x, int y)
	{
		if (cachedByteArray != null)
		{
			return cachedByteArray[(y * image.getWidth()) + x] & 0xFF;
		}
		if (cachedBinaryArray != null)
		{
			return (cachedBinaryArray[y * binaryScanlineStride + (x >> 3)] >> (7 - (x & 7))) & 1;
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
			gray = getGrayLevel(x, y) * 255;
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
