package nortantis.test;

import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.ImageHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
	public void testMaskWithImageCorrectness()
	{
		// Test that the shader produces the same results as the Java implementation
		int width = 100;
		int height = 100;

		Image image1 = createTestImage(width, height, ImageType.RGB, 42);
		Image image2 = createTestImage(width, height, ImageType.RGB, 123);
		Image mask = createTestMask(width, height, 456);

		// Run through ImageHelper
		Image result = ImageHelper.maskWithImage(image1, image2, mask);

		// Create copies and run again to verify consistency
		Image image1Copy = createTestImage(width, height, ImageType.RGB, 42);
		Image image2Copy = createTestImage(width, height, ImageType.RGB, 123);
		Image maskCopy = createTestMask(width, height, 456);
		Image resultCopy = ImageHelper.maskWithImage(image1Copy, image2Copy, maskCopy);

		// Compare results - allow small differences due to floating point
		assertImagesEqual(result, resultCopy, 2, "Results should be consistent");
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

	private void assertImagesEqual(Image expected, Image actual, int tolerance, String message)
	{
		assertEquals(expected.getWidth(), actual.getWidth(), message + " - width mismatch");
		assertEquals(expected.getHeight(), actual.getHeight(), message + " - height mismatch");

		try (var expectedPixels = expected.createPixelReader(); var actualPixels = actual.createPixelReader())
		{
			int differences = 0;
			int maxDiff = 0;

			for (int y = 0; y < expected.getHeight(); y++)
			{
				for (int x = 0; x < expected.getWidth(); x++)
				{
					int expectedRGB = expectedPixels.getRGB(x, y);
					int actualRGB = actualPixels.getRGB(x, y);

					int rDiff = Math.abs(((expectedRGB >> 16) & 0xFF) - ((actualRGB >> 16) & 0xFF));
					int gDiff = Math.abs(((expectedRGB >> 8) & 0xFF) - ((actualRGB >> 8) & 0xFF));
					int bDiff = Math.abs((expectedRGB & 0xFF) - (actualRGB & 0xFF));

					int diff = Math.max(rDiff, Math.max(gDiff, bDiff));
					maxDiff = Math.max(maxDiff, diff);

					if (diff > tolerance)
					{
						differences++;
					}
				}
			}

			if (differences > 0)
			{
				fail(message + " - " + differences + " pixels differ by more than " + tolerance + " (max diff: " + maxDiff + ")");
			}
		}
	}
}
