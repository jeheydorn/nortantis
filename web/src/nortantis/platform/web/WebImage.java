package nortantis.platform.web;

import java.util.Arrays;

import nortantis.geom.IntRectangle;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PixelReader;
import nortantis.platform.PixelReaderWriter;
import nortantis.platform.PixelWriter;
import nortantis.platform.ScalingMethod;

/**
 * An image whose pixels are held in a Java array: packed ARGB ints for ARGB and RGB images (an RGB pixel's alpha byte is always 255), one
 * byte per pixel for Grayscale8Bit and Binary images (a binary pixel holds 0 or 1), and one short per pixel for Grayscale16Bit images. A
 * sub-image shares its parent's array, so pixel (x, y) is at {@code offset + y * stride + x}.
 */
public class WebImage extends Image
{
	final int width;
	final int height;
	final int offset;
	final int stride;
	final int[] ints;
	final byte[] bytes;
	final short[] shorts;

	public WebImage(int width, int height, ImageType type)
	{
		super(type);
		this.width = width;
		this.height = height;
		this.offset = 0;
		this.stride = width;
		int size = Math.max(0, width) * Math.max(0, height);
		if (type == ImageType.ARGB || type == ImageType.RGB)
		{
			ints = new int[size];
			bytes = null;
			shorts = null;
			if (type == ImageType.RGB)
			{
				Arrays.fill(ints, 0xFF000000);
			}
		}
		else if (type == ImageType.Grayscale16Bit)
		{
			ints = null;
			bytes = null;
			shorts = new short[size];
		}
		else
		{
			ints = null;
			bytes = new byte[size];
			shorts = null;
		}
	}

	private WebImage(WebImage parent, int x, int y, int width, int height)
	{
		super(parent.getType());
		this.width = width;
		this.height = height;
		this.offset = parent.offset + y * parent.stride + x;
		this.stride = parent.stride;
		this.ints = parent.ints;
		this.bytes = parent.bytes;
		this.shorts = parent.shorts;
	}

	@Override
	public int getWidth()
	{
		return width;
	}

	@Override
	public int getHeight()
	{
		return height;
	}

	@Override
	public Painter createPainter(DrawQuality quality)
	{
		return new WebPainter(this, quality);
	}

	@Override
	protected PixelReader innerCreateNewPixelReader(IntRectangle bounds)
	{
		return createPixels();
	}

	@Override
	protected PixelReaderWriter innerCreateNewPixelReaderWriter(IntRectangle bounds)
	{
		return createPixels();
	}

	@Override
	protected PixelWriter innerCreateNewPixelWriter(IntRectangle bounds)
	{
		return createPixels();
	}

	private PixelReaderWriter createPixels()
	{
		if (ints != null)
		{
			return new WebIntPixels(this);
		}
		if (shorts != null)
		{
			return new WebShortPixels(this);
		}
		return new WebBytePixels(this);
	}

	/**
	 * The pixel at (x, y) as an ARGB int, the way {@link PixelReader#getRGB} gives it.
	 */
	int getARGB(int x, int y)
	{
		int i = offset + y * stride + x;
		if (ints != null)
		{
			return ints[i];
		}
		int gray;
		if (shorts != null)
		{
			gray = (shorts[i] & 0xFFFF) >> 8;
		}
		else if (getType() == ImageType.Binary)
		{
			gray = bytes[i] == 0 ? 0 : 255;
		}
		else
		{
			gray = bytes[i] & 0xFF;
		}
		return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
	}

	/**
	 * The array that holds the pixels: an int[] of ARGB values for color images, a short[] for 16-bit grayscale, and a byte[] otherwise.
	 * Pixel (x, y) is at x + y * width, except in a sub-image.
	 */
	public Object getPixelArray()
	{
		if (ints != null)
		{
			return ints;
		}
		return shorts != null ? shorts : bytes;
	}

	/**
	 * Every pixel as an ARGB int, row after row with no gaps between rows.
	 */
	public int[] toPackedARGB()
	{
		int[] result = new int[width * height];
		if (ints != null)
		{
			for (int y = 0; y < height; y++)
			{
				System.arraycopy(ints, offset + y * stride, result, y * width, width);
			}
			return result;
		}
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				result[y * width + x] = getARGB(x, y);
			}
		}
		return result;
	}

	@Override
	public Image scale(ScalingMethod method, int width, int height)
	{
		return WebScaling.scale(this, width, height);
	}

	@Override
	public Image deepCopy()
	{
		WebImage copy = new WebImage(width, height, getType());
		copyPixels(this, 0, 0, copy, 0, 0, width, height);
		return copy;
	}

	@Override
	public Image getSubImage(IntRectangle bounds)
	{
		return new WebImage(this, bounds.x, bounds.y, bounds.width, bounds.height);
	}

	@Override
	public Image copySubImage(IntRectangle bounds, boolean addAlphaChanel)
	{
		ImageType resultType = addAlphaChanel ? ImageType.ARGB : getType();
		WebImage result = new WebImage(bounds.width, bounds.height, resultType);
		IntRectangle clipped = bounds.findIntersection(new IntRectangle(0, 0, width, height));
		if (clipped == null || clipped.width <= 0 || clipped.height <= 0)
		{
			return result;
		}
		copyPixels(this, clipped.x, clipped.y, result, clipped.x - bounds.x, clipped.y - bounds.y, clipped.width, clipped.height);
		return result;
	}

	@Override
	public Image copyAndAddAlphaChanel()
	{
		WebImage result = new WebImage(width, height, ImageType.ARGB);
		copyPixels(this, 0, 0, result, 0, 0, width, height);
		return result;
	}

	/**
	 * Copies a block of pixels, converting between image types when they differ.
	 */
	static void copyPixels(WebImage source, int sourceX, int sourceY, WebImage target, int targetX, int targetY, int width, int height)
	{
		if (source.getType() == target.getType())
		{
			for (int y = 0; y < height; y++)
			{
				int from = source.offset + (sourceY + y) * source.stride + sourceX;
				int to = target.offset + (targetY + y) * target.stride + targetX;
				if (source.ints != null)
				{
					System.arraycopy(source.ints, from, target.ints, to, width);
				}
				else if (source.shorts != null)
				{
					System.arraycopy(source.shorts, from, target.shorts, to, width);
				}
				else
				{
					System.arraycopy(source.bytes, from, target.bytes, to, width);
				}
			}
			return;
		}

		try (PixelReaderWriter targetPixels = target.createPixelReaderWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int argb = source.getARGB(sourceX + x, sourceY + y);
					if (target.getType() == ImageType.RGB)
					{
						argb |= 0xFF000000;
					}
					targetPixels.setRGB(targetX + x, targetY + y, argb);
				}
			}
		}
	}
}
