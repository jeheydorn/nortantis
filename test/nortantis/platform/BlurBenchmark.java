package nortantis.platform;

import nortantis.geom.IntPoint;
import nortantis.platform.awt.AwtFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Compares the cost and the output of FFT convolution against the spatial blurs in {@link GaussianBlur}.
 *
 * Run with: ./gradlew test --tests "nortantis.platform.BlurBenchmark" -DrunBenchmarks=true
 */
@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")
public class BlurBenchmark
{
	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new AwtFactory());
	}

	@Test
	public void compareBlurAndScale()
	{
		System.out.println("\n=== blurAndScale: FFT vs separable Gaussian vs three box filters ===");
		System.out.println("Threads available: " + nortantis.util.ThreadHelper.getInstance().getThreadCount());

		int[] imageSizes = { 128, 256, 512, 1024, 2048 };
		int[] blurLevels = { 5, 15, 30, 60, 120 };
		float scale = 0.35f;

		// Warm up everything once so that class loading and JIT compilation do not land on whichever configuration runs first.
		try (Image warmupMask = createCoastlineLikeMask(512, 512, 1))
		{
			for (int i = 0; i < 5; i++)
			{
				closeQuietly(fftBlurAndScale(warmupMask, 20, scale));
				closeQuietly(GaussianBlur.blurAndScale(warmupMask, 20, scale, true, GaussianBlur.Algorithm.separableGaussian));
				closeQuietly(GaussianBlur.blurAndScale(warmupMask, 20, scale, true, GaussianBlur.Algorithm.threeBoxes));
			}
		}

		for (int imageSize : imageSizes)
		{
			try (Image mask = createCoastlineLikeMask(imageSize, imageSize, 42))
			{
				for (int blurLevel : blurLevels)
				{
					System.out.println("\n--- " + imageSize + "x" + imageSize + ", blurLevel " + blurLevel + " (kernel "
							+ (blurLevel * 2) + "x" + (blurLevel * 2) + ", sigma " + String.format("%.2f", blurLevel / 6.0) + ") ---");

					Image fftResult = null;
					Image separableResult = null;
					Image boxResult = null;
					try
					{
						long fftTime = time(3, () -> closeQuietly(fftBlurAndScale(mask, blurLevel, scale)));
						long separableTime = time(10,
								() -> closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.separableGaussian)));
						long boxTime = time(10, () -> closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.threeBoxes)));

						fftResult = fftBlurAndScale(mask, blurLevel, scale);
						separableResult = GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.separableGaussian);
						boxResult = GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.threeBoxes);

						System.out.println("  FFT:        " + formatTime(fftTime));
						System.out.println("  separable:  " + formatTime(separableTime) + "   " + String.format("%.1fx faster", fftTime / (double) separableTime)
								+ "   " + describeDifference(fftResult, separableResult));
						System.out.println("  three box:  " + formatTime(boxTime) + "   " + String.format("%.1fx faster", fftTime / (double) boxTime) + "   "
								+ describeDifference(fftResult, boxResult));
					}
					finally
					{
						closeQuietly(fftResult);
						closeQuietly(separableResult);
						closeQuietly(boxResult);
					}
				}
			}
		}
	}

	/**
	 * Uses the sizes and blur levels a real map produces. At Medium display quality the resolution scale is 1.0, so a preset map is 4096
	 * wide and MapCreator's sizeMultiplier is 8/3. The coast shading slider defaults to 30 and goes to 100, so blurLevel runs from 80 to 266.
	 */
	@Test
	public void compareAtRealisticMapSizes()
	{
		System.out.println("\n=== Realistic map sizes and blur levels (Medium quality, 4096 wide) ===");
		double sizeMultiplier = 8.0 / 3.0;

		int[][] sizes = { { 4096, 2304 }, { 4096, 2531 }, { 4096, 4096 } };
		int[] shadingSliderValues = { 10, 30, 60, 100 };

		// Snippet sizes an incremental brush-stroke draw produces, which is the brush area padded by the effects width.
		int[][] snippetSizes = { { 384, 384 }, { 640, 640 }, { 1024, 1024 }, { 1536, 1536 } };

		System.out.println("\n--- Full map draws and exports ---");
		for (int[] size : sizes)
		{
			runOneComparison(size[0], size[1], 30, sizeMultiplier, "coast shading 30 (default)");
		}

		System.out.println("\n--- 4096x2304, across the coast shading slider ---");
		for (int sliderValue : shadingSliderValues)
		{
			runOneComparison(4096, 2304, sliderValue, sizeMultiplier, "coast shading " + sliderValue);
		}

		System.out.println("\n--- Brush stroke snippets, coast shading 30 (default) ---");
		for (int[] snippetSize : snippetSizes)
		{
			runOneComparison(snippetSize[0], snippetSize[1], 30, sizeMultiplier, "coast shading 30 (default)");
		}

		System.out.println("\n--- Brush stroke snippets, coast shading 100 (maximum) ---");
		for (int[] snippetSize : snippetSizes)
		{
			runOneComparison(snippetSize[0], snippetSize[1], 100, sizeMultiplier, "coast shading 100");
		}
	}

	/**
	 * Mirrors how MapCreator turns a coast shading slider value into a blur level and a scale. The scale matters to this comparison: it
	 * amplifies the blurred mask about tenfold, so it magnifies any difference between the methods by the same factor.
	 */
	private void runOneComparison(int width, int height, int shadingSliderValue, double sizeMultiplier, String label)
	{
		int blurLevel = (int) (shadingSliderValue * sizeMultiplier);
		final float coastlineShadingScale = 5.27f;
		float scale = coastlineShadingScale * ImageHelper.getInstance().getGaussianMode((int) (15 * sizeMultiplier))
				/ ImageHelper.getInstance().getGaussianMode(blurLevel);

		try (Image mask = createCoastlineLikeMask(width, height, 42))
		{
			long fftTime = time(3, () -> closeQuietly(fftBlurAndScale(mask, blurLevel, scale)));
			long separableTime = time(5, () -> closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.separableGaussian)));
			long boxTime = time(5, () -> closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.threeBoxes)));
			long recursiveTime = time(5, () -> closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.recursiveGaussian)));

			try (Image fftResult = fftBlurAndScale(mask, blurLevel, scale);
					Image separableResult = GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.separableGaussian);
					Image recursiveResult = GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.recursiveGaussian);
					Image boxResult = GaussianBlur.blurAndScale(mask, blurLevel, scale, true, GaussianBlur.Algorithm.threeBoxes))
			{
				System.out.println("\n  " + width + "x" + height + ", " + label + " -> blurLevel " + blurLevel + " (kernel " + (blurLevel * 2) + "x"
						+ (blurLevel * 2) + "), scale " + String.format("%.2f", scale));
				System.out.println("    FFT:        " + formatTime(fftTime));
				System.out.println("    separable:  " + formatTime(separableTime) + "   " + String.format("%.1fx", fftTime / (double) separableTime) + "   "
						+ describeDifference(fftResult, separableResult));
				System.out.println("    three box:  " + formatTime(boxTime) + "   " + String.format("%.1fx", fftTime / (double) boxTime) + "   "
						+ describeDifference(fftResult, boxResult));
				System.out.println("    recursive:  " + formatTime(recursiveTime) + "   " + String.format("%.1fx", fftTime / (double) recursiveTime) + "   "
						+ describeDifference(fftResult, recursiveResult));
			}
		}
	}

	/**
	 * Blurs from inside jobs that are themselves running on ThreadHelper's pools, which is what icon shading masks do. Fails rather than
	 * hanging if the blur ever waits on a pool it is already running inside of.
	 */
	@Test
	public void blurringFromInsideParallelJobsDoesNotDeadlock() throws Exception
	{
		int jobCount = nortantis.util.ThreadHelper.getInstance().getThreadCount() * 2;
		List<Runnable> jobs = new ArrayList<>();
		for (int i = 0; i < jobCount; i++)
		{
			jobs.add(() ->
			{
				try (Image mask = createCoastlineLikeMask(512, 512, 3))
				{
					closeQuietly(GaussianBlur.blurAndScale(mask, 80, 0.35f, true, GaussianBlur.Algorithm.threeBoxes));
					closeQuietly(GaussianBlur.blur(mask, 80, true, true, GaussianBlur.Algorithm.separableGaussian));
				}
			});
		}

		Thread runner = new Thread(() ->
		{
			nortantis.util.ThreadHelper.getInstance().processInParallel(jobs, true);
			nortantis.util.ThreadHelper.getInstance().processInParallel(jobs, false);
		});
		runner.start();
		runner.join(120_000);
		if (runner.isAlive())
		{
			throw new AssertionError("Blurring from inside ThreadHelper's pools deadlocked.");
		}
		System.out.println("\n=== Blurring from inside both of ThreadHelper's pools completed without deadlock ===");
	}

	/**
	 * Measures each method against an exact double-precision Gaussian convolution, as a fraction of the blurred image's peak value.
	 *
	 * Comparing 8-bit outputs hides errors whenever the blurred image is dim: the grunge box's peak is about 0.008, so its whole 8-bit output
	 * spans two or three gray levels, and a comparison there measures quantization instead of accuracy. Error relative to the peak is what
	 * survives a contrast stretch - after stretching, the difference in gray levels is about 255 times the fraction reported here.
	 */
	@Test
	public void measureAccuracyAgainstExactGaussian()
	{
		System.out.println("\n=== Accuracy vs exact double-precision Gaussian (error as a fraction of the blurred peak) ===");

		for (int blurLevel : new int[] { 20, 70, 80, 266 })
		{
			for (boolean lineAtEdge : new boolean[] { true, false })
			{
				int size = Math.max(142, blurLevel * 2 + 2);
				try (Image mask = lineAtEdge ? createGrungeLikeBox(size) : createCoastlineLikeMask(size, size, 42); Image mask16Bit = toGrayscale16Bit(mask))
				{
					double[] exact = convolveExactlyInDouble(mask16Bit, blurLevel);
					double peak = 0;
					for (double value : exact)
					{
						peak = Math.max(peak, value);
					}

					System.out.println("\n  blurLevel " + blurLevel + ", " + size + "x" + size + ", "
							+ (lineAtEdge ? "grunge L-line at the image edge" : "coastlines") + " (peak " + String.format("%.5f", peak) + ")");
					System.out.println("    FFT:         " + describeRelativeError(exact, peak, blurredLevelsFromFft(mask16Bit, blurLevel)));
					System.out.println("    separable:   " + describeRelativeError(exact, peak, blurredLevels(mask16Bit, blurLevel, GaussianBlur.Algorithm.separableGaussian)));
					for (GaussianBlur.Algorithm algorithm : boxAlgorithms())
					{
						String name = algorithm == GaussianBlur.Algorithm.recursiveGaussian ? "recursive:" : algorithm.boxCount + " boxes:";
						System.out.println(String.format("    %-13s", name) + describeRelativeError(exact, peak, blurredLevels(mask16Bit, blurLevel, algorithm)));
					}
				}
			}
		}
	}

	/**
	 * Compares each method's impulse response, on a single row far from any edge, against the exact Gaussian. This separates the filter's own
	 * shape error from anything to do with image boundaries: an impulse in the middle of a wide image cannot reach an edge.
	 */
	@Test
	public void measureImpulseResponseAccuracy()
	{
		System.out.println("\n=== Impulse response accuracy, impulse centered far from any edge ===");
		for (int blurLevel : new int[] { 20, 80, 266 })
		{
			int size = blurLevel * 6;
			try (Image impulse = Image.create(size, size, ImageType.Grayscale16Bit))
			{
				try (PixelWriter pixels = impulse.createPixelWriter())
				{
					pixels.setGrayLevel(size / 2, size / 2, impulse.getMaxPixelLevel());
				}

				double[] exact = convolveExactlyInDouble(impulse, blurLevel);
				double peak = 0;
				for (double value : exact)
				{
					peak = Math.max(peak, value);
				}

				System.out.println("\n  blurLevel " + blurLevel + ", " + size + "x" + size + " (peak " + String.format("%.6f", peak) + ")");
				System.out.println("    separable:   " + describeRelativeError(exact, peak, blurredFloatLevels(impulse, blurLevel, GaussianBlur.Algorithm.separableGaussian)));
				System.out.println("    3 boxes:     " + describeRelativeError(exact, peak, blurredFloatLevels(impulse, blurLevel, GaussianBlur.Algorithm.threeBoxes)));
				System.out.println("    recursive:   " + describeRelativeError(exact, peak, blurredFloatLevels(impulse, blurLevel, GaussianBlur.Algorithm.recursiveGaussian)));
			}
		}
	}

	/**
	 * Reads the blurred levels straight out of the float pipeline, with no image quantization in the way at all.
	 */
	private double[] blurredFloatLevels(Image mask, int blurLevel, GaussianBlur.Algorithm algorithm)
	{
		float[] levels = GaussianBlur.blurToLevels(mask, blurLevel, true, algorithm);
		double[] result = new double[levels.length];
		for (int i = 0; i < levels.length; i++)
		{
			result[i] = levels[i];
		}
		return result;
	}

	private static GaussianBlur.Algorithm[] boxAlgorithms()
	{
		return new GaussianBlur.Algorithm[] { GaussianBlur.Algorithm.threeBoxes, GaussianBlur.Algorithm.fourBoxes, GaussianBlur.Algorithm.fiveBoxes,
				GaussianBlur.Algorithm.sixBoxes, GaussianBlur.Algorithm.eightBoxes, GaussianBlur.Algorithm.twelveBoxes, GaussianBlur.Algorithm.recursiveGaussian };
	}

	private String describeRelativeError(double[] exact, double peak, double[] actual)
	{
		double maxError = 0;
		double totalError = 0;
		for (int i = 0; i < exact.length; i++)
		{
			double error = Math.abs(exact[i] - actual[i]);
			maxError = Math.max(maxError, error);
			totalError += error;
		}
		return String.format("max %.6f of peak (%6.1f gray levels once stretched), mean %.6f", maxError / peak, 255.0 * maxError / peak,
				(totalError / exact.length) / peak);
	}

	/**
	 * Blurs with the given algorithm and returns the normalized levels. The mask is 16-bit so that the output is too, which keeps output
	 * quantization far below the differences being measured.
	 */
	private double[] blurredLevels(Image mask16Bit, int blurLevel, GaussianBlur.Algorithm algorithm)
	{
		try (Image blurred = GaussianBlur.blurAndScale(mask16Bit, blurLevel, 1f, true, algorithm))
		{
			return readNormalizedLevels(blurred);
		}
	}

	private double[] blurredLevelsFromFft(Image mask16Bit, int blurLevel)
	{
		float[][] kernel = ImageHelper.getInstance().createGaussianKernel(blurLevel);
		try (Image blurred = ImageHelper.getInstance().convolveGrayscaleThenScale(mask16Bit, kernel, 1f, true))
		{
			return readNormalizedLevels(blurred);
		}
	}

	/**
	 * Copies a mask into a 16-bit grayscale image, so that blurring it produces 16-bit output.
	 */
	private Image toGrayscale16Bit(Image mask)
	{
		int width = mask.getWidth();
		int height = mask.getHeight();
		Image result = Image.create(width, height, ImageType.Grayscale16Bit);
		int targetMaxPixelValue = result.getMaxPixelLevel();
		int sourceMaxPixelValue = mask.getMaxPixelLevel();
		try (PixelReader sourcePixels = mask.createPixelReader(); PixelWriter targetPixels = result.createPixelWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					targetPixels.setGrayLevel(x, y, sourcePixels.getGrayLevel(x, y) * targetMaxPixelValue / sourceMaxPixelValue);
				}
			}
		}
		return result;
	}

	private double[] readNormalizedLevels(Image image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		double[] levels = new double[width * height];
		double maxPixelValue = image.getMaxPixelLevel();
		try (PixelReader pixels = image.createPixelReader())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					levels[y * width + x] = pixels.getGrayLevel(x, y) / maxPixelValue;
				}
			}
		}
		return levels;
	}

	/**
	 * Convolves with the full untruncated Gaussian in double precision, treating everything outside the image as zero. It is separable, and
	 * both passes are evaluated everywhere they are needed, so this is exact apart from double rounding.
	 */
	private double[] convolveExactlyInDouble(Image image, int blurLevel)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		double standardDeviation = blurLevel / 6.0;

		int tapsPerSide = blurLevel;
		double[] kernel = new double[tapsPerSide * 2];
		double sum = 0;
		for (int i = 0; i < kernel.length; i++)
		{
			double distance = Math.abs(tapsPerSide - i - 0.5);
			kernel[i] = Math.exp(-(distance * distance) / (2.0 * standardDeviation * standardDeviation));
			sum += kernel[i];
		}
		for (int i = 0; i < kernel.length; i++)
		{
			kernel[i] /= sum;
		}
		int centerIndex = tapsPerSide - 1;

		double[] source = readNormalizedLevels(image);
		double[] horizontal = new double[width * height];
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				double total = 0;
				for (int i = 0; i < kernel.length; i++)
				{
					int sourceX = x - centerIndex + i;
					if (sourceX >= 0 && sourceX < width)
					{
						total += kernel[i] * source[y * width + sourceX];
					}
				}
				horizontal[y * width + x] = total;
			}
		}

		double[] result = new double[width * height];
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				double total = 0;
				for (int i = 0; i < kernel.length; i++)
				{
					int sourceY = y - centerIndex + i;
					if (sourceY >= 0 && sourceY < height)
					{
						total += kernel[i] * horizontal[sourceY * width + x];
					}
				}
				result[y * width + x] = total;
			}
		}
		return result;
	}

	private Image createGrungeLikeBox(int boxWidth)
	{
		Image box = Image.create(boxWidth, boxWidth, ImageType.Binary);
		try (Painter p = box.createPainter())
		{
			p.setColor(Color.white);
			p.fillRect(0, 0, boxWidth, boxWidth);
			p.setColor(Color.black);
			p.fillRect(1, 1, boxWidth, boxWidth);
		}
		return box;
	}

	/**
	 * The grunge border blurs a box whose kernel is nearly as wide as the box itself, which is the largest kernel the app uses.
	 */
	@Test
	public void compareHugeKernelBlur()
	{
		System.out.println("\n=== Huge kernel (the grunge border's blurred box) ===");
		int[] blurLevels = { 70, 140, 281 };
		for (int blurLevel : blurLevels)
		{
			int boxWidth = blurLevel * 2 + 2;
			try (Image box = Image.create(boxWidth, boxWidth, ImageType.Binary))
			{
				try (Painter p = box.createPainter())
				{
					p.setColor(Color.white);
					p.fillRect(0, 0, boxWidth, boxWidth);
					p.setColor(Color.black);
					p.fillRect(1, 1, boxWidth, boxWidth);
				}

				long fftTime = time(3, () -> closeQuietly(ImageHelper.getInstance().convolveGrayscale(box, ImageHelper.getInstance().createGaussianKernel(blurLevel), true, true)));
				long separableTime = time(5, () -> closeQuietly(GaussianBlur.blur(box, blurLevel, true, true, GaussianBlur.Algorithm.separableGaussian)));
				long boxTime = time(5, () -> closeQuietly(GaussianBlur.blur(box, blurLevel, true, true, GaussianBlur.Algorithm.threeBoxes)));

				try (Image fftResult = ImageHelper.getInstance().convolveGrayscale(box, ImageHelper.getInstance().createGaussianKernel(blurLevel), true, true);
						Image separableResult = GaussianBlur.blur(box, blurLevel, true, true, GaussianBlur.Algorithm.separableGaussian);
						Image boxResult = GaussianBlur.blur(box, blurLevel, true, true, GaussianBlur.Algorithm.threeBoxes))
				{
					System.out.println("\n--- " + boxWidth + "x" + boxWidth + " image, kernel " + (blurLevel * 2) + "x" + (blurLevel * 2) + " ---");
					System.out.println("  FFT:        " + formatTime(fftTime));
					System.out.println("  separable:  " + formatTime(separableTime) + "   " + String.format("%.1fx faster", fftTime / (double) separableTime) + "   "
							+ describeDifference(fftResult, separableResult));
					System.out.println("  three box:  " + formatTime(boxTime) + "   " + String.format("%.1fx faster", fftTime / (double) boxTime) + "   "
							+ describeDifference(fftResult, boxResult));
				}

				// Repeat the comparison without maximizing contrast, to separate the approximation's own error from the
				// amplification that stretching a very dim blurred image to the full range applies to it.
				try (Image fftPlain = ImageHelper.getInstance().convolveGrayscaleThenScale(box, ImageHelper.getInstance().createGaussianKernel(blurLevel), 1f, true);
						Image separablePlain = GaussianBlur.blurAndScale(box, blurLevel, 1f, true, GaussianBlur.Algorithm.separableGaussian);
						Image boxPlain = GaussianBlur.blurAndScale(box, blurLevel, 1f, true, GaussianBlur.Algorithm.threeBoxes))
				{
					System.out.println("  no contrast stretch, separable:  " + describeDifference(fftPlain, separablePlain));
					System.out.println("  no contrast stretch, three box:  " + describeDifference(fftPlain, boxPlain));
				}
			}
		}
	}

	@Test
	public void measureSincKernelConvolutionForReference()
	{
		System.out.println("\n=== Ripple waves (non-separable sinc kernel, FFT only) ===");
		int[] imageSizes = { 512, 1024 };
		int[] wavesLevels = { 15, 40 };
		for (int imageSize : imageSizes)
		{
			try (Image mask = createCoastlineLikeMask(imageSize, imageSize, 7))
			{
				for (int wavesLevel : wavesLevels)
				{
					float[][] kernel = ImageHelper.getInstance().createPositiveSincKernel(wavesLevel, 1.0);
					long fftTime = time(3, () -> closeQuietly(ImageHelper.getInstance().convolveGrayscaleThenScale(mask, kernel, 0.35f, true)));
					System.out.println("  " + imageSize + "x" + imageSize + ", sinc kernel " + wavesLevel + "x" + wavesLevel + ":  " + formatTime(fftTime));
				}
			}
		}
	}

	private Image fftBlurAndScale(Image mask, int blurLevel, float scale)
	{
		float[][] kernel = ImageHelper.getInstance().createGaussianKernel(blurLevel);
		return ImageHelper.getInstance().convolveGrayscaleThenScale(mask, kernel, scale, true);
	}

	private String describeDifference(Image expected, Image actual)
	{
		long differingPixels = 0;
		int maxDifference = 0;
		double totalDifference = 0;
		int width = expected.getWidth();
		int height = expected.getHeight();
		try (PixelReader expectedPixels = expected.createPixelReader(); PixelReader actualPixels = actual.createPixelReader())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int difference = Math.abs(expectedPixels.getGrayLevel(x, y) - actualPixels.getGrayLevel(x, y));
					if (difference > 0)
					{
						differingPixels++;
						totalDifference += difference;
						maxDifference = Math.max(maxDifference, difference);
					}
				}
			}
		}
		double pixelCount = (double) width * height;
		return String.format("max diff %d levels, mean diff %.3f, %.1f%% of pixels differ", maxDifference, totalDifference / pixelCount,
				100.0 * differingPixels / pixelCount);
	}

	/**
	 * Draws thin wandering white lines on black, which is the shape of the coastline and region-boundary masks that get blurred during a
	 * draw.
	 */
	private Image createCoastlineLikeMask(int width, int height, long seed)
	{
		Image mask = Image.create(width, height, ImageType.Binary);
		Random rand = new Random(seed);
		try (Painter p = mask.createPainter(DrawQuality.High))
		{
			p.setColor(Color.white);
			p.setBasicStroke(1f);
			for (int line = 0; line < 12; line++)
			{
				List<IntPoint> points = new ArrayList<>();
				double x = rand.nextInt(width);
				double y = rand.nextInt(height);
				double angle = rand.nextDouble() * Math.PI * 2;
				for (int step = 0; step < 200; step++)
				{
					points.add(new IntPoint((int) x, (int) y));
					angle += (rand.nextDouble() - 0.5) * 0.7;
					x += Math.cos(angle) * (width / 60.0);
					y += Math.sin(angle) * (height / 60.0);
				}
				p.drawPolyline(points);
			}
		}
		return mask;
	}

	private long time(int iterations, Runnable action)
	{
		for (int i = 0; i < Math.max(2, iterations / 2); i++)
		{
			action.run();
		}
		long best = Long.MAX_VALUE;
		for (int i = 0; i < iterations; i++)
		{
			long start = System.nanoTime();
			action.run();
			best = Math.min(best, System.nanoTime() - start);
		}
		return best;
	}

	private static void closeQuietly(Image image)
	{
		if (image != null)
		{
			image.close();
		}
	}

	private String formatTime(long nanos)
	{
		if (nanos < 1_000_000)
		{
			return String.format("%8.2f us", nanos / 1000.0);
		}
		return String.format("%8.2f ms", nanos / 1_000_000.0);
	}
}
