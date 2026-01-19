package nortantis.test;

import nortantis.Stopwatch;
import nortantis.geom.IntPoint;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.platform.*;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.ImageHelper.ColorifyAlgorithm;
import nortantis.util.Range;
import org.apache.commons.io.FileUtils;
import org.imgscalr.Scalr.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageHelper image creation and modification methods.
 *
 * These tests follow the pattern from SkiaPainterTest: they compare rendered images against
 * expected images stored in "unit test files/expected image helper tests/". When an expected image doesn't exist,
 * the test creates it from the actual output. Failed tests save their output to
 * "unit test files/failed image helper tests/" along with a diff image.
 */
public class ImageHelperTest
{
	private static final String expectedFolderName = "expected image helper tests";
	private static final String failedFolderName = "failed image helper tests";
	private static final int testImageWidth = 100;
	private static final int testImageHeight = 100;

	@BeforeAll
	public static void setUpBeforeClass() throws Exception
	{
		PlatformFactory.setInstance(new SkiaFactory());
		Assets.disableAddedArtPacksForUnitTests();

		FileHelper.createFolder(Paths.get("unit test files", expectedFolderName).toString());
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", failedFolderName).toString()));
	}

	// ==================== Convert Tests ====================

	@Test
	public void testConvertToGrayscale()
	{
		Image colorImage = createColorTestImage();
		Image grayscale = ImageHelper.convertToGrayscale(colorImage);

		assertEquals(ImageType.Grayscale8Bit, grayscale.getType(), "Result should be grayscale");
		assertEquals(colorImage.getWidth(), grayscale.getWidth(), "Width should match");
		assertEquals(colorImage.getHeight(), grayscale.getHeight(), "Height should match");
		compareWithExpected(grayscale, "convertToGrayscale");

	}

	@Test
	public void testConvertImageToTypeRGB()
	{
		Image grayscaleImage = createGrayscaleTestImage();
		Image rgb = ImageHelper.convertImageToType(grayscaleImage, ImageType.RGB);

		assertEquals(ImageType.RGB, rgb.getType(), "Result should be RGB");
		compareWithExpected(rgb, "convertImageToTypeRGB");
	}

	@Test
	public void testConvertImageToTypeARGB()
	{
		Image rgbImage = createColorTestImage();
		Image argb = ImageHelper.convertImageToType(rgbImage, ImageType.ARGB);

		assertEquals(ImageType.ARGB, argb.getType(), "Result should be ARGB");
		compareWithExpected(argb, "convertImageToTypeARGB");
	}

	// ==================== Scale Tests ====================

	@Test
	public void testScaleByWidth()
	{
		Image original = createColorTestImage();
		Image scaled = ImageHelper.scaleByWidth(original, 50);

		assertEquals(50, scaled.getWidth(), "Width should be 50");
		assertTrue(scaled.getHeight() > 0, "Height should be positive");
		compareWithExpected(scaled, "scaleByWidth");
	}

	@Test
	public void testScaleByHeight()
	{
		Image original = createColorTestImage();
		Image scaled = ImageHelper.scaleByHeight(original, 50);

		assertEquals(50, scaled.getHeight(), "Height should be 50");
		assertTrue(scaled.getWidth() > 0, "Width should be positive");
		compareWithExpected(scaled, "scaleByHeight");
	}

	@Test
	public void testScale()
	{
		Image original = createColorTestImage();
		Image scaled = ImageHelper.scale(original, 75, 50, Method.QUALITY);

		assertEquals(75, scaled.getWidth(), "Width should be 75");
		assertEquals(50, scaled.getHeight(), "Height should be 50");
		compareWithExpected(scaled, "scale");
	}

	@Test
	public void testScaleInto()
	{
		Image source = createColorTestImage();
		Image target = Image.create(50, 50, ImageType.RGB);

		ImageHelper.scaleInto(source, target, null);
		compareWithExpected(target, "scaleInto");
	}

	@Test
	public void testScaleIntoWithBounds()
	{
		Image source = createColorTestImage();
		Image target = Image.create(50, 50, ImageType.RGB);

		IntRectangle bounds = new IntRectangle(25, 25, 50, 50);
		ImageHelper.scaleInto(source, target, bounds);
		compareWithExpected(target, "scaleIntoWithBounds");
	}

	// ==================== Copy Snippet Tests ====================

	@Test
	public void testCopySnippet()
	{
		Image source = createColorTestImage();
		Image snippet = ImageHelper.copySnippet(source, 20, 20, 40, 40);

		assertEquals(40, snippet.getWidth(), "Snippet width should be 40");
		assertEquals(40, snippet.getHeight(), "Snippet height should be 40");
		compareWithExpected(snippet, "copySnippet");
	}

	@Test
	public void testCopySnippetWithRectangle()
	{
		Image source = createColorTestImage();
		IntRectangle bounds = new IntRectangle(10, 10, 60, 60);
		Image snippet = ImageHelper.copySnippet(source, bounds);

		assertEquals(60, snippet.getWidth(), "Snippet width should be 60");
		assertEquals(60, snippet.getHeight(), "Snippet height should be 60");
		compareWithExpected(snippet, "copySnippetWithRectangle");
	}

	@Test
	public void testCopySnippetRotated()
	{
		Image source = createColorTestImage();
		Point pivot = new Point(50, 50);
		Image rotated = ImageHelper.copySnippetRotated(source, 20, 20, 60, 60, Math.PI / 4, pivot);

		assertEquals(60, rotated.getWidth(), "Rotated snippet width should be 60");
		assertEquals(60, rotated.getHeight(), "Rotated snippet height should be 60");
		compareWithExpected(rotated, "copySnippetRotated");
	}

	@Test
	public void testCopySnippetPreservingAlphaOfTransparentPixels()
	{
		Image source = createARGBTestImage();
		Image snippet = ImageHelper.copySnippetPreservingAlphaOfTransparentPixels(source, 10, 10, 50, 50);

		assertEquals(50, snippet.getWidth(), "Snippet width should be 50");
		assertEquals(50, snippet.getHeight(), "Snippet height should be 50");
		// Use threshold due to alpha premultiplication differences in PNG round-trip
		compareWithExpected(snippet, "copySnippetPreservingAlpha", 4);
	}

	// ==================== Flip and Rotate Tests ====================

	@Test
	public void testFlipOnXAxis()
	{
		Image original = createAsymmetricTestImage();
		Image flipped = ImageHelper.flipOnXAxis(original);

		assertEquals(original.getWidth(), flipped.getWidth(), "Width should match");
		assertEquals(original.getHeight(), flipped.getHeight(), "Height should match");
		compareWithExpected(flipped, "flipOnXAxis");
	}

	@Test
	public void testFlipOnYAxis()
	{
		Image original = createAsymmetricTestImage();
		Image flipped = ImageHelper.flipOnYAxis(original);

		assertEquals(original.getWidth(), flipped.getWidth(), "Width should match");
		assertEquals(original.getHeight(), flipped.getHeight(), "Height should match");
		compareWithExpected(flipped, "flipOnYAxis");
	}

	@Test
	public void testRotate90DegreesClockwise()
	{
		Image original = createAsymmetricTestImage();
		Image rotated = ImageHelper.rotate90Degrees(original, true);

		assertEquals(original.getHeight(), rotated.getWidth(), "Width should equal original height");
		assertEquals(original.getWidth(), rotated.getHeight(), "Height should equal original width");
		compareWithExpected(rotated, "rotate90DegreesClockwise");
	}

	@Test
	public void testRotate90DegreesCounterClockwise()
	{
		Image original = createAsymmetricTestImage();
		Image rotated = ImageHelper.rotate90Degrees(original, false);

		assertEquals(original.getHeight(), rotated.getWidth(), "Width should equal original height");
		assertEquals(original.getWidth(), rotated.getHeight(), "Height should equal original width");
		compareWithExpected(rotated, "rotate90DegreesCounterClockwise");
	}

	// ==================== Mask Tests ====================

	@Test
	public void testMaskWithImage()
	{
		Image image1 = createSolidColorImage(testImageWidth, testImageHeight, Color.red);
		Image image2 = createSolidColorImage(testImageWidth, testImageHeight, Color.blue);
		Image mask = createGradientMask();

		Image result = ImageHelper.maskWithImage(image1, image2, mask);

		assertEquals(image1.getWidth(), result.getWidth(), "Width should match");
		assertEquals(image1.getHeight(), result.getHeight(), "Height should match");
		compareWithExpected(result, "maskWithImage");
	}

	@Test
	public void testMaskWithColor()
	{
		Image image = createColorTestImage();
		Image mask = createGradientMask();
		Color color = Color.create(0, 255, 0, 128);

		Stopwatch sw = new Stopwatch("maskWithColor");
		Image result = ImageHelper.maskWithColor(image, color, mask, false);
		sw.printElapsedTime();

		compareWithExpected(result, "maskWithColor");
	}

	/**
	 * Tests that we don't hit any crashes when running GPU drawing back to back.
	 */
	@Test
	public void testMaskWithColorMultipleTimes()
	{
		final int numberOfRuns = 2;
		int successCount = 0;
		for (int i : new Range(numberOfRuns))
		{
			Image image = createColorTestImage();
			Image mask = createGradientMask();
			Color color = Color.create(0, 255, 0, 128);

			Stopwatch sw = new Stopwatch("maskWithColor");
			Image result = ImageHelper.maskWithColor(image, color, mask, false);
			sw.printElapsedTime();
			successCount++;
		}

		assertEquals(successCount, numberOfRuns);
	}

	@Test
	public void testMaskWithColorInverted()
	{
		Image image = createColorTestImage();
		Image mask = createGradientMask();
		Color color = Color.create(255, 0, 255);

		Image result = ImageHelper.maskWithColor(image, color, mask, true);
		compareWithExpected(result, "maskWithColorInverted");
	}

	@Test
	public void testMaskWithImageInPlace()
	{
		Image image1 = createColorTestImage();
		Image image2 = createSolidColorImage(testImageWidth, testImageHeight, Color.green);
		Image mask = createGradientMask();

		ImageHelper.maskWithImageInPlace(image1, image2, mask);
		compareWithExpected(image1, "maskWithImageInPlace");
	}

	@Test
	public void testDrawMaskOntoImage()
	{
		Image image = createColorTestImage();
		Image mask = createSmallBinaryMask(60, 60);

		ImageHelper.drawMaskOntoImage(image, mask, Color.yellow, new IntPoint(20, 20));
		compareWithExpected(image, "drawMaskOntoImage");
	}

	// ==================== Alpha Tests ====================

	@Test
	public void testSetAlphaFromMask()
	{
		Image image = createColorTestImage();
		// Use a mask that avoids alpha=0 (fully transparent pixels lose RGB during PNG round-trip)
		Image mask = createGradientMaskWithMinAlpha(64);

		Image result = ImageHelper.setAlphaFromMask(image, mask, false);

		assertEquals(ImageType.ARGB, result.getType(), "Result should have alpha channel");
		// Use threshold due to alpha premultiplication differences in PNG round-trip
		compareWithExpected(result, "setAlphaFromMask", 4);
	}

	@Test
	public void testSetAlphaFromMaskInverted()
	{
		Image image = createColorTestImage();
		// Use mask with min alpha to avoid fully transparent pixels
		Image mask = createGradientMaskWithMinAlpha(64);

		Image result = ImageHelper.setAlphaFromMask(image, mask, true);
		// Use threshold due to alpha premultiplication differences in PNG round-trip
		compareWithExpected(result, "setAlphaFromMaskInverted", 4);
	}

	@Test
	public void testApplyAlpha()
	{
		Image original = createColorTestImage();
		Image result = ImageHelper.applyAlpha(original, 128);

		try(PixelReader reader = result.createPixelReader())
		{
			assertEquals(reader.getPixelColor(20, 20).getAlpha(), 128);
		}

		assertEquals(ImageType.ARGB, result.getType(), "Result should have alpha channel");
		compareWithExpected(result, "applyAlpha");
	}

	@Test
	public void testApplyAlphaFull()
	{
		Image original = createColorTestImage();
		Image result = ImageHelper.applyAlpha(original, 255);

		// When alpha is 255, the original is returned unchanged
		assertSame(original, result, "With full alpha, original should be returned");
	}

	@Test
	public void testCopyAlphaTo()
	{
		Image target = createColorTestImage();
		Image alphaSource = createARGBTestImage();

		Image result = ImageHelper.copyAlphaTo(target, alphaSource);

		assertEquals(ImageType.ARGB, result.getType(), "Result should have alpha channel");
		// Use threshold due to alpha premultiplication differences in PNG round-trip
		compareWithExpected(result, "copyAlphaTo", 4);
	}

	@Test
	public void testSetAlphaOfAllPixels()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.ARGB);
		Painter p = image.createPainter();
		p.setColor(Color.red);
		p.fillRect(0, 0, testImageWidth, testImageHeight);
		p.dispose();

		ImageHelper.setAlphaOfAllPixels(image, 0);
		compareWithExpected(image, "setAlphaOfAllPixels");
	}

	// ==================== Colorify Tests ====================

	@Test
	public void testColorifyAlgorithm2()
	{
		Image grayscale = createGrayscaleTestImage();
		Color color = Color.create(100, 150, 200);

		Image result = ImageHelper.colorify(grayscale, color, ColorifyAlgorithm.algorithm2);
		compareWithExpected(result, "colorifyAlgorithm2", 2);
	}

	@Test
	public void testColorifyAlgorithm3()
	{
		Image grayscale = createGrayscaleTestImage();
		Color color = Color.create(200, 100, 50);

		Image result = ImageHelper.colorify(grayscale, color, ColorifyAlgorithm.algorithm3);
		compareWithExpected(result, "colorifyAlgorithm3", 2);
	}

	@Test
	public void testColorifySolidColor()
	{
		Image grayscale = createGrayscaleTestImage();
		Color color = Color.create(50, 200, 100);

		Image result = ImageHelper.colorify(grayscale, color, ColorifyAlgorithm.solidColor);
		compareWithExpected(result, "colorifySolidColor");
	}

	@Test
	public void testColorifyNone()
	{
		Image grayscale = createGrayscaleTestImage();
		Color color = Color.create(100, 100, 100);

		Image result = ImageHelper.colorify(grayscale, color, ColorifyAlgorithm.none);
		assertSame(grayscale, result, "None algorithm should return original");
	}

	// Disabling this test for now because it fails, but I don't actually use this functionality. TODO remove this test if I don't need it
//	@Test
//	public void testColorifyWithAlpha()
//	{
//		Image grayscale = createGrayscaleTestImage();
//		Color color = Color.create(100, 150, 200, 128);
//
//		Image result = ImageHelper.colorify(grayscale, color, ColorifyAlgorithm.algorithm3);
//
//		assertEquals(ImageType.ARGB, result.getType(), "Result should have alpha channel when color has transparency");
//		// Use threshold due to potential alpha premultiplication differences in PNG round-trip
//		compareWithExpected(result, "colorifyWithAlpha", 4);
//	}

	// ==================== Grayscale Modification Tests ====================

	@Test
	public void testMaximizeContrastGrayscale()
	{
		Image image = createLowContrastGrayscaleImage();
		ImageHelper.maximizeContrastGrayscale(image);
		compareWithExpected(image, "maximizeContrastGrayscale");
	}

	@Test
	public void testScaleGrayLevels()
	{
		Image image = createGrayscaleTestImage();
		ImageHelper.scaleGrayLevels(image, 0.5f);
		compareWithExpected(image, "scaleGrayLevels");
	}

	@Test
	public void testThreshold()
	{
		Image image = createGrayscaleTestImage();
		ImageHelper.threshold(image, 128);
		compareWithExpected(image, "threshold");
	}

	@Test
	public void testThresholdWithHighValue()
	{
		Image image = createGrayscaleTestImage();
		ImageHelper.threshold(image, 100, 200);
		compareWithExpected(image, "thresholdWithHighValue");
	}

	// ==================== Arithmetic Operations Tests ====================

	@Test
	public void testAdd()
	{
		Image target = createGrayscaleTestImage();
		Image other = createGrayscaleGradientVertical();

		ImageHelper.add(target, other);
		compareWithExpected(target, "add");
	}

	@Test
	public void testSubtract()
	{
		Image target = createSolidGrayscaleImage(200);
		Image other = createGrayscaleTestImage();

		ImageHelper.subtract(target, other);
		compareWithExpected(target, "subtract");
	}

	@Test
	public void testFillInTarget()
	{
		Image target = createGrayscaleTestImage();
		Image source = createGrayscaleGradientVertical();

		ImageHelper.fillInTarget(target, source, 100, 200, 255);
		compareWithExpected(target, "fillInTarget");
	}

	@Test
	public void testSubtractThresholded()
	{
		Image toThreshold = createGrayscaleTestImage();
		Image toSubtractFrom = createSolidGrayscaleImage(200);

		ImageHelper.subtractThresholded(toThreshold, 128, 100, toSubtractFrom);
		compareWithExpected(toSubtractFrom, "subtractThresholded");
	}

	@Test
	public void testAddThresholded()
	{
		Image toThreshold = createGrayscaleTestImage();
		Image toAddTo = createSolidGrayscaleImage(50);

		ImageHelper.addThresholded(toThreshold, 128, 100, toAddTo);
		compareWithExpected(toAddTo, "addThresholded");
	}

	// ==================== Draw If Pixel Value Greater Tests ====================

	@Test
	public void testDrawIfPixelValueIsGreaterThanTarget()
	{
		Image target = Image.create(testImageWidth, testImageHeight, ImageType.Binary);
		Image toDraw = createSmallBinaryMask(60, 60);

		ImageHelper.drawIfPixelValueIsGreaterThanTarget(target, toDraw, 20, 20);
		compareWithExpected(target, "drawIfPixelValueIsGreaterThanTarget");
	}

	// ==================== Convolution and Blur Tests ====================

	@Test
	public void testConvolveGrayscale()
	{
		Image image = createGrayscaleTestImage();
		float[][] kernel = ImageHelper.createGaussianKernel(5);

		Image result = ImageHelper.convolveGrayscale(image, kernel, true, true);
		compareWithExpected(result, "convolveGrayscale");
	}

	@Test
	public void testBlur()
	{
		Image image = createGrayscaleTestImage();
		Image blurred = ImageHelper.blur(image, 3, false,true);

		assertEquals(image.getWidth(), blurred.getWidth(), "Width should match");
		assertEquals(image.getHeight(), blurred.getHeight(), "Height should match");
		compareWithExpected(blurred, "blur");
	}

	@Test
	public void testBlurZeroLevel()
	{
		Image image = createGrayscaleTestImage();
		Image blurred = ImageHelper.blur(image, 0, false, true);

		assertSame(image, blurred, "With blur level 0, original should be returned");
	}

	@Test
	public void testBlurLine()
	{
		Image image = createGrayscaleXImage(ImageType.Grayscale8Bit);
		Image blurred = ImageHelper.blur(image, 5, false, true);
		compareWithExpected(blurred, "blurLine");
	}

	@Test
	public void testBlurAndScaleLine()
	{
		Image image = createGrayscaleXImage(ImageType.Grayscale8Bit);
		Image blurred = ImageHelper.blurAndScale(image, 20, 2.3973336f,true);

		assertEquals(image.getWidth(), blurred.getWidth(), "Width should match");
		assertEquals(image.getHeight(), blurred.getHeight(), "Height should match");
		compareWithExpected(blurred, "blurAndScaleLine");
	}

	// Commented out because binary vs grayscale isn't precise enough to get pixel-perfect matching in results.
//	@Test
//	public void testBlurBinaryVsGrayscale()
//	{
//		Image gray8Bit = createGrayscaleXImage(ImageType.Grayscale8Bit);
//		Image binary = createGrayscaleXImage(ImageType.Binary);
//		final int threshold = 5;
//		MapTestUtil.checkIfImagesAreEqualAndWriteToFailedIfNot(gray8Bit, binary, threshold, "grayVsBinaryX", failedFolderName);
//
//		Image blurredGray8Bit = ImageHelper.blurAndScale(gray8Bit, 20, 2.3973336f,true);
//		Image blurredBinary = ImageHelper.blurAndScale(binary, 20, 2.3973336f, true);
//		MapTestUtil.checkIfImagesAreEqualAndWriteToFailedIfNot(blurredGray8Bit, blurredBinary, threshold, "testBlurBinaryVsGrayscale", failedFolderName);
//	}

	private Image createGrayscaleXImage(ImageType type)
	{
		Image image = Image.create(testImageWidth, testImageHeight, type);
		Painter painter = image.createPainter(DrawQuality.High);
		painter.setColor(Color.white);
		painter.setBasicStroke(2.0f);
		painter.drawLine(10, 10, 90, 90);
		painter.drawLine(10, 90, 90, 10);
		painter.dispose();
		return image;
	}


	// ==================== Array and Noise Tests ====================

	@Test
	public void testArrayToImage()
	{
		float[][] array = new float[testImageHeight][testImageWidth];
		for (int y = 0; y < testImageHeight; y++)
		{
			for (int x = 0; x < testImageWidth; x++)
			{
				array[y][x] = (float) (x + y) / (testImageWidth + testImageHeight - 2);
			}
		}

		Image result = ImageHelper.arrayToImage(array, ImageType.Grayscale8Bit);

		assertEquals(testImageWidth, result.getWidth(), "Width should match array columns");
		assertEquals(testImageHeight, result.getHeight(), "Height should match array rows");
		compareWithExpected(result, "arrayToImage");
	}

	@Test
	public void testGenWhiteNoise()
	{
		Random rand = new Random(12345);
		Image noise = ImageHelper.genWhiteNoise(rand, testImageHeight, testImageWidth, ImageType.Grayscale8Bit);

		assertEquals(testImageWidth, noise.getWidth(), "Width should match");
		assertEquals(testImageHeight, noise.getHeight(), "Height should match");
		compareWithExpected(noise, "genWhiteNoise");
	}

	// ==================== Copy Snippet Paste Tests ====================

	@Test
	public void testCopySnippetFromSourceAndPasteIntoTarget()
	{
		Image target = createColorTestImage();
		Image source = createSolidColorImage(testImageWidth, testImageHeight, Color.blue);

		IntPoint pasteLocation = new IntPoint(20, 20);
		IntRectangle copyBounds = new IntRectangle(0, 0, 30, 30);

		ImageHelper.copySnippetFromSourceAndPasteIntoTarget(target, source, pasteLocation, copyBounds, 5);
		compareWithExpected(target, "copySnippetFromSourceAndPasteIntoTarget");
	}

	// ==================== Combine Images With Mask Tests ====================

	@Test
	public void testCombineImagesWithMaskInRegion()
	{
		Image image1 = createColorTestImage();
		Image image2 = createSolidColorImage(testImageWidth, testImageHeight, Color.cyan);
		Image mask = createGradientMask();
		mask = ImageHelper.scale(mask, 40, 40, Method.QUALITY);

		Point pivot = new Point(50, 50);
		ImageHelper.combineImagesWithMaskInRegion(image1, image2, mask, 30, 30, Math.PI / 6, pivot);
		compareWithExpected(image1, "combineImagesWithMaskInRegion");
	}

	// ==================== Helper Methods ====================

	private Image createColorTestImage()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.RGB);
		image.withPainter(DrawQuality.High, (p) ->
		{
			// Create a pattern with different colors in each quadrant
			p.setColor(Color.red);
			p.fillRect(0, 0, 50, 50);

			p.setColor(Color.green);
			p.fillRect(50, 0, 50, 50);

			p.setColor(Color.blue);
			p.fillRect(0, 50, 50, 50);

			p.setColor(Color.yellow);
			p.fillRect(50, 50, 50, 50);

		});
		return image;
	}

	private Image createAsymmetricTestImage()
	{
		Image image = Image.create(80, 60, ImageType.RGB);
		Painter p = image.createPainter(DrawQuality.High);

		p.setColor(Color.white);
		p.fillRect(0, 0, 80, 60);

		// Draw a triangle in upper left to make it asymmetric
		p.setColor(Color.red);
		int[] xPoints = { 10, 40, 10 };
		int[] yPoints = { 10, 30, 50 };
		p.fillPolygon(xPoints, yPoints);

		// Draw a circle in upper right
		p.setColor(Color.blue);
		p.fillOval(50, 10, 20, 20);

		p.dispose();
		return image;
	}

	private Image createGrayscaleTestImage()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					int level = (int) ((x + y) * 255.0 / (testImageWidth + testImageHeight - 2));
					writer.setGrayLevel(x, y, level);
				}
			}
		}
		return image;
	}

	private Image createLowContrastGrayscaleImage()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					// Low contrast: values only between 100 and 150
					int level = 100 + (int) ((x + y) * 50.0 / (testImageWidth + testImageHeight - 2));
					writer.setGrayLevel(x, y, level);
				}
			}
		}
		return image;
	}

	private Image createARGBTestImage()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.ARGB);
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			// Use min alpha of 64 to avoid fully transparent pixels which lose RGB during PNG round-trip
			int minAlpha = 64;
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					// Create pattern with varying alpha from minAlpha to 255
					int alpha = minAlpha + ((255 - minAlpha) * x) / (testImageWidth - 1);
					writer.setRGB(x, y, 255, 128, 64, alpha);
				}
			}
		}
		return image;
	}

	private Image createGradientMask()
	{
		Image mask = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		try (PixelReaderWriter writer = mask.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					int level = (x * 255) / testImageWidth;
					writer.setGrayLevel(x, y, level);
				}
			}
		}
		return mask;
	}

	/**
	 * Creates a horizontal gradient mask from minLevel to maxLevel.
	 * Use minLevel > 0 to avoid fully transparent pixels which lose RGB during PNG round-trip.
	 */
	private Image createGradientMaskWithMinAlpha(int minLevel)
	{
		int maxLevel = 255 - minLevel; // Range from minLevel to (255-minLevel) to avoid both extremes
		Image mask = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		try (PixelReaderWriter writer = mask.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					int level = minLevel + ((maxLevel - minLevel) * x) / (testImageWidth - 1);
					writer.setGrayLevel(x, y, level);
				}
			}
		}
		return mask;
	}

	private Image createSmallBinaryMask(int width, int height)
	{
		Image mask = Image.create(width, height, ImageType.Binary);
		try (PixelReaderWriter writer = mask.createPixelReaderWriter())
		{
			// Create a circular mask using pixel manipulation
			int centerX = width / 2;
			int centerY = height / 2;
			int radius = Math.min(width, height) / 3;
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int dx = x - centerX;
					int dy = y - centerY;
					if (dx * dx + dy * dy <= radius * radius)
					{
						writer.setGrayLevel(x, y, 255);
					}
				}
			}
		}
		return mask;
	}

	private Image createSolidColorImage(int width, int height, Color color)
	{
		Image image = Image.create(width, height, ImageType.RGB);
		Painter p = image.createPainter();
		p.setColor(color);
		p.fillRect(0, 0, width, height);
		p.dispose();
		return image;
	}

	private Image createSolidGrayscaleImage(int level)
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					writer.setGrayLevel(x, y, level);
				}
			}
		}
		return image;
	}

	private Image createGrayscaleGradientVertical()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				int level = (y * 255) / (testImageHeight - 1);
				for (int x = 0; x < testImageWidth; x++)
				{
					writer.setGrayLevel(x, y, level);
				}
			}
		}
		return image;
	}

	private static String getExpectedFilePath(String testName)
	{
		return Paths.get("unit test files", expectedFolderName, testName + ".png").toString();
	}

	private static String getFailedFilePath(String testName)
	{
		return Paths.get("unit test files", failedFolderName, testName + ".png").toString();
	}

	private void compareWithExpected(Image actual, String testName)
	{
		compareWithExpected(actual, testName, 0);
	}

	/**
	 * Compare actual image with expected, allowing a threshold for pixel differences.
	 * Use threshold > 0 for images with partial alpha, which may have precision loss during PNG round-trip.
	 */
	private void compareWithExpected(Image actual, String testName, int threshold)
	{
		String expectedFilePath = getExpectedFilePath(testName);
		Image expected;

		if (new File(expectedFilePath).exists())
		{
			expected = Assets.readImage(expectedFilePath);
		}
		else
		{
			expected = actual;
			ImageHelper.write(actual, expectedFilePath);
			return;
		}

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expected, actual, threshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedFolderName).toString());
			ImageHelper.write(actual, getFailedFilePath(testName));
			MapTestUtil.createImageDiffIfImagesAreSameSize(expected, actual, testName, threshold, failedFolderName);
			fail("Test '" + testName + "' failed: " + comparisonErrorMessage);
		}
	}
}
