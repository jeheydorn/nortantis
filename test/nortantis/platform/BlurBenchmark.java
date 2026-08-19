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
	 * Reports the standard deviation each method actually produces at the small blur levels that text background haze uses, by measuring the
	 * second moment of its impulse response. The box filters can only compose whole-pixel widths, and the recursive filter's coefficients come
	 * from a fit that stops holding once the blur is narrower than about one pixel, so both drift from the requested standard deviation here in
	 * ways they do not at the levels shading and grunge use.
	 */
	@Test
	public void measureSmallBlurLevelStandardDeviations()
	{
		System.out.println("\n=== Standard deviation produced at small blur levels (requested is blurLevel / 6) ===");
		System.out.println("Two ways of reading the width off the impulse response: its second moment, which the tails dominate, and its peak,");
		System.out.println("which only the core reaches. A method that agrees with one and not the other has the right core and the wrong tails.");
		System.out.println("  level  requested       separable            3 boxes           recursive");

		for (int blurLevel = 2; blurLevel <= 60; blurLevel += blurLevel < 20 ? 1 : 5)
		{
			double requested = blurLevel / 6.0;
			int size = Math.max(64, blurLevel * 6);
			StringBuilder moments = new StringBuilder(String.format("  %5d  %9.3f", blurLevel, requested));
			StringBuilder peaks = new StringBuilder(String.format("  %5s  %9s", "", "from peak"));
			for (GaussianBlur.Algorithm algorithm : new GaussianBlur.Algorithm[] { GaussianBlur.Algorithm.separableGaussian,
					GaussianBlur.Algorithm.threeBoxes, GaussianBlur.Algorithm.recursiveGaussian })
			{
				double[] marginal = measureImpulseMarginal(blurLevel, algorithm, size);
				double fromMoment = calcStandardDeviation(marginal);
				moments.append(String.format("   %7.3f (%+5.1f%%)", fromMoment, 100.0 * (fromMoment - requested) / requested));

				double peak = 0;
				double total = 0;
				for (double value : marginal)
				{
					peak = Math.max(peak, value);
					total += value;
				}
				// A Gaussian normalized to sum to one peaks at 1 / (standardDeviation * sqrt(2 * pi)), so the peak names a width of its own.
				double fromPeak = total / (peak * Math.sqrt(2 * Math.PI));
				peaks.append(String.format("   %7.3f (%+5.1f%%)", fromPeak, 100.0 * (fromPeak - requested) / requested));
			}
			System.out.println(moments);
			System.out.println(peaks);
		}
	}

	/**
	 * Runs the same pair of blurs, with the threshold between them, that {@code TextDrawer.drawBackgroundBlendingForText} runs, and reports how
	 * far each method's finished haze lands from the one FFT convolution produces.
	 *
	 * The chain amplifies whatever the first blur gets wrong. Both blurs stretch their output to the full range of gray levels, and the
	 * threshold between them turns every level above zero white, so the second blur's input is the whole area the first blur's tail reached at
	 * all rather than the area it covered strongly. Methods whose tails end sooner or later than a Gaussian's differ there by much more than
	 * their shape error alone would suggest.
	 */
	@Test
	public void measureTextHazeChain()
	{
		System.out.println("\n=== Text background haze: blur, threshold, blur again, compared against FFT ===");
		System.out.println("Font heights span small labels at low display quality up to region names at high display quality.");

		for (int fontHeight : new int[] { 12, 20, 30, 50, 80, 130, 220 })
		{
			int kernelSize = (int) ((13.0 / 54.0) * fontHeight);
			if (kernelSize == 0)
			{
				continue;
			}

			try (Image textBG = createTextLikeMask(fontHeight, kernelSize))
			{
				System.out.println("\n  font height " + fontHeight + " -> blurLevel " + kernelSize + " (sigma " + String.format("%.2f", kernelSize / 6.0)
						+ "), mask " + textBG.getWidth() + "x" + textBG.getHeight());

				try (Image fftHaze = fftHazeChain(textBG, kernelSize))
				{
					long fftTime = time(5, () -> closeQuietly(fftHazeChain(textBG, kernelSize)));
					System.out.println("    FFT:         " + formatTime(fftTime));
					for (GaussianBlur.Algorithm algorithm : new GaussianBlur.Algorithm[] { GaussianBlur.Algorithm.separableGaussian,
							GaussianBlur.Algorithm.threeBoxes, GaussianBlur.Algorithm.recursiveGaussian, null })
					{
						long algorithmTime = time(5, () -> closeQuietly(runHazeChain(textBG, kernelSize, algorithm)));
						try (Image haze = runHazeChain(textBG, kernelSize, algorithm))
						{
							System.out.println(String.format("    %-12s", shortName(algorithm) + ":") + formatTime(algorithmTime) + "   "
									+ describeWhiteFraction(textBG, kernelSize, algorithm) + "   " + describeDifference(fftHaze, haze));
						}
					}
				}
			}
		}
	}

	/**
	 * Finds where convolving with the exact kernel stops being cheap. Its cost grows with the blur level while the box filters' does not, so
	 * below some level the exact kernel is both the most accurate way to blur and an affordable one.
	 */
	@Test
	public void measureExactKernelCrossover()
	{
		System.out.println("\n=== Cost of the exact kernel against the box filters, by blur level ===");
		for (int imageSize : new int[] { 512, 1024 })
		{
			try (Image mask = createCoastlineLikeMask(imageSize, imageSize, 42))
			{
				// Warm up both paths before either is timed, so that neither pays for the other's compilation.
				for (int i = 0; i < 3; i++)
				{
					closeQuietly(GaussianBlur.blurAndScale(mask, 20, 1f, true, GaussianBlur.Algorithm.separableGaussian));
					closeQuietly(GaussianBlur.blurAndScale(mask, 20, 1f, true, GaussianBlur.Algorithm.threeBoxes));
				}

				System.out.println("\n  " + imageSize + "x" + imageSize);
				System.out.println("   level    separable    three box    ratio");
				for (int blurLevel : new int[] { 2, 4, 8, 12, 16, 20, 30, 40, 60, 80, 120 })
				{
					long separableTime = time(15, () -> closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, 1f, true, GaussianBlur.Algorithm.separableGaussian)));
					long boxTime = time(15, () -> closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, 1f, true, GaussianBlur.Algorithm.threeBoxes)));
					System.out.println(String.format("   %5d %12s %12s   %5.2fx", blurLevel, formatTime(separableTime), formatTime(boxTime),
							separableTime / (double) boxTime));
				}
			}
		}
	}

	private static String shortName(GaussianBlur.Algorithm algorithm)
	{
		if (algorithm == null)
		{
			return "as shipped";
		}
		return algorithm == GaussianBlur.Algorithm.threeBoxes ? "three box" : algorithm == GaussianBlur.Algorithm.recursiveGaussian ? "recursive" : "separable";
	}

	/**
	 * Blurs with the given algorithm, or, when it is null, however {@link ImageHelper} is configured to blur one of this level, which is what
	 * text haze actually gets.
	 */
	private Image hazeBlur(Image image, int blurLevel, GaussianBlur.Algorithm algorithm)
	{
		if (algorithm == null)
		{
			return ImageHelper.getInstance().blur(image, blurLevel, true, true);
		}
		return GaussianBlur.blur(image, blurLevel, true, true, algorithm);
	}

	private Image fftHazeChain(Image textBG, int blurLevel)
	{
		try (Image haze1 = ImageHelper.getInstance().convolveGrayscale(textBG, ImageHelper.getInstance().createGaussianKernel(blurLevel), true, true))
		{
			ImageHelper.getInstance().threshold(haze1, 1);
			return ImageHelper.getInstance().convolveGrayscale(haze1, ImageHelper.getInstance().createGaussianKernel(blurLevel), true, true);
		}
	}

	private Image runHazeChain(Image textBG, int blurLevel, GaussianBlur.Algorithm algorithm)
	{
		try (Image haze1 = hazeBlur(textBG, blurLevel, algorithm))
		{
			ImageHelper.getInstance().threshold(haze1, 1);
			return hazeBlur(haze1, blurLevel, algorithm);
		}
	}

	/**
	 * The fraction of the mask the threshold in the middle of the haze chain turns white, which is how far the first blur's tail reached.
	 */
	private String describeWhiteFraction(Image textBG, int blurLevel, GaussianBlur.Algorithm algorithm)
	{
		try (Image haze1 = hazeBlur(textBG, blurLevel, algorithm))
		{
			ImageHelper.getInstance().threshold(haze1, 1);
			long white = 0;
			try (PixelReader pixels = haze1.createPixelReader())
			{
				for (int y = 0; y < haze1.getHeight(); y++)
				{
					for (int x = 0; x < haze1.getWidth(); x++)
					{
						if (pixels.getGrayLevel(x, y) > 0)
						{
							white++;
						}
					}
				}
			}
			return String.format("%5.1f%% white after threshold", 100.0 * white / ((double) haze1.getWidth() * haze1.getHeight()));
		}
	}

	/**
	 * Draws a place name into an image padded the way {@code TextDrawer} pads its own, which leaves the blur only as much room around the text
	 * as its own blur level.
	 */
	private Image createTextLikeMask(int fontHeight, int padding)
	{
		int width = (int) (fontHeight * 4.5) + padding * 2;
		int height = (int) (fontHeight * 1.3) + padding * 2;
		Image mask = Image.create(width, height, ImageType.Grayscale8Bit);
		try (Painter p = mask.createPainter(DrawQuality.High))
		{
			p.setFont(Font.create("Serif", FontStyle.Plain, fontHeight));
			p.setColor(Color.white);
			p.drawString("Riverwood", padding, padding + p.getFontAscent());
		}
		return mask;
	}

	/**
	 * Sums an impulse response down its columns, which for a blur done as a horizontal pass and then a vertical one leaves the horizontal pass's
	 * one-dimensional kernel by itself.
	 */
	private double[] measureImpulseMarginal(int blurLevel, GaussianBlur.Algorithm algorithm, int size)
	{
		try (Image impulse = Image.create(size, size, ImageType.Grayscale16Bit))
		{
			try (PixelWriter pixels = impulse.createPixelWriter())
			{
				pixels.setGrayLevel(size / 2, size / 2, impulse.getMaxPixelLevel());
			}

			float[] levels = GaussianBlur.blurToLevels(impulse, blurLevel, true, algorithm);
			double[] marginal = new double[size];
			for (int y = 0; y < size; y++)
			{
				for (int x = 0; x < size; x++)
				{
					marginal[x] += levels[y * size + x];
				}
			}
			return marginal;
		}
	}

	private double calcStandardDeviation(double[] marginal)
	{
		double total = 0;
		double weightedSum = 0;
		for (int x = 0; x < marginal.length; x++)
		{
			total += marginal[x];
			weightedSum += x * marginal[x];
		}
		double mean = weightedSum / total;
		double variance = 0;
		for (int x = 0; x < marginal.length; x++)
		{
			variance += marginal[x] * (x - mean) * (x - mean);
		}
		return Math.sqrt(variance / total);
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
