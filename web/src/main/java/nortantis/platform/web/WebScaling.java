package nortantis.platform.web;

import nortantis.platform.ImageType;

/**
 * Resizes images. Each axis is resampled separately: an axis that shrinks averages the source pixels each result pixel covers, weighting the
 * partly covered ones at its edges, and an axis that grows interpolates linearly between neighboring source pixels. Color is averaged with
 * each pixel weighted by its alpha, so transparent pixels don't darken the edges of what's around them.
 */
class WebScaling
{
	/**
	 * For one axis, the source pixels and weights that make up each result pixel.
	 */
	private static class Contributions
	{
		final int[] start;
		final int[] count;
		final float[] weights;
		final int maxCount;

		Contributions(int[] start, int[] count, float[] weights, int maxCount)
		{
			this.start = start;
			this.count = count;
			this.weights = weights;
			this.maxCount = maxCount;
		}
	}

	static WebImage scale(WebImage source, int width, int height)
	{
		width = Math.max(1, width);
		height = Math.max(1, height);
		ImageType type = source.getType();
		WebImage result = new WebImage(width, height, type);
		Contributions columns = contributions(source.width, width);
		Contributions rows = contributions(source.height, height);

		if (source.ints != null)
		{
			scaleColor(source, result, columns, rows, type == ImageType.ARGB);
		}
		else
		{
			float[] gray = resampleGray(source, columns, rows, width, height);
			if (source.shorts != null)
			{
				for (int i = 0; i < gray.length; i++)
				{
					result.shorts[i] = (short) Math.max(0, Math.min(65535, Math.round(gray[i])));
				}
			}
			else if (type == ImageType.Binary)
			{
				for (int i = 0; i < gray.length; i++)
				{
					result.bytes[i] = (byte) (gray[i] >= 0.5f ? 1 : 0);
				}
			}
			else
			{
				for (int i = 0; i < gray.length; i++)
				{
					result.bytes[i] = (byte) clamp(Math.round(gray[i]));
				}
			}
		}
		return result;
	}

	/**
	 * Resamples all four channels of a color image together. Colors are premultiplied by alpha while they're averaged.
	 */
	private static void scaleColor(WebImage source, WebImage result, Contributions columns, Contributions rows, boolean hasAlpha)
	{
		int sourceWidth = source.width;
		int sourceHeight = source.height;
		int width = result.width;
		int height = result.height;
		// The horizontal pass's result: four floats per pixel (alpha, then premultiplied red, green, and blue).
		float[] horizontal = new float[width * sourceHeight * 4];
		for (int y = 0; y < sourceHeight; y++)
		{
			int rowStart = source.offset + y * source.stride;
			int outRow = y * width * 4;
			for (int x = 0; x < width; x++)
			{
				int start = columns.start[x];
				int count = columns.count[x];
				int weightIndex = x * columns.maxCount;
				float a = 0, r = 0, g = 0, b = 0;
				for (int k = 0; k < count; k++)
				{
					int argb = source.ints[rowStart + start + k];
					float weight = columns.weights[weightIndex + k];
					float pixelAlpha = hasAlpha ? (argb >>> 24) : 255f;
					float alphaWeight = weight * pixelAlpha;
					a += alphaWeight;
					r += ((argb >> 16) & 0xFF) * alphaWeight;
					g += ((argb >> 8) & 0xFF) * alphaWeight;
					b += (argb & 0xFF) * alphaWeight;
				}
				int out = outRow + x * 4;
				horizontal[out] = a;
				horizontal[out + 1] = r;
				horizontal[out + 2] = g;
				horizontal[out + 3] = b;
			}
		}

		int[] pixels = result.ints;
		for (int y = 0; y < height; y++)
		{
			int start = rows.start[y];
			int count = rows.count[y];
			int weightIndex = y * rows.maxCount;
			for (int x = 0; x < width; x++)
			{
				float a = 0, r = 0, g = 0, b = 0;
				for (int k = 0; k < count; k++)
				{
					float weight = rows.weights[weightIndex + k];
					int in = ((start + k) * width + x) * 4;
					a += horizontal[in] * weight;
					r += horizontal[in + 1] * weight;
					g += horizontal[in + 2] * weight;
					b += horizontal[in + 3] * weight;
				}
				int alpha = hasAlpha ? clamp(Math.round(a)) : 255;
				int red = 0, green = 0, blue = 0;
				if (a > 0f)
				{
					red = clamp(Math.round(r / a));
					green = clamp(Math.round(g / a));
					blue = clamp(Math.round(b / a));
				}
				pixels[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
			}
		}
	}

	private static int clamp(int value)
	{
		return value < 0 ? 0 : (value > 255 ? 255 : value);
	}

	/**
	 * Resamples a grayscale image's levels.
	 */
	private static float[] resampleGray(WebImage source, Contributions columns, Contributions rows, int width, int height)
	{
		int sourceWidth = source.width;
		int sourceHeight = source.height;
		float[] horizontal = new float[width * sourceHeight];
		float[] sourceRow = new float[sourceWidth];
		for (int y = 0; y < sourceHeight; y++)
		{
			int rowStart = source.offset + y * source.stride;
			for (int x = 0; x < sourceWidth; x++)
			{
				sourceRow[x] = source.shorts != null ? source.shorts[rowStart + x] & 0xFFFF : source.bytes[rowStart + x] & 0xFF;
			}
			int outRow = y * width;
			for (int x = 0; x < width; x++)
			{
				int start = columns.start[x];
				int count = columns.count[x];
				int weightIndex = x * columns.maxCount;
				float sum = 0f;
				for (int k = 0; k < count; k++)
				{
					sum += sourceRow[start + k] * columns.weights[weightIndex + k];
				}
				horizontal[outRow + x] = sum;
			}
		}

		float[] result = new float[width * height];
		for (int y = 0; y < height; y++)
		{
			int start = rows.start[y];
			int count = rows.count[y];
			int weightIndex = y * rows.maxCount;
			int outRow = y * width;
			for (int k = 0; k < count; k++)
			{
				float weight = rows.weights[weightIndex + k];
				int inRow = (start + k) * width;
				for (int x = 0; x < width; x++)
				{
					result[outRow + x] += horizontal[inRow + x] * weight;
				}
			}
		}
		return result;
	}

	private static Contributions contributions(int sourceSize, int resultSize)
	{
		double scale = (double) sourceSize / resultSize;
		int[] start = new int[resultSize];
		int[] count = new int[resultSize];
		int maxCount;
		float[] weights;
		if (resultSize <= sourceSize)
		{
			// Shrinking: each result pixel averages the source pixels its span covers.
			maxCount = (int) Math.ceil(scale) + 2;
			weights = new float[resultSize * maxCount];
			for (int i = 0; i < resultSize; i++)
			{
				double from = i * scale;
				double to = Math.min(sourceSize, (i + 1) * scale);
				int first = (int) Math.floor(from);
				int last = Math.min(sourceSize, (int) Math.ceil(to));
				start[i] = first;
				int n = 0;
				for (int s = first; s < last; s++)
				{
					double weight = (Math.min(s + 1, to) - Math.max(s, from)) / scale;
					weights[i * maxCount + n] = (float) weight;
					n++;
				}
				count[i] = n;
			}
		}
		else
		{
			// Growing: linear interpolation between the two nearest source pixel centers.
			maxCount = 2;
			weights = new float[resultSize * maxCount];
			for (int i = 0; i < resultSize; i++)
			{
				double center = (i + 0.5) * scale - 0.5;
				int first = (int) Math.floor(center);
				double fraction = center - first;
				if (first < 0)
				{
					first = 0;
					fraction = 0;
				}
				if (first >= sourceSize - 1)
				{
					first = sourceSize - 1;
					fraction = 0;
				}
				start[i] = first;
				if (fraction == 0)
				{
					count[i] = 1;
					weights[i * maxCount] = 1f;
				}
				else
				{
					count[i] = 2;
					weights[i * maxCount] = (float) (1 - fraction);
					weights[i * maxCount + 1] = (float) fraction;
				}
			}
		}
		return new Contributions(start, count, weights, maxCount);
	}
}
