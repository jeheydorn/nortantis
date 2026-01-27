package nortantis.test;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;
import nortantis.platform.skia.GPUExecutor;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.ImageHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark for ImageHelper operations.
 */
public class ImageHelperBenchmark
{
	@BeforeAll
	public static void setup()
	{
		// Ensure we're using Skia
		PlatformFactory.setInstance(new SkiaFactory());
	}

	@Test
	public void benchmarkMaskWithImage()
	{
		System.out.println("\n=== maskWithImage Benchmark ===\n");

		int size = 4096;
		System.out.println("Image size: " + size + "x" + size);

		Image image1 = createTestImage(size, size, ImageType.RGB, 42);
		Image image2 = createTestImage(size, size, ImageType.RGB, 123);
		Image mask = createTestMask(size, size, 456);

		// Warmup
		for (int i = 0; i < 3; i++)
		{
			ImageHelper.maskWithImage(image1, image2, mask);
		}

		// Run twice and average
		int iterations = 10;
		long totalTime = 0;
		for (int run = 0; run < 2; run++)
		{
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++)
			{
				ImageHelper.maskWithImage(image1, image2, mask);
			}
			totalTime += (System.nanoTime() - start) / iterations;
		}
		long avgTime = totalTime / 2;

		System.out.println("  ImageHelper.maskWithImage:  " + formatTime(avgTime));
	}

	@Test
	public void benchmarkColorify()
	{
		System.out.println("\n=== colorify Benchmark (with HSB conversion) ===\n");

		int size = 4096;
		System.out.println("Image size: " + size + "x" + size);

		Image grayscale = createTestMask(size, size, 42);
		nortantis.platform.Color color = nortantis.platform.Color.create(180, 100, 50); // Test color

		// Warmup
		for (int i = 0; i < 3; i++)
		{
			ImageHelper.colorify(grayscale, color, ImageHelper.ColorifyAlgorithm.algorithm3, false);
		}

		// Run twice and average
		int iterations = 10;
		long totalTime = 0;
		for (int run = 0; run < 2; run++)
		{
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++)
			{
				ImageHelper.colorify(grayscale, color, ImageHelper.ColorifyAlgorithm.algorithm3, false);
			}
			totalTime += (System.nanoTime() - start) / iterations;
		}
		long avgTime = totalTime / 2;

		System.out.println("  ImageHelper.colorify (algorithm3):  " + formatTime(avgTime));
	}

	@Test
	public void benchmarkMaskWithColor()
	{
		System.out.println("\n=== maskWithColor Benchmark ===\n");

		int size = 4096;
		System.out.println("Image size: " + size + "x" + size);

		Image image = createTestImage(size, size, ImageType.RGB, 42);
		Image mask = createTestMask(size, size, 456);
		nortantis.platform.Color color = nortantis.platform.Color.create(255, 0, 0); // Red

		// Warmup
		for (int i = 0; i < 3; i++)
		{
			ImageHelper.maskWithColor(image, color, mask, false);
		}

		// Run twice and average
		int iterations = 10;
		long totalTime = 0;
		for (int run = 0; run < 2; run++)
		{
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++)
			{
				ImageHelper.maskWithColor(image, color, mask, false);
			}
			totalTime += (System.nanoTime() - start) / iterations;
		}
		long avgTime = totalTime / 2;

		System.out.println("  ImageHelper.maskWithColor:  " + formatTime(avgTime));
	}

	@Test
	public void benchmarkSetAlphaFromMask()
	{
		System.out.println("\n=== setAlphaFromMask Benchmark ===\n");

		int size = 4096;
		System.out.println("Image size: " + size + "x" + size);

		Image image = createTestImage(size, size, ImageType.ARGB, 42);
		Image mask = createTestMask(size, size, 456);

		// Warmup
		for (int i = 0; i < 3; i++)
		{
			ImageHelper.setAlphaFromMask(image, mask, false);
		}

		// Run twice and average
		int iterations = 10;
		long totalTime = 0;
		for (int run = 0; run < 2; run++)
		{
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++)
			{
				ImageHelper.setAlphaFromMask(image, mask, false);
			}
			totalTime += (System.nanoTime() - start) / iterations;
		}
		long avgTime = totalTime / 2;

		System.out.println("  ImageHelper.setAlphaFromMask:  " + formatTime(avgTime));
	}

	@Test
	public void benchmarkCopySubImage()
	{
		System.out.println("\n=== copySubImage Benchmark ===\n");

		int sourceSize = 4000;
		int numCopies = 200;
		int minSize = 10;
		int maxSize = 50;

		System.out.println("Source image size: " + sourceSize + "x" + sourceSize);
		System.out.println("Number of copies: " + numCopies);
		System.out.println("Copy sizes: " + minSize + "x" + minSize + " to " + maxSize + "x" + maxSize);

		Image sourceImage = createTestImage(sourceSize, sourceSize, ImageType.RGB, 42);
		Random rand = new Random(123);

		// Pre-generate random bounds for consistent benchmarking
		IntRectangle[] bounds = new IntRectangle[numCopies];
		for (int i = 0; i < numCopies; i++)
		{
			int size = minSize + rand.nextInt(maxSize - minSize + 1);
			int x = rand.nextInt(sourceSize - size);
			int y = rand.nextInt(sourceSize - size);
			bounds[i] = new IntRectangle(x, y, size, size);
		}

		// Warmup
		for (int i = 0; i < 10; i++)
		{
			try (Image copy = sourceImage.copySubImage(bounds[i % numCopies]))
			{
				// Just create and close
			}
		}

		// Run twice and average
		long totalTime = 0;
		for (int run = 0; run < 2; run++)
		{
			long start = System.nanoTime();
			for (int i = 0; i < numCopies; i++)
			{
				try (Image copy = sourceImage.copySubImage(bounds[i]))
				{
					// Just create and close
				}
			}
			totalTime += System.nanoTime() - start;
		}
		long avgTime = totalTime / 2;

		System.out.println("  Total time for " + numCopies + " copies:  " + formatTime(avgTime));
		System.out.println("  Average per copy:  " + formatTime(avgTime / numCopies));
	}

	private String formatTime(long nanos)
	{
		if (nanos < 1_000_000)
		{
			return String.format("%.2f µs", nanos / 1000.0);
		}
		else if (nanos < 1_000_000_000)
		{
			return String.format("%.2f ms", nanos / 1_000_000.0);
		}
		else
		{
			return String.format("%.2f s", nanos / 1_000_000_000.0);
		}
	}

	private Image createTestImage(int width, int height, ImageType type, long seed)
	{
		Image image = Image.create(width, height, type);
		Random rand = new Random(seed);

		try (var pixels = image.createPixelReaderWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int r = rand.nextInt(256);
					int g = rand.nextInt(256);
					int b = rand.nextInt(256);
					pixels.setRGB(x, y, r, g, b);
				}
			}
		}

		return image;
	}

	private Image createTestMask(int width, int height, long seed)
	{
		Image mask = Image.create(width, height, ImageType.Grayscale8Bit);
		Random rand = new Random(seed);

		try (var pixels = mask.createPixelReaderWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					pixels.setGrayLevel(x, y, rand.nextInt(256));
				}
			}
		}

		return mask;
	}

	@Test
	public void benchmarkColorifyMulti()
	{
		System.out.println("\n=== colorifyMulti Benchmark (Dynamic Palette Sizing) ===\n");

		GPUExecutor.setRenderingMode(GPUExecutor.RenderingMode.GPU);
		int size = 2048;
		System.out.println("Image size: " + size + "x" + size);

		// Test with different numbers of regions to show dynamic palette optimization
		int[] regionCounts = { 5, 20, 100, 256, 500 };

		for (int numRegions : regionCounts)
		{
			System.out.println("\nTesting with " + numRegions + " regions:");

			// Create grayscale source image
			Image grayscale = createTestMask(size, size, 42);

			// Create color index image with region IDs
			Image colorIndexes = createColorIndexImage(size, size, numRegions, 123);

			// Create color map for regions
			Map<Integer, Color> colorMap = createColorMap(numRegions, 456);

			// Warmup
			for (int i = 0; i < 3; i++)
			{
				try (Image result = ImageHelper.colorifyMulti(grayscale, colorMap, colorIndexes, ImageHelper.ColorifyAlgorithm.algorithm3, null))
				{
					// Just create and close
				}
			}

			// Run twice and average
			int iterations = 5;
			long totalTime = 0;
			for (int run = 0; run < 2; run++)
			{
				long start = System.nanoTime();
				for (int i = 0; i < iterations; i++)
				{
					try (Image result = ImageHelper.colorifyMulti(grayscale, colorMap, colorIndexes, ImageHelper.ColorifyAlgorithm.algorithm3, null))
					{
						// Just create and close
					}
				}
				totalTime += (System.nanoTime() - start) / iterations;
			}
			long avgTime = totalTime / 2;

			System.out.println("  colorifyMulti (algorithm3):  " + formatTime(avgTime));

			grayscale.close();
			colorIndexes.close();
		}
	}

	@Test
	public void benchmarkMaskWithMultipleColors()
	{
		System.out.println("\n=== maskWithMultipleColors Benchmark (Dynamic Palette Sizing) ===\n");

		int size = 2048;
		System.out.println("Image size: " + size + "x" + size);

		// Test with different numbers of regions to show dynamic palette optimization
		int[] regionCounts = { 5, 20, 100, 256, 500 };

		for (int numRegions : regionCounts)
		{
			System.out.println("\nTesting with " + numRegions + " regions:");

			// Create source image
			Image image = createTestImage(size, size, ImageType.RGB, 42);

			// Create color index image with region IDs
			Image colorIndexes = createColorIndexImage(size, size, numRegions, 123);

			// Create mask
			Image mask = createTestMask(size, size, 789);

			// Create color map for regions
			Map<Integer, Color> colorMap = createColorMap(numRegions, 456);

			// Warmup
			for (int i = 0; i < 3; i++)
			{
				try (Image result = ImageHelper.maskWithMultipleColors(image, colorMap, colorIndexes, mask, false))
				{
					// Just create and close
				}
			}

			// Run twice and average
			int iterations = 5;
			long totalTime = 0;
			for (int run = 0; run < 2; run++)
			{
				long start = System.nanoTime();
				for (int i = 0; i < iterations; i++)
				{
					try (Image result = ImageHelper.maskWithMultipleColors(image, colorMap, colorIndexes, mask, false))
					{
						// Just create and close
					}
				}
				totalTime += (System.nanoTime() - start) / iterations;
			}
			long avgTime = totalTime / 2;

			System.out.println("  maskWithMultipleColors:  " + formatTime(avgTime));

			image.close();
			colorIndexes.close();
			mask.close();
		}
	}

	/**
	 * Creates a color index image where each pixel's RGB encodes a region ID. Region IDs are distributed randomly across the image.
	 */
	private Image createColorIndexImage(int width, int height, int numRegions, long seed)
	{
		Image image = Image.create(width, height, ImageType.RGB);
		Random rand = new Random(seed);

		try (var pixels = image.createPixelReaderWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int regionId = rand.nextInt(numRegions);
					// Encode region ID as RGB (r << 16 | g << 8 | b)
					int r = (regionId >> 16) & 0xFF;
					int g = (regionId >> 8) & 0xFF;
					int b = regionId & 0xFF;
					pixels.setRGB(x, y, r, g, b);
				}
			}
		}

		return image;
	}

	/**
	 * Creates a map of region IDs to random colors.
	 */
	private Map<Integer, Color> createColorMap(int numRegions, long seed)
	{
		Map<Integer, Color> colorMap = new HashMap<>();
		Random rand = new Random(seed);

		for (int i = 0; i < numRegions; i++)
		{
			colorMap.put(i, Color.create(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256)));
		}

		return colorMap;
	}
}
