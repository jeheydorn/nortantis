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
 * Writes images comparing how each blur method looks, so the differences can be judged by eye rather than only by numbers.
 *
 * Run with: ./gradlew test --tests "nortantis.platform.BlurComparisonImages" -DrunBenchmarks=true -DblurImageOutputDirectory=some/path
 */
@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")
public class BlurComparisonImages
{
	/**
	 * How much the difference images multiply the difference by, so that errors far below one gray level become visible.
	 */
	private static final int differenceAmplification = 16;

	private static final int labelHeight = 22;
	private static final int gap = 8;

	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new AwtFactory());
	}

	/**
	 * Writes every comparison, so the images can be produced without going through the test runner.
	 */
	public static void main(String[] args)
	{
		if (args.length > 0)
		{
			System.setProperty("blurImageOutputDirectory", args[0]);
		}
		setup();
		BlurComparisonImages images = new BlurComparisonImages();
		images.writeCoastShadingComparison();
		images.writeCoastShadingComparisonAtMaximumSetting();
		images.writeGrungeComparison();
	}

	private String outputDirectory()
	{
		return System.getProperty("blurImageOutputDirectory", "build");
	}

	/**
	 * The coast shading path, at the default settings: blur level 80, and the scale MapCreator applies, which is about tenfold.
	 */
	@Test
	public void writeCoastShadingComparison()
	{
		int blurLevel = 80;
		float scale = coastShadingScale(30, 8.0 / 3.0);
		try (Image mask = createCoastlineLikeMask(512, 512, 42))
		{
			writeComparison(mask, blurLevel, scale, false, "coastShading_blurLevel80_scale" + Math.round(scale),
					"Coast shading, blur level 80, scale " + String.format("%.1f", scale));
		}
	}

	/**
	 * The same path at the maximum coast shading setting, where the scale reaches about thirtyfold.
	 */
	@Test
	public void writeCoastShadingComparisonAtMaximumSetting()
	{
		int blurLevel = 266;
		float scale = coastShadingScale(100, 8.0 / 3.0);
		try (Image mask = createCoastlineLikeMask(512, 512, 42))
		{
			writeComparison(mask, blurLevel, scale, false, "coastShading_blurLevel266_scale" + Math.round(scale),
					"Coast shading at maximum setting, blur level 266, scale " + String.format("%.1f", scale));
		}
	}

	/**
	 * The grunge border's blurred box, which stretches contrast afterwards and so magnifies any error enormously.
	 */
	@Test
	public void writeGrungeComparison()
	{
		int blurLevel = 140;
		try (Image box = createGrungeLikeBox(blurLevel * 2 + 2))
		{
			writeComparison(box, blurLevel, 1f, true, "grunge_blurLevel140_contrastMaximized",
					"Grunge border, blur level 140, contrast maximized afterwards");
		}
	}

	private static float coastShadingScale(int shadingSliderValue, double sizeMultiplier)
	{
		final float coastlineShadingScale = 5.27f;
		return coastlineShadingScale * ImageHelper.getInstance().getGaussianMode((int) (15 * sizeMultiplier))
				/ ImageHelper.getInstance().getGaussianMode((int) (shadingSliderValue * sizeMultiplier));
	}

	/**
	 * Renders one row per method: the blurred result, then the difference from an exact Gaussian, multiplied so it can be seen.
	 */
	private void writeComparison(Image mask, int blurLevel, float scale, boolean maximizeContrast, String fileName, String title)
	{
		List<String> names = new ArrayList<>();
		List<Image> results = new ArrayList<>();

		names.add("Exact Gaussian (reference)");
		results.add(exactlyBlurred(mask, blurLevel, scale, maximizeContrast));

		names.add("FFT (what the app does now)");
		results.add(fftBlurred(mask, blurLevel, scale, maximizeContrast));

		names.add("Separable Gaussian");
		results.add(spatiallyBlurred(mask, blurLevel, scale, maximizeContrast, GaussianBlur.Algorithm.separableGaussian));

		names.add("Recursive (Young-van Vliet)");
		results.add(spatiallyBlurred(mask, blurLevel, scale, maximizeContrast, GaussianBlur.Algorithm.recursiveGaussian));

		names.add("Three box filters");
		results.add(spatiallyBlurred(mask, blurLevel, scale, maximizeContrast, GaussianBlur.Algorithm.threeBoxes));

		try
		{
			Image reference = results.get(0);
			int cellWidth = reference.getWidth();
			int cellHeight = reference.getHeight();
			int totalWidth = gap + (cellWidth + gap) * 2;
			int totalHeight = labelHeight + gap + (cellHeight + labelHeight + gap) * results.size();

			Image sheet = Image.create(totalWidth, totalHeight, ImageType.RGB);
			try (Painter p = sheet.createPainter(DrawQuality.High))
			{
				p.setColor(Color.create(32, 32, 36));
				p.fillRect(0, 0, totalWidth, totalHeight);
				p.setColor(Color.white);
				p.drawString(title + "   (right column: difference from exact, multiplied by " + differenceAmplification + ")", gap, labelHeight - 6);

				int y = labelHeight + gap;
				for (int i = 0; i < results.size(); i++)
				{
					p.setColor(Color.white);
					p.drawString(names.get(i), gap, y + labelHeight - 6);
					p.drawImage(results.get(i), gap, y + labelHeight);

					if (i > 0)
					{
						try (Image difference = amplifiedDifference(reference, results.get(i)))
						{
							p.drawImage(difference, gap + cellWidth + gap, y + labelHeight);
						}
						p.setColor(Color.white);
						p.drawString("max " + maxDifference(reference, results.get(i)) + " levels", gap + cellWidth + gap, y + labelHeight - 6);
					}
					y += cellHeight + labelHeight + gap;
				}
			}

			String path = outputDirectory() + "/" + fileName + ".png";
			sheet.write(path);
			sheet.close();
			System.out.println("Wrote " + path);
		}
		finally
		{
			for (Image result : results)
			{
				result.close();
			}
		}
	}

	private Image amplifiedDifference(Image expected, Image actual)
	{
		int width = expected.getWidth();
		int height = expected.getHeight();
		Image difference = Image.create(width, height, ImageType.Grayscale8Bit);
		try (PixelReader expectedPixels = expected.createPixelReader(); PixelReader actualPixels = actual.createPixelReader();
				PixelWriter differencePixels = difference.createPixelWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int value = Math.abs(expectedPixels.getGrayLevel(x, y) - actualPixels.getGrayLevel(x, y)) * differenceAmplification;
					differencePixels.setGrayLevel(x, y, Math.min(255, value));
				}
			}
		}
		return difference;
	}

	private int maxDifference(Image expected, Image actual)
	{
		int maxDifference = 0;
		try (PixelReader expectedPixels = expected.createPixelReader(); PixelReader actualPixels = actual.createPixelReader())
		{
			for (int y = 0; y < expected.getHeight(); y++)
			{
				for (int x = 0; x < expected.getWidth(); x++)
				{
					maxDifference = Math.max(maxDifference, Math.abs(expectedPixels.getGrayLevel(x, y) - actualPixels.getGrayLevel(x, y)));
				}
			}
		}
		return maxDifference;
	}

	private Image spatiallyBlurred(Image mask, int blurLevel, float scale, boolean maximizeContrast, GaussianBlur.Algorithm algorithm)
	{
		if (maximizeContrast)
		{
			return GaussianBlur.blur(mask, blurLevel, true, true, algorithm);
		}
		return GaussianBlur.blurAndScale(mask, blurLevel, scale, true, algorithm);
	}

	private Image fftBlurred(Image mask, int blurLevel, float scale, boolean maximizeContrast)
	{
		float[][] kernel = ImageHelper.getInstance().createGaussianKernel(blurLevel);
		if (maximizeContrast)
		{
			return ImageHelper.getInstance().convolveGrayscale(mask, kernel, true, true);
		}
		return ImageHelper.getInstance().convolveGrayscaleThenScale(mask, kernel, scale, true);
	}

	/**
	 * Blurs with the full untruncated Gaussian in double precision, treating everything outside the image as zero, then applies the same
	 * scaling or contrast stretch the other methods do.
	 */
	private Image exactlyBlurred(Image mask, int blurLevel, float scale, boolean maximizeContrast)
	{
		int width = mask.getWidth();
		int height = mask.getHeight();
		double standardDeviation = blurLevel / 6.0;

		double[] kernel = new double[blurLevel * 2];
		double sum = 0;
		for (int i = 0; i < kernel.length; i++)
		{
			double distance = Math.abs(blurLevel - i - 0.5);
			kernel[i] = Math.exp(-(distance * distance) / (2.0 * standardDeviation * standardDeviation));
			sum += kernel[i];
		}
		for (int i = 0; i < kernel.length; i++)
		{
			kernel[i] /= sum;
		}
		int centerIndex = blurLevel - 1;

		double[] source = new double[width * height];
		double maxPixelValue = mask.getMaxPixelLevel();
		try (PixelReader pixels = mask.createPixelReader())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					source[y * width + x] = pixels.getGrayLevel(x, y) / maxPixelValue;
				}
			}
		}

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

		double[] blurred = new double[width * height];
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
				blurred[y * width + x] = total;
			}
		}

		if (maximizeContrast)
		{
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (double value : blurred)
			{
				min = Math.min(min, value);
				max = Math.max(max, value);
			}
			for (int i = 0; i < blurred.length; i++)
			{
				blurred[i] = (blurred[i] - min) / (max - min);
			}
		}
		else
		{
			for (int i = 0; i < blurred.length; i++)
			{
				blurred[i] = Math.min(1.0, blurred[i] * scale);
			}
		}

		Image result = Image.create(width, height, ImageType.Grayscale8Bit);
		try (PixelWriter pixels = result.createPixelWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					pixels.setGrayLevel(x, y, Math.min(255, (int) (blurred[y * width + x] * 255)));
				}
			}
		}
		return result;
	}

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
}
