package nortantis.platform.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import nortantis.platform.ImageType;

/**
 * Decodes PNG files that aren't interlaced. The image type matches what AWT's ImageIO gives for the same file: an RGB file becomes an RGB
 * image, an RGBA file an ARGB image, an 8-bit (or less) gray file a Grayscale8Bit image, and a 16-bit gray file a Grayscale16Bit image.
 * Gray-with-alpha and palette files become ARGB images, or RGB for a palette without transparency.
 */
class PngDecoder
{
	private static final int colorTypeGray = 0;
	private static final int colorTypeRgb = 2;
	private static final int colorTypePalette = 3;
	private static final int colorTypeGrayAlpha = 4;
	private static final int colorTypeRgba = 6;

	static WebImage decode(InputStream stream) throws IOException
	{
		return decode(stream.readAllBytes());
	}

	static WebImage decode(byte[] data) throws IOException
	{
		if (data.length < 8 || (data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G')
		{
			throw new IOException("Not a PNG file.");
		}
		int position = 8;
		int width = 0, height = 0, bitDepth = 0, colorType = 0;
		byte[] palette = null;
		byte[] paletteAlpha = null;
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		while (position + 8 <= data.length)
		{
			int length = readInt(data, position);
			String chunkType = new String(data, position + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
			int chunkStart = position + 8;
			if (chunkType.equals("IHDR"))
			{
				width = readInt(data, chunkStart);
				height = readInt(data, chunkStart + 4);
				bitDepth = data[chunkStart + 8] & 0xFF;
				colorType = data[chunkStart + 9] & 0xFF;
				if ((data[chunkStart + 12] & 0xFF) != 0)
				{
					throw new IOException("Interlaced PNG files aren't supported.");
				}
			}
			else if (chunkType.equals("PLTE"))
			{
				palette = new byte[length];
				System.arraycopy(data, chunkStart, palette, 0, length);
			}
			else if (chunkType.equals("tRNS"))
			{
				paletteAlpha = new byte[length];
				System.arraycopy(data, chunkStart, paletteAlpha, 0, length);
			}
			else if (chunkType.equals("IDAT"))
			{
				compressed.write(data, chunkStart, length);
			}
			else if (chunkType.equals("IEND"))
			{
				break;
			}
			position = chunkStart + length + 4;
		}

		int channels;
		switch (colorType)
		{
			case colorTypeGray:
			case colorTypePalette:
				channels = 1;
				break;
			case colorTypeGrayAlpha:
				channels = 2;
				break;
			case colorTypeRgb:
				channels = 3;
				break;
			case colorTypeRgba:
				channels = 4;
				break;
			default:
				throw new IOException("Unrecognized PNG color type " + colorType);
		}
		int bitsPerPixel = channels * bitDepth;
		int bytesPerPixel = Math.max(1, bitsPerPixel / 8);
		int rowBytes = (width * bitsPerPixel + 7) / 8;
		byte[] raw = inflate(compressed.toByteArray(), (rowBytes + 1) * height);
		byte[] pixels = unfilter(raw, rowBytes, height, bytesPerPixel);

		WebImage image;
		if (colorType == colorTypeGray && bitDepth == 16)
		{
			image = new WebImage(width, height, ImageType.Grayscale16Bit);
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int i = y * rowBytes + x * 2;
					image.shorts[y * width + x] = (short) (((pixels[i] & 0xFF) << 8) | (pixels[i + 1] & 0xFF));
				}
			}
			return image;
		}
		if (colorType == colorTypeGray)
		{
			image = new WebImage(width, height, ImageType.Grayscale8Bit);
			int maxValue = (1 << bitDepth) - 1;
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					image.bytes[y * width + x] = (byte) (sample(pixels, y * rowBytes, x, bitDepth) * 255 / maxValue);
				}
			}
			return image;
		}

		boolean hasAlpha = colorType == colorTypeRgba || colorType == colorTypeGrayAlpha || (colorType == colorTypePalette && paletteAlpha != null);
		image = new WebImage(width, height, hasAlpha ? ImageType.ARGB : ImageType.RGB);
		int step = bitDepth == 16 ? 2 : 1;
		for (int y = 0; y < height; y++)
		{
			int row = y * rowBytes;
			for (int x = 0; x < width; x++)
			{
				int r, g, b, a = 255;
				if (colorType == colorTypePalette)
				{
					int entry = sample(pixels, row, x, bitDepth);
					r = palette[entry * 3] & 0xFF;
					g = palette[entry * 3 + 1] & 0xFF;
					b = palette[entry * 3 + 2] & 0xFF;
					if (paletteAlpha != null && entry < paletteAlpha.length)
					{
						a = paletteAlpha[entry] & 0xFF;
					}
				}
				else
				{
					int i = row + x * bytesPerPixel;
					if (colorType == colorTypeGrayAlpha)
					{
						r = g = b = pixels[i] & 0xFF;
						a = pixels[i + step] & 0xFF;
					}
					else
					{
						r = pixels[i] & 0xFF;
						g = pixels[i + step] & 0xFF;
						b = pixels[i + 2 * step] & 0xFF;
						if (colorType == colorTypeRgba)
						{
							a = pixels[i + 3 * step] & 0xFF;
						}
					}
				}
				image.ints[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
			}
		}
		return image;
	}

	private static int sample(byte[] pixels, int rowStart, int x, int bitDepth)
	{
		if (bitDepth == 8)
		{
			return pixels[rowStart + x] & 0xFF;
		}
		if (bitDepth == 16)
		{
			return pixels[rowStart + x * 2] & 0xFF;
		}
		int bitIndex = x * bitDepth;
		int value = pixels[rowStart + bitIndex / 8] & 0xFF;
		int shift = 8 - bitDepth - (bitIndex % 8);
		return (value >> shift) & ((1 << bitDepth) - 1);
	}

	private static int readInt(byte[] data, int position)
	{
		return ((data[position] & 0xFF) << 24) | ((data[position + 1] & 0xFF) << 16) | ((data[position + 2] & 0xFF) << 8) | (data[position + 3] & 0xFF);
	}

	private static byte[] inflate(byte[] compressed, int expectedSize) throws IOException
	{
		Inflater inflater = new Inflater();
		inflater.setInput(compressed);
		byte[] result = new byte[expectedSize];
		int total = 0;
		try
		{
			while (total < expectedSize && !inflater.finished())
			{
				int count = inflater.inflate(result, total, expectedSize - total);
				if (count == 0 && (inflater.needsInput() || inflater.needsDictionary()))
				{
					break;
				}
				total += count;
			}
		}
		catch (DataFormatException e)
		{
			throw new IOException("Corrupt PNG data.", e);
		}
		finally
		{
			inflater.end();
		}
		return result;
	}

	/**
	 * Reverses the PNG row filters. Each input row starts with its filter type.
	 */
	private static byte[] unfilter(byte[] raw, int rowBytes, int height, int bytesPerPixel) throws IOException
	{
		byte[] result = new byte[rowBytes * height];
		for (int y = 0; y < height; y++)
		{
			int filter = raw[y * (rowBytes + 1)] & 0xFF;
			int in = y * (rowBytes + 1) + 1;
			int out = y * rowBytes;
			int previous = out - rowBytes;
			for (int i = 0; i < rowBytes; i++)
			{
				int value = raw[in + i] & 0xFF;
				int left = i >= bytesPerPixel ? result[out + i - bytesPerPixel] & 0xFF : 0;
				int up = y > 0 ? result[previous + i] & 0xFF : 0;
				int upLeft = (y > 0 && i >= bytesPerPixel) ? result[previous + i - bytesPerPixel] & 0xFF : 0;
				switch (filter)
				{
					case 0:
						break;
					case 1:
						value += left;
						break;
					case 2:
						value += up;
						break;
					case 3:
						value += (left + up) >> 1;
						break;
					case 4:
						// The Paeth predictor: whichever neighbor is closest to left + up - upLeft.
						int estimate = left + up - upLeft;
						int distanceToLeft = Math.abs(estimate - left);
						int distanceToUp = Math.abs(estimate - up);
						int distanceToUpLeft = Math.abs(estimate - upLeft);
						value += (distanceToLeft <= distanceToUp && distanceToLeft <= distanceToUpLeft) ? left : (distanceToUp <= distanceToUpLeft ? up : upLeft);
						break;
					default:
						throw new IOException("Unrecognized PNG filter type " + filter);
				}
				result[out + i] = (byte) value;
			}
		}
		return result;
	}
}
