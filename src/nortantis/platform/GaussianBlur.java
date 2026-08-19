package nortantis.platform;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Gaussian blur of grayscale images done in the spatial domain instead of through FFT convolution.
 *
 * A 2-dimensional Gaussian is the product of two 1-dimensional Gaussians, so blurring with one can be done as a horizontal pass followed by
 * a vertical pass. That costs O(width * height * kernelWidth) instead of the O(width * height * kernelWidth^2) a 2-dimensional convolution
 * would cost, and unlike FFT convolution it needs no padding to a transform-friendly size and no complex-number scratch arrays.
 *
 * For large blur levels the exact kernel is replaced by three box filters in a row, whose composition approximates a Gaussian closely
 * enough to be visually indistinguishable. Each box filter runs from a sliding sum, so that path costs O(width * height) no matter how wide
 * the blur is.
 *
 * Results match the FFT convolution in {@link ImageHelper#convolveGrayscale(Image, float[][], boolean, boolean)} closely, including its
 * conventions: values outside the image count as zero when padImageToAvoidWrapping is true and wrap around when it is false, and the blur
 * is offset half a pixel up and to the left because the kernel samples land on half-pixel offsets from its center.
 */
class GaussianBlur
{
	/**
	 * How far out from the center, in standard deviations, the exact Gaussian kernel extends. Beyond 3 standard deviations a Gaussian holds
	 * about 0.3% of its weight, which is below what an 8-bit gray level can represent.
	 */
	private static final double gaussianKernelStandardDeviations = 3.0;

	/**
	 * The fewest rows or columns worth giving a thread of its own. Splitting finer than this costs more in coordination than it saves.
	 */
	private static final int minLinesPerTask = 24;

	/**
	 * The most columns the vertical box filters gather into one buffer at a time. Sized so that the buffer stays small enough to remain in
	 * cache while the three filters run over it.
	 */
	private static final int maxColumnsPerBand = 64;

	/**
	 * Which way of blurring to use. Production code gets this from {@link ImageHelper#getBlurAlgorithm()}. The ones it never returns are
	 * kept so that benchmarks and tests can compare them.
	 */
	enum Algorithm
	{
		/**
		 * Convolve with a sampled Gaussian kernel, once horizontally and once vertically.
		 */
		separableGaussian(0),

		/**
		 * Approximate the Gaussian with box filters in a row, each done horizontally and vertically. More of them follow the Gaussian's shape
		 * more closely, at a cost proportional to their number.
		 */
		threeBoxes(3), fourBoxes(4), fiveBoxes(5), sixBoxes(6), eightBoxes(8), twelveBoxes(12),

		/**
		 * Run a third-order recursive filter forwards and then backwards along each line, which costs the same per pixel whatever the blur
		 * level is, like the box filters, but follows the shape of a Gaussian far more closely. Always treats the outside of the image as zero.
		 */
		recursiveGaussian(-1);

		final int boxCount;

		Algorithm(int boxCount)
		{
			this.boxCount = boxCount;
		}

		boolean usesBoxes()
		{
			return boxCount > 0;
		}
	}

	static Image blur(Image image, int blurLevel, boolean maximizeContrast, boolean padImageToAvoidWrapping, Algorithm algorithm)
	{
		ImageType resultType = resultTypeFor(image);
		if (!maximizeContrast)
		{
			return blurToImage(image, blurLevel, padImageToAvoidWrapping, algorithm, resultType, 1f);
		}

		float[] levels = blurToLevels(image, blurLevel, padImageToAvoidWrapping, algorithm);
		setContrast(levels, 0f, 1f);
		return levelsToImage(levels, image.getWidth(), image.getHeight(), resultType);
	}

	static Image blurAndScale(Image image, int blurLevel, float scale, boolean padImageToAvoidWrapping, Algorithm algorithm)
	{
		return blurAndScale(image, blurLevel, scale, padImageToAvoidWrapping, algorithm, resultTypeFor(image));
	}

	static Image blurAndScale(Image image, int blurLevel, float scale, boolean padImageToAvoidWrapping, Algorithm algorithm, ImageType resultType)
	{
		return blurToImage(image, blurLevel, padImageToAvoidWrapping, algorithm, resultType, scale);
	}

	private static ImageType resultTypeFor(Image image)
	{
		return image.getType() == ImageType.Grayscale16Bit ? ImageType.Grayscale16Bit : ImageType.Grayscale8Bit;
	}

	/**
	 * Blurs the given image, multiplies the result by scale, and writes it straight into a new image, without keeping a float buffer of the
	 * blurred levels.
	 */
	private static Image blurToImage(Image image, int blurLevel, boolean padImageToAvoidWrapping, Algorithm algorithm, ImageType resultType, float scale)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		Image result = Image.create(width, height, resultType);
		int maxPixelValue = Image.getMaxPixelLevelForType(resultType);

		if (algorithm != Algorithm.separableGaussian)
		{
			// Only the separable path below produces its output row by row. The others produce it a column at a time, so
			// they cannot write into the image as they go.
			float[] levels = blurToLevels(image, blurLevel, padImageToAvoidWrapping, algorithm);
			scaleLevels(levels, scale);
			return levelsToImage(levels, width, height, resultType);
		}

		float[] kernel = createGaussianKernel1D(blurLevel);
		int centerIndex = kernel.length / 2 - 1;
		boolean wrap = !padImageToAvoidWrapping;

		float[] horizontallyBlurred = new float[width * height];
		readAndConvolveRows(image, horizontallyBlurred, kernel, centerIndex, wrap);

		try (PixelWriter resultPixels = result.createPixelWriter())
		{
			processRangesInParallel(height, (startRow, endRow) ->
			{
				float[] row = new float[width];
				for (int y = startRow; y < endRow; y++)
				{
					convolveOneRowFromColumns(horizontallyBlurred, row, width, height, y, kernel, centerIndex, wrap);
					for (int x = 0; x < width; x++)
					{
						resultPixels.setGrayLevel(x, y, toGrayLevel(row[x] * scale, maxPixelValue));
					}
				}
			});
		}
		return result;
	}

	/**
	 * Blurs the given image and returns the result as gray levels normalized to the range 0 to 1, in row-major order.
	 */
	static float[] blurToLevels(Image image, int blurLevel, boolean padImageToAvoidWrapping, Algorithm algorithm)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		boolean wrap = !padImageToAvoidWrapping;
		float[] horizontallyBlurred = new float[width * height];
		float[] result = new float[width * height];

		if (algorithm.usesBoxes())
		{
			int[][] widthsAndOffsets = createBoxWidthsAndOffsets(blurLevel, algorithm.boxCount);
			readAndBoxBlurRows(image, horizontallyBlurred, widthsAndOffsets, wrap);
			boxBlurColumns(horizontallyBlurred, result, width, height, widthsAndOffsets, wrap);
			return result;
		}

		if (algorithm == Algorithm.recursiveGaussian)
		{
			// The recursion always treats everything outside the image as zero, so it ignores wrapping.
			RecursiveGaussianCoefficients coefficients = RecursiveGaussianCoefficients
					.create(ImageHelper.getInstance().getStandardDeviationSizeForGaussianKernel(blurLevel));
			readAndRecursivelyBlurRows(image, horizontallyBlurred, coefficients);
			recursivelyBlurColumns(horizontallyBlurred, result, width, height, coefficients);
			return result;
		}

		float[] kernel = createGaussianKernel1D(blurLevel);
		int centerIndex = kernel.length / 2 - 1;
		readAndConvolveRows(image, horizontallyBlurred, kernel, centerIndex, wrap);
		processRangesInParallel(height, (startRow, endRow) ->
		{
			float[] row = new float[width];
			for (int y = startRow; y < endRow; y++)
			{
				convolveOneRowFromColumns(horizontallyBlurred, row, width, height, y, kernel, centerIndex, wrap);
				System.arraycopy(row, 0, result, y * width, width);
			}
		});
		return result;
	}

	/**
	 * Creates the 1-dimensional kernel whose outer product with itself is the 2-dimensional Gaussian kernel that
	 * {@code ImageHelper.createGaussianKernel} builds, except truncated to {@link #gaussianKernelStandardDeviations}. Its samples sit on
	 * half-pixel offsets from the center, so its length is always even.
	 */
	static float[] createGaussianKernel1D(int blurLevel)
	{
		double standardDeviation = ImageHelper.getInstance().getStandardDeviationSizeForGaussianKernel(blurLevel);
		int tapsPerSide = Math.min(blurLevel, (int) Math.ceil(gaussianKernelStandardDeviations * standardDeviation));
		if (tapsPerSide < 1)
		{
			tapsPerSide = 1;
		}

		float[] kernel = new float[tapsPerSide * 2];
		double twiceVariance = 2.0 * standardDeviation * standardDeviation;
		float sum = 0f;
		for (int i = 0; i < kernel.length; i++)
		{
			double distanceFromCenter = Math.abs(tapsPerSide - i - 0.5);
			kernel[i] = (float) Math.exp(-(distanceFromCenter * distanceFromCenter) / twiceVariance);
			sum += kernel[i];
		}
		for (int i = 0; i < kernel.length; i++)
		{
			kernel[i] /= sum;
		}
		return kernel;
	}

	/**
	 * Chooses the widths and starting offsets of the given number of box filters whose composition has as close to the same standard
	 * deviation as the Gaussian for the given blur level as whole-pixel widths allow. Each returned array holds the box's width followed by
	 * the offset, from the pixel being written, of the first pixel the box covers.
	 *
	 * More boxes follow the shape of a Gaussian more closely, at a proportional cost, but every count approximates it to within a fixed
	 * fraction that does not depend on the blur level.
	 */
	static int[][] createBoxWidthsAndOffsets(int blurLevel, int boxCount)
	{
		double standardDeviation = ImageHelper.getInstance().getStandardDeviationSizeForGaussianKernel(blurLevel);
		double targetVariance = standardDeviation * standardDeviation;

		// A box of width w has variance (w * w - 1) / 12, so boxCount of them match the target when w is about
		// sqrt(12 * variance / boxCount + 1). Search around that for the mix of widths w and w + 1 that comes closest.
		int estimatedWidth = (int) Math.sqrt(12.0 * targetVariance / boxCount + 1.0);
		int bestNarrowWidth = 1;
		int bestWideCount = 0;
		double bestError = Double.POSITIVE_INFINITY;
		for (int narrowWidth = Math.max(1, estimatedWidth - 2); narrowWidth <= estimatedWidth + 2; narrowWidth++)
		{
			for (int wideCount = 0; wideCount <= boxCount; wideCount++)
			{
				// Composing boxes shifts the result by half a pixel for every even-width box, and the blur has to end up
				// shifted half a pixel overall to match the sampling of the Gaussian kernel. That needs an odd number of
				// even-width boxes.
				int evenWidthCount = narrowWidth % 2 == 0 ? boxCount - wideCount : wideCount;
				if (evenWidthCount % 2 == 0)
				{
					continue;
				}

				int wideWidth = narrowWidth + 1;
				double variance = (wideCount * (wideWidth * wideWidth - 1) + (boxCount - wideCount) * (narrowWidth * narrowWidth - 1)) / 12.0;
				double error = Math.abs(variance - targetVariance);
				if (error < bestError)
				{
					bestError = error;
					bestNarrowWidth = narrowWidth;
					bestWideCount = wideCount;
				}
			}
		}

		int[][] widthsAndOffsets = new int[boxCount][];
		boolean nextEvenBoxShiftsForward = true;
		for (int i = 0; i < boxCount; i++)
		{
			int boxWidth = i < bestWideCount ? bestNarrowWidth + 1 : bestNarrowWidth;
			int offset;
			if (boxWidth % 2 == 1)
			{
				offset = -(boxWidth - 1) / 2;
			}
			else
			{
				// An even-width box cannot be centered on a pixel, so it lands half a pixel to one side. Alternate which
				// side, starting forward, so that an odd number of them leaves a net shift of half a pixel forward.
				offset = nextEvenBoxShiftsForward ? -boxWidth / 2 + 1 : -boxWidth / 2;
				nextEvenBoxShiftsForward = !nextEvenBoxShiftsForward;
			}
			widthsAndOffsets[i] = new int[] { boxWidth, offset };
		}
		return widthsAndOffsets;
	}

	/**
	 * Reads the image's gray levels and convolves each row with the kernel in one pass, so that the unblurred levels never need a buffer of
	 * their own.
	 */
	private static void readAndConvolveRows(Image image, float[] target, float[] kernel, int centerIndex, boolean wrap)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int taps = kernel.length;
		int interiorStart = Math.min(centerIndex, width);
		int interiorEnd = Math.max(interiorStart, width - (taps - 1 - centerIndex));

		try (PixelReader pixels = image.createPixelReader())
		{
			processRangesInParallel(height, (startRow, endRow) ->
			{
				float[] row = new float[width];
				for (int y = startRow; y < endRow; y++)
				{
					readRow(pixels, image, row, width, y);
					int rowOffset = y * width;
					for (int x = 0; x < interiorStart; x++)
					{
						target[rowOffset + x] = convolveOneValueInLine(row, width, x, kernel, centerIndex, wrap);
					}
					for (int x = interiorStart; x < interiorEnd; x++)
					{
						int base = x - centerIndex;
						float sum = 0f;
						for (int i = 0; i < taps; i++)
						{
							sum += kernel[i] * row[base + i];
						}
						target[rowOffset + x] = sum;
					}
					for (int x = interiorEnd; x < width; x++)
					{
						target[rowOffset + x] = convolveOneValueInLine(row, width, x, kernel, centerIndex, wrap);
					}
				}
			});
		}
	}

	/**
	 * Convolves down the columns to produce one output row, reading whole rows of the source at a time so that memory is walked in order.
	 */
	private static void convolveOneRowFromColumns(float[] source, float[] targetRow, int width, int height, int y, float[] kernel, int centerIndex, boolean wrap)
	{
		java.util.Arrays.fill(targetRow, 0f);
		for (int i = 0; i < kernel.length; i++)
		{
			int sourceY = y - centerIndex + i;
			if (wrap)
			{
				sourceY = Math.floorMod(sourceY, height);
			}
			else if (sourceY < 0 || sourceY >= height)
			{
				continue;
			}
			float weight = kernel[i];
			int sourceOffset = sourceY * width;
			for (int x = 0; x < width; x++)
			{
				targetRow[x] += weight * source[sourceOffset + x];
			}
		}
	}

	private static float convolveOneValueInLine(float[] line, int length, int index, float[] kernel, int centerIndex, boolean wrap)
	{
		float sum = 0f;
		for (int i = 0; i < kernel.length; i++)
		{
			int sourceIndex = index - centerIndex + i;
			if (wrap)
			{
				sourceIndex = Math.floorMod(sourceIndex, length);
			}
			else if (sourceIndex < 0 || sourceIndex >= length)
			{
				continue;
			}
			sum += kernel[i] * line[sourceIndex];
		}
		return sum;
	}

	/**
	 * Coefficients of Young and van Vliet's third-order recursive approximation of a Gaussian, which is applied once forwards and once
	 * backwards along a line so that the combined filter is symmetric.
	 *
	 * The recursion is centered on the pixel, but the Gaussian kernel this class reproduces sits half a pixel off center, so the recursion
	 * targets a slightly narrower Gaussian and is followed by an average of each pixel with the next one. That average contributes exactly the
	 * missing half pixel of offset, and a variance of a quarter, which is why it is subtracted here.
	 */
	private static final class RecursiveGaussianCoefficients
	{
		final double feedForward;
		final double feedback1;
		final double feedback2;
		final double feedback3;

		/**
		 * The width-2 average applied after the recursion, as a width and offset pair like the box filters use.
		 */
		static final int[] halfPixelAverage = new int[] { 2, 0 };

		/**
		 * How many standard deviations of extra room the recursion needs on each side of a line. The backwards pass starts with no history, so
		 * it needs to start far enough outside the image for the part it cannot know about to have decayed away.
		 */
		static final double standardDeviationsOfPadding = 5.0;

		private final double paddingStandardDeviation;

		private RecursiveGaussianCoefficients(double feedForward, double feedback1, double feedback2, double feedback3, double paddingStandardDeviation)
		{
			this.feedForward = feedForward;
			this.feedback1 = feedback1;
			this.feedback2 = feedback2;
			this.feedback3 = feedback3;
			this.paddingStandardDeviation = paddingStandardDeviation;
		}

		int calcPadding()
		{
			return (int) Math.ceil(standardDeviationsOfPadding * paddingStandardDeviation) + halfPixelAverage[0];
		}

		static RecursiveGaussianCoefficients create(double standardDeviation)
		{
			double varianceForRecursion = Math.max(0.25, standardDeviation * standardDeviation - 0.25);
			double recursionStandardDeviation = Math.sqrt(varianceForRecursion);

			double q;
			if (recursionStandardDeviation >= 2.5)
			{
				q = 0.98711 * recursionStandardDeviation - 0.96330;
			}
			else
			{
				q = 3.97156 - 4.14554 * Math.sqrt(1.0 - 0.26891 * recursionStandardDeviation);
			}

			double scale0 = 1.57825 + 2.44413 * q + 1.4281 * q * q + 0.422205 * q * q * q;
			double scale1 = 2.44413 * q + 2.85619 * q * q + 1.26661 * q * q * q;
			double scale2 = -(1.4281 * q * q + 1.26661 * q * q * q);
			double scale3 = 0.422205 * q * q * q;

			double feedback1 = scale1 / scale0;
			double feedback2 = scale2 / scale0;
			double feedback3 = scale3 / scale0;
			// Chosen so that each pass leaves a constant signal unchanged, which keeps the whole blur normalized.
			double feedForward = 1.0 - (feedback1 + feedback2 + feedback3);

			return new RecursiveGaussianCoefficients(feedForward, feedback1, feedback2, feedback3, standardDeviation);
		}
	}

	/**
	 * Runs the recursive filter forwards and then backwards along a contiguous line, in place, and then applies the half-pixel average.
	 */
	private static void recursivelyBlurLine(float[] line, float[] scratch, int length, RecursiveGaussianCoefficients coefficients)
	{
		double previous1 = 0;
		double previous2 = 0;
		double previous3 = 0;
		for (int i = 0; i < length; i++)
		{
			double value = coefficients.feedForward * line[i] + coefficients.feedback1 * previous1 + coefficients.feedback2 * previous2
					+ coefficients.feedback3 * previous3;
			line[i] = (float) value;
			previous3 = previous2;
			previous2 = previous1;
			previous1 = value;
		}

		double next1 = 0;
		double next2 = 0;
		double next3 = 0;
		for (int i = length - 1; i >= 0; i--)
		{
			double value = coefficients.feedForward * line[i] + coefficients.feedback1 * next1 + coefficients.feedback2 * next2
					+ coefficients.feedback3 * next3;
			line[i] = (float) value;
			next3 = next2;
			next2 = next1;
			next1 = value;
		}

		boxBlurLine(line, 0, 1, scratch, 0, 1, length, RecursiveGaussianCoefficients.halfPixelAverage, false);
		System.arraycopy(scratch, 0, line, 0, length);
	}

	private static void readAndRecursivelyBlurRows(Image image, float[] target, RecursiveGaussianCoefficients coefficients)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int padding = coefficients.calcPadding();
		int paddedWidth = width + padding * 2;

		try (PixelReader pixels = image.createPixelReader())
		{
			processRangesInParallel(height, (startRow, endRow) ->
			{
				float[] line = new float[paddedWidth];
				float[] scratch = new float[paddedWidth];
				for (int y = startRow; y < endRow; y++)
				{
					java.util.Arrays.fill(line, 0, padding, 0f);
					java.util.Arrays.fill(line, padding + width, paddedWidth, 0f);
					readRow(pixels, image, line, padding, width, y);

					recursivelyBlurLine(line, scratch, paddedWidth, coefficients);
					System.arraycopy(line, padding, target, y * width, width);
				}
			});
		}
	}

	private static void recursivelyBlurColumns(float[] source, float[] target, int width, int height, RecursiveGaussianCoefficients coefficients)
	{
		int padding = coefficients.calcPadding();
		int paddedHeight = height + padding * 2;

		processRangesInParallel(width, maxColumnsPerBand, (bandStart, bandEnd) ->
		{
			int bandColumns = bandEnd - bandStart;
			// Freshly allocated, so the padding above and below each gathered column starts out zero.
			float[] columns = new float[bandColumns * paddedHeight];
			float[] line = new float[paddedHeight];
			float[] scratch = new float[paddedHeight];

			for (int y = 0; y < height; y++)
			{
				int sourceOffset = y * width + bandStart;
				for (int column = 0; column < bandColumns; column++)
				{
					columns[column * paddedHeight + padding + y] = source[sourceOffset + column];
				}
			}

			for (int column = 0; column < bandColumns; column++)
			{
				System.arraycopy(columns, column * paddedHeight, line, 0, paddedHeight);
				recursivelyBlurLine(line, scratch, paddedHeight, coefficients);

				int targetColumn = bandStart + column;
				for (int y = 0; y < height; y++)
				{
					target[y * width + targetColumn] = line[padding + y];
				}
			}
		});
	}

	/**
	 * How far outside a line the three box filters have to be evaluated for the values inside it to come out right.
	 *
	 * Each filter spreads a value out by up to half its width, so a value that starts inside the line can land outside it partway through the
	 * chain and belong back inside by the end. Evaluating only across the line itself would drop such a value at that point, and no later
	 * filter could bring it back. Wrapping loses nothing this way, so it needs no extra.
	 */
	private static int calcBoxChainPadding(int[][] widthsAndOffsets, boolean wrap)
	{
		if (wrap)
		{
			return 0;
		}

		int reachBefore = 0;
		int reachAfter = 0;
		for (int[] widthAndOffset : widthsAndOffsets)
		{
			int boxWidth = widthAndOffset[0];
			int firstOffset = widthAndOffset[1];
			reachBefore += Math.max(0, -firstOffset);
			reachAfter += Math.max(0, firstOffset + boxWidth - 1);
		}
		return Math.max(reachBefore, reachAfter);
	}

	/**
	 * Reads the image's gray levels and runs all three horizontal box filters over each row in one pass. Rows do not depend on each other,
	 * so the three filters need no synchronization between them.
	 */
	private static void readAndBoxBlurRows(Image image, float[] target, int[][] widthsAndOffsets, boolean wrap)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int padding = calcBoxChainPadding(widthsAndOffsets, wrap);
		int paddedWidth = width + padding * 2;

		try (PixelReader pixels = image.createPixelReader())
		{
			processRangesInParallel(height, (startRow, endRow) ->
			{
				float[] lineA = new float[paddedWidth];
				float[] lineB = new float[paddedWidth];
				for (int y = startRow; y < endRow; y++)
				{
					java.util.Arrays.fill(lineA, 0, padding, 0f);
					java.util.Arrays.fill(lineA, padding + width, paddedWidth, 0f);
					readRow(pixels, image, lineA, padding, width, y);

					float[] from = lineA;
					float[] to = lineB;
					for (int[] widthAndOffset : widthsAndOffsets)
					{
						boxBlurLine(from, 0, 1, to, 0, 1, paddedWidth, widthAndOffset, wrap);
						float[] swap = from;
						from = to;
						to = swap;
					}
					System.arraycopy(from, padding, target, y * width, width);
				}
			});
		}
	}

	/**
	 * Runs all three vertical box filters. Columns are gathered into a buffer a band at a time so that the three filters walk contiguous
	 * memory instead of striding through the image three times.
	 */
	private static void boxBlurColumns(float[] source, float[] target, int width, int height, int[][] widthsAndOffsets, boolean wrap)
	{
		int padding = calcBoxChainPadding(widthsAndOffsets, wrap);
		int paddedHeight = height + padding * 2;

		// Gathering a band at a time keeps the gathered buffer small enough to stay in cache while the three filters run
		// over it, and keeps the strided reads of the image contiguous within each row.
		processRangesInParallel(width, maxColumnsPerBand, (bandStart, bandEnd) ->
		{
			int bandColumns = bandEnd - bandStart;
			// Freshly allocated, so the padding above and below each gathered column starts out zero, which is what the
			// filters need to read there.
			float[] columns = new float[bandColumns * paddedHeight];
			float[] scratch = new float[paddedHeight];

			for (int y = 0; y < height; y++)
			{
				int sourceOffset = y * width + bandStart;
				for (int column = 0; column < bandColumns; column++)
				{
					columns[column * paddedHeight + padding + y] = source[sourceOffset + column];
				}
			}

			for (int column = 0; column < bandColumns; column++)
			{
				int base = column * paddedHeight;
				// Alternate between the gathered column and the scratch line. The gathered values are not needed once the
				// first filter has read them, so overwriting them is safe.
				int fromStart = base;
				int toStart = 0;
				float[] from = columns;
				float[] to = scratch;
				for (int[] widthAndOffset : widthsAndOffsets)
				{
					boxBlurLine(from, fromStart, 1, to, toStart, 1, paddedHeight, widthAndOffset, wrap);
					float[] swapArray = from;
					from = to;
					to = swapArray;
					int swapStart = fromStart;
					fromStart = toStart;
					toStart = swapStart;
				}

				int targetColumn = bandStart + column;
				for (int y = 0; y < height; y++)
				{
					target[y * width + targetColumn] = from[fromStart + padding + y];
				}
			}
		});
	}

	/**
	 * Averages a sliding window of the source line into the target line. The window's width and its offset from the value being written come
	 * from widthAndOffset.
	 */
	private static void boxBlurLine(float[] source, int sourceStart, int sourceStride, float[] target, int targetStart, int targetStride, int length, int[] widthAndOffset,
			boolean wrap)
	{
		int boxWidth = widthAndOffset[0];
		int firstOffset = widthAndOffset[1];
		float inverseBoxWidth = 1f / boxWidth;

		double sum = 0;
		for (int i = 0; i < boxWidth; i++)
		{
			sum += readLine(source, sourceStart, sourceStride, length, firstOffset + i, wrap);
		}
		target[targetStart] = (float) (sum * inverseBoxWidth);
		for (int i = 1; i < length; i++)
		{
			sum -= readLine(source, sourceStart, sourceStride, length, i - 1 + firstOffset, wrap);
			sum += readLine(source, sourceStart, sourceStride, length, i + firstOffset + boxWidth - 1, wrap);
			target[targetStart + i * targetStride] = (float) (sum * inverseBoxWidth);
		}
	}

	private static float readLine(float[] source, int start, int stride, int length, int index, boolean wrap)
	{
		if (wrap)
		{
			index = Math.floorMod(index, length);
		}
		else if (index < 0 || index >= length)
		{
			return 0f;
		}
		return source[start + index * stride];
	}

	private static void readRow(PixelReader pixels, Image image, float[] row, int width, int y)
	{
		readRow(pixels, image, row, 0, width, y);
	}

	private static void readRow(PixelReader pixels, Image image, float[] row, int rowOffset, int width, int y)
	{
		if (image.isGrayscaleOrBinary())
		{
			float maxPixelValue = image.getMaxPixelLevel();
			for (int x = 0; x < width; x++)
			{
				row[rowOffset + x] = pixels.getGrayLevel(x, y) / maxPixelValue;
			}
		}
		else
		{
			for (int x = 0; x < width; x++)
			{
				row[rowOffset + x] = pixels.getGrayLevel(x, y);
			}
		}
	}

	private static void setContrast(float[] levels, float targetMin, float targetMax)
	{
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (float level : levels)
		{
			if (level < min)
			{
				min = level;
			}
			if (level > max)
			{
				max = level;
			}
		}

		float range = max - min;
		float targetRange = targetMax - targetMin;
		for (int i = 0; i < levels.length; i++)
		{
			levels[i] = ((levels[i] - min) / range) * targetRange + targetMin;
		}
	}

	private static void scaleLevels(float[] levels, float scale)
	{
		if (scale == 1f)
		{
			return;
		}
		for (int i = 0; i < levels.length; i++)
		{
			levels[i] *= scale;
		}
	}

	private static int toGrayLevel(float level, int maxPixelValue)
	{
		int value = (int) (level * maxPixelValue);
		if (value < 0)
		{
			return 0;
		}
		return Math.min(maxPixelValue, value);
	}

	private static Image levelsToImage(float[] levels, int width, int height, ImageType imageType)
	{
		Image image = Image.create(width, height, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(imageType);
		try (PixelWriter pixels = image.createPixelWriter())
		{
			processRangesInParallel(height, (startRow, endRow) ->
			{
				for (int y = startRow; y < endRow; y++)
				{
					int rowOffset = y * width;
					for (int x = 0; x < width; x++)
					{
						pixels.setGrayLevel(x, y, toGrayLevel(levels[rowOffset + x], maxPixelValue));
					}
				}
			});
		}
		return image;
	}

	private interface RangeConsumer
	{
		void accept(int start, int endExclusive);
	}

	private static void processRangesInParallel(int count, RangeConsumer consumer)
	{
		processRangesInParallel(count, minLinesPerTask, consumer);
	}

	/**
	 * Splits the numbers 0 through count into contiguous ranges of at most minPerTask each and runs them in parallel.
	 *
	 * This uses a fork-join pool rather than the pools in {@code ThreadHelper} because a blur can be started from a thread that is itself one
	 * of that class's workers - icon shading masks are blurred lazily from inside icon drawing jobs, for instance. Submitting work to a pool
	 * from one of its own threads and then waiting on it risks deadlock, whereas a fork-join pool resolves the same nesting by having the
	 * waiting thread help run the queued work.
	 */
	private static void processRangesInParallel(int count, int minPerTask, RangeConsumer consumer)
	{
		if (count <= minPerTask)
		{
			consumer.accept(0, count);
			return;
		}

		// Split into a few more pieces than there are threads, for load balancing, but never into pieces smaller than
		// minPerTask, below which coordinating the split costs more than it saves.
		int maxTaskCount = Math.min(count / minPerTask, blurThreadPool.getParallelism() * 4);
		int maxPerTask = Math.max(minPerTask, (count + maxTaskCount - 1) / maxTaskCount);
		blurThreadPool.invoke(new RangeTask(0, count, maxPerTask, consumer));
	}

	private static final ForkJoinPool blurThreadPool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()));

	private static class RangeTask extends RecursiveAction
	{
		private static final long serialVersionUID = 1L;

		private final int start;
		private final int end;
		private final int maxPerTask;
		private final transient RangeConsumer consumer;

		RangeTask(int start, int end, int maxPerTask, RangeConsumer consumer)
		{
			this.start = start;
			this.end = end;
			this.maxPerTask = maxPerTask;
			this.consumer = consumer;
		}

		@Override
		protected void compute()
		{
			int count = end - start;
			if (count <= maxPerTask)
			{
				consumer.accept(start, end);
				return;
			}
			int middle = start + count / 2;
			invokeAll(new RangeTask(start, middle, maxPerTask, consumer), new RangeTask(middle, end, maxPerTask, consumer));
		}
	}
}
