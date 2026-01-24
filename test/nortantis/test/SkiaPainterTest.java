package nortantis.test;

import nortantis.Stroke;
import nortantis.StrokeType;
import nortantis.geom.FloatPoint;
import nortantis.geom.IntRectangle;
import nortantis.platform.*;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SkiaPainter drawing operations and SkiaPixelReaderWriter.
 *
 * These tests follow the pattern from SkiaMapCreatorTest: they compare rendered images against expected images stored in "unit test
 * files/expected skia tests/". When an expected image doesn't exist, the test creates it from the actual output. Failed tests save their
 * output to "unit test files/failed skia tests/" along with a diff image.
 */
public class SkiaPainterTest
{
	private static final String expectedFolderName = "expected skia tests";
	private static final String failedFolderName = "failed skia tests";
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

	@Test
	public void testBlankImageRGB()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.RGB);
		compareWithExpected(image, "blankImageRGB");
	}

	@Test
	public void testBlankImageARGB()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.ARGB);
		compareWithExpected(image, "blankImageARGB");
	}

	@Test
	public void testBlankImageGrayscale8Bit()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		compareWithExpected(image, "blankImageGrayscale8Bit");
	}

	// This functionality isn't implemented yet.
	// @Test
	// public void testBlankImageGrayscale16Bit()
	// {
	// Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale16Bit);
	// compareWithExpected(image, "blankImageGrayscale16Bit");
	// }

	@Test
	public void testBlankImageBinary()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Binary);
		compareWithExpected(image, "blankImageBinary");
	}

	@Test
	public void testPainterOnGrayscale8Bit()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			// Draw shapes with different gray levels
			painter.setColor(createColor(255, 255, 255));
			painter.fillRect(10, 10, 30, 30);

			painter.setColor(createColor(128, 128, 128));
			painter.fillRect(50, 10, 40, 40);

			painter.setColor(createColor(64, 64, 64));
			painter.fillOval(10, 50, 35, 35);

			painter.setColor(createColor(200, 200, 200));
			painter.setBasicStroke(3.0f);
			painter.drawLine(50, 60, 90, 90);
		}

		compareWithExpected(image, "painterOnGrayscale8Bit");
	}

	@Test
	public void testPainterOnBinary()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Binary);
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			// Draw white shapes on black background
			painter.setColor(createColor(255, 255, 255));
			painter.fillRect(5, 5, 40, 40);
			painter.fillOval(50, 5, 45, 45);

			// Draw a polygon
			int[] xPoints = { 25, 45, 35, 15 };
			int[] yPoints = { 55, 65, 95, 85 };
			painter.fillPolygon(xPoints, yPoints);

			// Draw lines
			painter.setBasicStroke(2.0f);
			painter.drawLine(55, 55, 95, 95);
			painter.drawLine(55, 95, 95, 55);
		}

		compareWithExpected(image, "painterOnBinary");
	}

	// This functionality isn't implemented yet.
	// @Test
	// public void testGradientOnGrayscale16Bit()
	// {
	// Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale16Bit);
	// Painter painter = image.createPainter(DrawQuality.High);
	//
	// // Draw a gradient from black to white
	// Color startColor = createColor(0, 0, 0);
	// Color endColor = createColor(255, 255, 255);
	// painter.setGradient(0, 0, startColor, testImageWidth, testImageHeight, endColor);
	// painter.fillRect(0, 0, testImageWidth, testImageHeight);
	//
	// painter.dispose();
	//
	// compareWithExpected(image, "gradientOnGrayscale16Bit");
	// }

	// ==================== Shape Drawing Tests ====================

	@Test
	public void testDrawLine()
	{
		Image image = createTestImage();
		image.withPainter(DrawQuality.High, (painter) ->
		{
			painter.setColor(createColor(255, 0, 0));
			painter.setBasicStroke(2.0f);
			painter.drawLine(10, 10, 90, 90);
			painter.drawLine(10, 90, 90, 10);
		});

		compareWithExpected(image, "drawLine");
	}

	@Test
	public void testDrawLineFloat()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(0, 128, 255));
			painter.setBasicStroke(1.5f);
			painter.drawLine(10.5f, 20.5f, 80.5f, 70.5f);
			painter.drawLine(50.0f, 10.0f, 50.0f, 90.0f);
		}

		compareWithExpected(image, "drawLineFloat");
	}

	@Test
	public void testDrawRect()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(0, 0, 255));
			painter.setBasicStroke(2.0f);
			painter.drawRect(10, 10, 80, 80);
			painter.setColor(createColor(255, 0, 0));
			painter.drawRect(25, 25, 50, 50);
		}

		compareWithExpected(image, "drawRect");
	}

	@Test
	public void testFillRect()
	{
		Image image = createTestImage();
		image.withPainter(DrawQuality.High, painter ->
		{
			painter.setColor(createColor(0, 255, 0));
			painter.fillRect(10, 10, 40, 40);
			painter.setColor(createColor(255, 128, 0));
			painter.fillRect(50, 50, 40, 40);
		});

		compareWithExpected(image, "testFillRect");
	}

	@Test
	public void testDrawOval()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(128, 0, 128));
			painter.setBasicStroke(2.0f);
			painter.drawOval(10, 10, 80, 80);
			painter.setColor(createColor(0, 128, 128));
			painter.drawOval(20, 30, 60, 40);
		}

		compareWithExpected(image, "drawOval");
	}

	@Test
	public void testFillOval()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(255, 200, 0));
			painter.fillOval(10, 10, 80, 80);
			painter.setColor(createColor(200, 50, 100));
			painter.fillOval(30, 40, 40, 30);
		}

		compareWithExpected(image, "fillOval");
	}

	@Test
	public void testDrawPolygon()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(0, 100, 200));
			painter.setBasicStroke(2.0f);

			int[] xPoints = { 50, 90, 70, 30, 10 };
			int[] yPoints = { 10, 40, 90, 90, 40 };
			painter.drawPolygon(xPoints, yPoints);
		}

		compareWithExpected(image, "drawPolygon");
	}

	@Test
	public void testFillPolygon()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(100, 200, 50));

			int[] xPoints = { 50, 90, 70, 30, 10 };
			int[] yPoints = { 10, 40, 90, 90, 40 };
			painter.fillPolygon(xPoints, yPoints);
		}

		compareWithExpected(image, "fillPolygon");
	}

	@Test
	public void testDrawPolyline()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(200, 100, 50));
			painter.setBasicStroke(3.0f);

			int[] xPoints = { 10, 30, 50, 70, 90 };
			int[] yPoints = { 50, 20, 80, 20, 50 };
			painter.drawPolyline(xPoints, yPoints);
		}

		compareWithExpected(image, "drawPolyline");
	}

	@Test
	public void testDrawPolygonFloat()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(150, 50, 200));
			painter.setBasicStroke(2.0f);

			List<FloatPoint> points = Arrays.asList(new FloatPoint(50.5f, 10.5f), new FloatPoint(90.5f, 40.5f), new FloatPoint(70.5f, 90.5f), new FloatPoint(30.5f, 90.5f),
					new FloatPoint(10.5f, 40.5f));
			painter.drawPolygonFloat(points);
		}

		compareWithExpected(image, "drawPolygonFloat");
	}


	// ==================== Stroke Tests ====================

	@Test
	public void testBasicStroke()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(0, 0, 0));

			painter.setBasicStroke(1.0f);
			painter.drawLine(10, 20, 90, 20);

			painter.setBasicStroke(3.0f);
			painter.drawLine(10, 50, 90, 50);

			painter.setBasicStroke(5.0f);
			painter.drawLine(10, 80, 90, 80);
		}

		compareWithExpected(image, "basicStroke");
	}

	@Test
	public void testDashedStroke()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(0, 0, 0));

			painter.setStroke(new Stroke(StrokeType.Dashes, 2.0f), 1.0);
			painter.drawLine(10, 30, 90, 30);

			painter.setStroke(new Stroke(StrokeType.Rounded_Dashes, 2.0f), 1.0);
			painter.drawLine(10, 50, 90, 50);

			painter.setStroke(new Stroke(StrokeType.Dots, 2.0f), 1.0);
			painter.drawLine(10, 70, 90, 70);
		}

		compareWithExpected(image, "dashedStroke");
	}

	@Test
	public void testStrokeSolidNoEndDecorations()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(100, 100, 100));

			painter.setStrokeToSolidLineWithNoEndDecorations(6.0f);
			painter.drawLine(10, 50, 90, 50);
		}

		compareWithExpected(image, "strokeSolidNoEndDecorations");
	}

	// ==================== Gradient Test ====================

	@Test
	public void testGradient()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			Color startColor = createColor(255, 0, 0);
			Color endColor = createColor(0, 0, 255);
			painter.setGradient(0, 0, startColor, 100, 100, endColor);
			painter.fillRect(10, 10, 80, 80);
		}

		compareWithExpected(image, "gradient");
	}

	// ==================== Text Drawing Test ====================

	@Test
	public void testDrawString()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			Font font = PlatformFactory.getInstance().createFont("SansSerif", FontStyle.Plain, 14);
			painter.setFont(font);
			painter.setColor(Color.green);
			painter.drawString("Hello", 10, 30);

			Font boldFont = PlatformFactory.getInstance().createFont("SansSerif", FontStyle.Bold, 18);
			painter.setFont(boldFont);
			painter.setColor(Color.red);
			painter.drawString("World", 10, 60);
		}

		compareWithExpected(image, "drawString");
	}

	@Test
	public void testStringWidthAndCharWidth()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			// Use Arial as it's more commonly available across platforms
			Font font = PlatformFactory.getInstance().createFont("Arial", FontStyle.Plain, 14);
			painter.setFont(font);

			int stringWidth = painter.stringWidth("Test");
			int charWidth = painter.charWidth('T');
			int fontAscent = painter.getFontAscent();
			int fontDescent = painter.getFontDescent();

			// If the font is found, width should be positive
			// Font metrics can be 0 if font is not available, which is acceptable
			assertTrue(stringWidth >= 0, "String width should be non-negative");
			assertTrue(charWidth >= 0, "Char width should be non-negative");
			assertTrue(fontAscent >= 0, "Font ascent should be non-negative");
			assertTrue(fontDescent >= 0, "Font descent should be non-negative");

			// At least one metric should be positive if any font rendering works
			assertTrue(stringWidth > 0 || charWidth > 0 || fontAscent > 0,
					"At least one font metric should be positive if font rendering is available. " + "stringWidth=" + stringWidth + ", charWidth=" + charWidth + ", fontAscent=" + fontAscent);
		}
	}

	// ==================== Image Drawing Test ====================

	@Test
	public void testDrawImage()
	{
		Image canvas = createTestImage();
		Image smallImage = Image.create(20, 20, ImageType.ARGB);

		try (PixelReaderWriter writer = smallImage.createPixelReaderWriter())
		{
			for (int y = 0; y < 20; y++)
			{
				for (int x = 0; x < 20; x++)
				{
					if ((x + y) % 7 == 0)
					{
						writer.setRGB(x, y, 255, 0, 0);
					}
					else
					{
						writer.setRGB(x, y, 0, 255, 0);
					}
				}
			}
		}

		compareWithExpected(canvas, "smallImage"); // TODO remove

		try (Painter painter = canvas.createPainter(DrawQuality.High))
		{
			painter.drawImage(smallImage, 10, 10);
			painter.drawImage(smallImage, 50, 50, 40, 40);
		}

		compareWithExpected(canvas, "drawImage");
	}

	@Test
	public void testCopySubImage()
	{
		// Create source image with a distinctive pattern
		Image source = Image.create(80, 80, ImageType.RGB);
		try (PixelReaderWriter writer = source.createPixelReaderWriter())
		{
			for (int y = 0; y < 80; y++)
			{
				for (int x = 0; x < 80; x++)
				{
					// Create quadrants with different colors
					if (x < 40 && y < 40)
					{
						writer.setRGB(x, y, 255, 0, 0); // Red top-left
					}
					else if (x >= 40 && y < 40)
					{
						writer.setRGB(x, y, 0, 255, 0); // Green top-right
					}
					else if (x < 40 && y >= 40)
					{
						writer.setRGB(x, y, 0, 0, 255); // Blue bottom-left
					}
					else
					{
						writer.setRGB(x, y, 255, 255, 0); // Yellow bottom-right
					}
				}
			}
		}

		// Copy a sub-region that spans all four quadrants (center 40x40)
		IntRectangle bounds = new IntRectangle(20, 20, 40, 40);
		Image subImage = source.copySubImage(bounds);

		// Verify dimensions
		assertEquals(40, subImage.getWidth(), "Sub-image width");
		assertEquals(40, subImage.getHeight(), "Sub-image height");
		assertEquals(ImageType.RGB, subImage.getType(), "Sub-image type should match source");

		// Verify pixel content
		try (PixelReader reader = subImage.createPixelReader())
		{
			// Top-left of sub-image should be red (from original at 20,20)
			int rgb1 = reader.getRGB(5, 5);
			assertEquals(255, (rgb1 >> 16) & 0xFF, "Red channel at sub-image top-left");
			assertEquals(0, (rgb1 >> 8) & 0xFF, "Green channel at sub-image top-left");
			assertEquals(0, rgb1 & 0xFF, "Blue channel at sub-image top-left");

			// Top-right of sub-image should be green (from original at 60,20)
			int rgb2 = reader.getRGB(35, 5);
			assertEquals(0, (rgb2 >> 16) & 0xFF, "Red channel at sub-image top-right");
			assertEquals(255, (rgb2 >> 8) & 0xFF, "Green channel at sub-image top-right");
			assertEquals(0, rgb2 & 0xFF, "Blue channel at sub-image top-right");

			// Bottom-left of sub-image should be blue (from original at 20,60)
			int rgb3 = reader.getRGB(5, 35);
			assertEquals(0, (rgb3 >> 16) & 0xFF, "Red channel at sub-image bottom-left");
			assertEquals(0, (rgb3 >> 8) & 0xFF, "Green channel at sub-image bottom-left");
			assertEquals(255, rgb3 & 0xFF, "Blue channel at sub-image bottom-left");

			// Bottom-right of sub-image should be yellow (from original at 60,60)
			int rgb4 = reader.getRGB(35, 35);
			assertEquals(255, (rgb4 >> 16) & 0xFF, "Red channel at sub-image bottom-right");
			assertEquals(255, (rgb4 >> 8) & 0xFF, "Green channel at sub-image bottom-right");
			assertEquals(0, rgb4 & 0xFF, "Blue channel at sub-image bottom-right");
		}

		// Verify that modifications to sub-image don't affect the original
		try (PixelReaderWriter writer = subImage.createPixelReaderWriter())
		{
			writer.setRGB(5, 5, 0, 0, 0); // Set to black
		}

		// Original should still be red at the corresponding location
		try (PixelReader reader = source.createPixelReader())
		{
			int rgb = reader.getRGB(25, 25);
			assertEquals(255, (rgb >> 16) & 0xFF, "Original should still be red after modifying copy");
		}

		compareWithExpected(subImage, "copySubImage");
	}

	@Test
	public void testCopySubImageWithAlphaChannel()
	{
		// Create an RGB source image (no alpha)
		Image source = Image.create(60, 60, ImageType.RGB);
		try (PixelReaderWriter writer = source.createPixelReaderWriter())
		{
			for (int y = 0; y < 60; y++)
			{
				for (int x = 0; x < 60; x++)
				{
					writer.setRGB(x, y, 100, 150, 200);
				}
			}
		}

		// Copy with alpha channel added
		IntRectangle bounds = new IntRectangle(10, 10, 40, 40);
		Image subImageWithAlpha = source.copySubImage(bounds, true);

		// Verify type is ARGB
		assertEquals(ImageType.ARGB, subImageWithAlpha.getType(), "Sub-image should have alpha channel");
		assertEquals(40, subImageWithAlpha.getWidth(), "Sub-image width");
		assertEquals(40, subImageWithAlpha.getHeight(), "Sub-image height");

		compareWithExpected(subImageWithAlpha, "copySubImageWithAlpha");
	}

	@Test
	public void testCopySubImageGrayscale()
	{
		// Create a grayscale source with a gradient
		Image source = Image.create(80, 80, ImageType.Grayscale8Bit);
		try (PixelReaderWriter writer = source.createPixelReaderWriter())
		{
			for (int y = 0; y < 80; y++)
			{
				for (int x = 0; x < 80; x++)
				{
					int level = (x + y) * 255 / 158; // Gradient from top-left to bottom-right
					writer.setGrayLevel(x, y, Math.min(255, level));
				}
			}
		}

		// Copy a sub-region
		IntRectangle bounds = new IntRectangle(20, 20, 40, 40);
		Image subImage = source.copySubImage(bounds);

		assertEquals(40, subImage.getWidth(), "Sub-image width");
		assertEquals(40, subImage.getHeight(), "Sub-image height");

		compareWithExpected(subImage, "copySubImageGrayscale");
	}

	// ==================== Transform Tests ====================

	@Test
	public void testTranslate()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(255, 0, 0));
			painter.fillRect(0, 0, 20, 20);

			painter.translate(30, 30);
			painter.setColor(createColor(0, 255, 0));
			painter.fillRect(0, 0, 20, 20);

			painter.translate(30, 30);
			painter.setColor(createColor(0, 0, 255));
			painter.fillRect(0, 0, 20, 20);
		}

		compareWithExpected(image, "translate");
	}

	@Test
	public void testRotate()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(255, 0, 0));
			painter.fillRect(40, 10, 20, 30);

			painter.rotate(Math.PI / 4, 50, 50);
			painter.setColor(createColor(0, 255, 0, 128));
			painter.fillRect(40, 10, 20, 30);
		}

		compareWithExpected(image, "rotate");
	}

	// ==================== Clip Test ====================

	@Test
	public void testSetClip()
	{
		Image image = createTestImage();
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setClip(20, 20, 60, 60);
			painter.setColor(createColor(255, 0, 0));
			painter.fillRect(0, 0, 100, 100);
		}

		compareWithExpected(image, "setClip");
	}

	// ==================== Alpha Composite Tests ====================

	@Test
	public void testAlphaCompositeWithAlpha()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.ARGB);
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(255, 0, 0));
			painter.fillRect(20, 20, 60, 60);

			painter.setColor(createColor(0, 0, 255));
			painter.setAlphaComposite(nortantis.platform.AlphaComposite.SrcOver, 0.5f);
			painter.fillRect(40, 40, 60, 60);
		}

		compareWithExpected(image, "alphaCompositeWithAlpha");
	}

	// ==================== PixelReaderWriter Tests ====================

	@Test
	public void testPixelReaderWriterSetGetRGB()
	{
		Image image = createTestImage();

		// Initialize all pixels to black first
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					writer.setRGB(x, y, 0, 0, 0);
				}
			}

			// Now set specific test pixels
			writer.setRGB(10, 10, 255, 0, 0);
			writer.setRGB(20, 20, 0, 255, 0);
			writer.setRGB(30, 30, 0, 0, 255);
			// Use full alpha for RGB images
			writer.setRGB(40, 40, 255, 255, 0);
		}

		try (PixelReader reader = image.createPixelReader())
		{
			int rgb1 = reader.getRGB(10, 10);
			assertEquals(255, (rgb1 >> 16) & 0xFF, "Red channel at (10,10)");
			assertEquals(0, (rgb1 >> 8) & 0xFF, "Green channel at (10,10)");
			assertEquals(0, rgb1 & 0xFF, "Blue channel at (10,10)");

			int rgb2 = reader.getRGB(20, 20);
			assertEquals(0, (rgb2 >> 16) & 0xFF, "Red channel at (20,20)");
			assertEquals(255, (rgb2 >> 8) & 0xFF, "Green channel at (20,20)");

			int rgb3 = reader.getRGB(30, 30);
			assertEquals(0, (rgb3 >> 16) & 0xFF, "Red channel at (30,30)");
			assertEquals(0, (rgb3 >> 8) & 0xFF, "Green channel at (30,30)");
			assertEquals(255, rgb3 & 0xFF, "Blue channel at (30,30)");
		}

		compareWithExpected(image, "pixelReaderWriterSetGetRGB");
	}

	@Test
	public void testPixelReaderWriterSetGetGrayscale()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);

		// Initialize all pixels to black first
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					writer.setRGB(x, y, 0, 0, 0);
				}
			}

			// Now set specific test pixels
			writer.setGrayLevel(10, 10, 100);
			writer.setGrayLevel(20, 20, 150);
			writer.setGrayLevel(30, 30, 200);
			writer.setGrayLevel(99, 99, 255);
		}

		try (PixelReader reader = image.createPixelReader())
		{
			int gray1 = reader.getGrayLevel(10, 10);
			assertEquals(gray1, 100, "Gray level at (10, 10)");

			// TODO Finish testing other 2 cases
			int gray2 = reader.getGrayLevel(20, 20);
			assertEquals(gray2, 150, "Gray level at (20, 20)");

			int gray3 = reader.getGrayLevel(30, 30);
			assertEquals(gray3, 200, "Gray level at (30, 30)");

			int gray4 = reader.getGrayLevel(99, 99);
			assertEquals(gray4, 255, "Gray level at (99, 99)");

		}

		compareWithExpected(image, "pixelReaderWriterSetGetGrayscale");
	}

	@Test
	public void testPixelReaderWriterSetPixelColor()
	{
		Image image = createTestImage();

		Color cyan = createColor(0, 255, 255);
		Color magenta = createColor(255, 0, 255);

		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < 50; y++)
			{
				for (int x = 0; x < 50; x++)
				{
					writer.setPixelColor(x, y, cyan);
				}
			}
			for (int y = 50; y < 100; y++)
			{
				for (int x = 50; x < 100; x++)
				{
					writer.setPixelColor(x, y, magenta);
				}
			}
		}

		compareWithExpected(image, "pixelReaderWriterSetPixelColor");
	}

	@Test
	public void testPixelReaderWriterBandLevel()
	{
		Image image = createTestImage();

		// Initialize all pixels to black first, then set individual bands
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < 100; y++)
			{
				for (int x = 0; x < 100; x++)
				{
					// Initialize to black
					writer.setRGB(x, y, 0, 0, 0);
				}
			}

			// Now set individual band levels
			for (int y = 0; y < 100; y++)
			{
				for (int x = 0; x < 100; x++)
				{
					if (x < 33)
					{
						writer.setBandLevel(x, y, 0, 255);
					}
					else if (x < 66)
					{
						writer.setBandLevel(x, y, 1, 255);
					}
					else
					{
						writer.setBandLevel(x, y, 2, 255);
					}
				}
			}
		}

		try (PixelReader reader = image.createPixelReader())
		{
			assertEquals(255, reader.getBandLevel(10, 50, 0), "Red band at (10,50)");
			assertEquals(0, reader.getBandLevel(10, 50, 1), "Green band at (10,50)");
			assertEquals(0, reader.getBandLevel(10, 50, 2), "Blue band at (10,50)");

			assertEquals(0, reader.getBandLevel(50, 50, 0), "Red band at (50,50)");
			assertEquals(255, reader.getBandLevel(50, 50, 1), "Green band at (50,50)");
		}

		compareWithExpected(image, "pixelReaderWriterBandLevel");
	}

	@Test
	public void testPixelReaderWriterGrayLevel()
	{
		Image image = createTestImage();

		// Initialize all pixels to black first, then set gray levels
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < 100; y++)
			{
				for (int x = 0; x < 100; x++)
				{
					// Initialize to black
					writer.setRGB(x, y, 0, 0, 0);
				}
			}

			// Now set gray levels (setGrayLevel sets band 0 = red)
			for (int y = 0; y < 100; y++)
			{
				int level = (int) (y * 2.55);
				for (int x = 0; x < 100; x++)
				{
					writer.setGrayLevel(x, y, level);
				}
			}
		}

		try (PixelReader reader = image.createPixelReader())
		{
			assertEquals(0, reader.getGrayLevel(50, 0), "Gray level at top");
			int middleLevel = reader.getGrayLevel(50, 50);
			assertTrue(middleLevel > 100 && middleLevel < 150, "Gray level at middle should be around 127");

			float normalized = reader.getNormalizedPixelLevel(50, 50);
			assertTrue(normalized > 0.4f && normalized < 0.6f, "Normalized level at middle should be around 0.5");
		}

		compareWithExpected(image, "pixelReaderWriterGrayLevel");
	}

	@Test
	public void testPixelReaderWriterAlpha()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.ARGB);

		// Create a pattern with different alpha values
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			// Outer border: fully opaque red
			for (int y = 0; y < 100; y++)
			{
				for (int x = 0; x < 100; x++)
				{
					writer.setRGB(x, y, 255, 0, 0, 255);
				}
			}

			// Inner area: semi-transparent blue
			for (int y = 20; y < 80; y++)
			{
				for (int x = 20; x < 80; x++)
				{
					writer.setRGB(x, y, 0, 0, 255, 128);
				}
			}
		}

		try (PixelReader reader = image.createPixelReader())
		{
			assertEquals(255, reader.getAlpha(10, 10), "Alpha at (10,10) should be 255");
			// Alpha at center should be semi-transparent
			int centerAlpha = reader.getAlpha(50, 50);
			assertTrue(centerAlpha > 0 && centerAlpha < 255, "Alpha at center should be semi-transparent, got: " + centerAlpha);
		}

		compareWithExpected(image, "pixelReaderWriterAlpha");
	}

	@Test
	public void testPixelReaderWriterGetPixelColor()
	{
		Image image = createTestImage();

		Color testColor = createColor(100, 150, 200);

		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			writer.setPixelColor(50, 50, testColor);
		}

		try (PixelReader reader = image.createPixelReader())
		{
			Color readColor = reader.getPixelColor(50, 50);
			assertEquals(100, readColor.getRed(), "Red component");
			assertEquals(150, readColor.getGreen(), "Green component");
			assertEquals(200, readColor.getBlue(), "Blue component");
		}
	}

	@Test
	public void testPixelReaderWriterGrayscale8Bit()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale8Bit);

		// Create a gradient pattern
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				int level = (int) (y * 2.55);
				for (int x = 0; x < testImageWidth; x++)
				{
					writer.setGrayLevel(x, y, level);
				}
			}
		}

		try (PixelReader reader = image.createPixelReader())
		{
			assertEquals(0, reader.getGrayLevel(50, 0), "Gray level at top should be 0");
			int middleLevel = reader.getGrayLevel(50, 50);
			assertTrue(middleLevel > 100 && middleLevel < 150, "Gray level at middle should be around 127, got: " + middleLevel);
			int bottomLevel = reader.getGrayLevel(50, 99);
			assertTrue(bottomLevel > 240, "Gray level at bottom should be close to 255, got: " + bottomLevel);
		}

		compareWithExpected(image, "pixelReaderWriterGrayscale8Bit");
	}

	// This functionality is not implemented yet
	// @Test
	// public void testPixelReaderWriterGrayscale16Bit()
	// {
	// Image image = Image.create(testImageWidth, testImageHeight, ImageType.Grayscale16Bit);
	//
	// // Create a gradient pattern
	// try (PixelReaderWriter writer = image.createPixelReaderWriter())
	// {
	// for (int y = 0; y < testImageHeight; y++)
	// {
	// int level = (int) (y * 255);
	// for (int x = 0; x < testImageWidth; x++)
	// {
	// writer.setGrayLevel(x, y, level);
	// }
	// }
	// }
	//
	// compareWithExpected(image, "pixelReaderWriterGrayscale16Bit");
	// }

	@Test
	public void testPixelReaderWriterBinary()
	{
		Image image = Image.create(testImageWidth, testImageHeight, ImageType.Binary);

		// Create a checkerboard pattern
		try (PixelReaderWriter writer = image.createPixelReaderWriter())
		{
			for (int y = 0; y < testImageHeight; y++)
			{
				for (int x = 0; x < testImageWidth; x++)
				{
					// Create 10x10 checkerboard squares
					boolean isWhite = ((x / 20) + (y / 20)) % 2 == 0;
					writer.setGrayLevel(x, y, isWhite ? 255 : 0);
				}
			}
		}

		try (PixelReader reader = image.createPixelReader())
		{
			// Check white square (top-left)
			int whiteLevel = reader.getGrayLevel(5, 5);
			// assertTrue(whiteLevel > 200, "White square should be close to 255, got: " + whiteLevel);

			// Check black square
			int blackLevel = reader.getGrayLevel(15, 5);
			// assertTrue(blackLevel < 55, "Black square should be close to 0, got: " + blackLevel);
		}

		compareWithExpected(image, "pixelReaderWriterBinary");
	}

	// ==================== Anti-aliased Stroke Tests ====================
	// These tests are designed to detect differences between CPU and GPU rendering,
	// particularly for anti-aliased strokes like roads, coastlines, and rivers.

	@Test
	public void testAntiAliasedStrokesOnARGB()
	{
		// Create a larger image that will use GPU if available (> 256x256 threshold)
		Image image = Image.create(300, 300, ImageType.ARGB);

		// Fill with a background color
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(220, 210, 180)); // Parchment-like color
			painter.fillRect(0, 0, 300, 300);

			// Draw strokes similar to roads (dark brown)
			painter.setColor(createColor(80, 60, 40));
			painter.setBasicStroke(3.0f);
			painter.drawLine(20, 150, 280, 150); // Horizontal
			painter.drawLine(150, 20, 150, 280); // Vertical
			painter.drawLine(30, 30, 270, 270); // Diagonal

			// Draw strokes similar to coastlines (dark)
			painter.setColor(createColor(60, 50, 40));
			painter.setBasicStroke(2.0f);
			int[] coastX = { 20, 50, 80, 100, 130, 160, 180, 200, 230, 260, 280 };
			int[] coastY = { 80, 70, 85, 65, 75, 60, 70, 55, 65, 50, 60 };
			painter.drawPolyline(coastX, coastY);

			// Draw strokes similar to rivers (blue)
			painter.setColor(createColor(60, 90, 140));
			painter.setBasicStroke(2.5f);
			int[] riverX = { 40, 60, 90, 120, 150, 180, 210, 250 };
			int[] riverY = { 250, 230, 245, 220, 235, 210, 225, 200 };
			painter.drawPolyline(riverX, riverY);
		}

		compareWithExpected(image, "antiAliasedStrokesOnARGB");
	}

	@Test
	public void testAntiAliasedStrokesOnRGB()
	{
		// Create a larger image that will use GPU if available (> 256x256 threshold)
		Image image = Image.create(300, 300, ImageType.RGB);

		// Fill with a background color
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(220, 210, 180)); // Parchment-like color
			painter.fillRect(0, 0, 300, 300);

			// Draw strokes similar to roads (dark brown)
			painter.setColor(createColor(80, 60, 40));
			painter.setBasicStroke(3.0f);
			painter.drawLine(20, 150, 280, 150); // Horizontal
			painter.drawLine(150, 20, 150, 280); // Vertical
			painter.drawLine(30, 30, 270, 270); // Diagonal

			// Draw strokes similar to coastlines (dark)
			painter.setColor(createColor(60, 50, 40));
			painter.setBasicStroke(2.0f);
			int[] coastX = { 20, 50, 80, 100, 130, 160, 180, 200, 230, 260, 280 };
			int[] coastY = { 80, 70, 85, 65, 75, 60, 70, 55, 65, 50, 60 };
			painter.drawPolyline(coastX, coastY);

			// Draw strokes similar to rivers (blue)
			painter.setColor(createColor(60, 90, 140));
			painter.setBasicStroke(2.5f);
			int[] riverX = { 40, 60, 90, 120, 150, 180, 210, 250 };
			int[] riverY = { 250, 230, 245, 220, 235, 210, 225, 200 };
			painter.drawPolyline(riverX, riverY);
		}

		compareWithExpected(image, "antiAliasedStrokesOnRGB");
	}

	@Test
	public void testImageCompositing()
	{
		// Test that draws images onto a larger canvas to detect compositing differences
		Image canvas = Image.create(300, 300, ImageType.ARGB);
		Image sourceImage = Image.create(100, 100, ImageType.ARGB);

		// Create a source image with semi-transparent colors
		try (Painter p = sourceImage.createPainter(DrawQuality.High))
		{
			p.setColor(createColor(255, 0, 0, 200)); // Semi-transparent red
			p.fillRect(0, 0, 100, 100);
			p.setColor(createColor(0, 255, 0, 150)); // Semi-transparent green
			p.fillOval(20, 20, 60, 60);
		}

		// Draw the source image onto the canvas multiple times
		try (Painter painter = canvas.createPainter(DrawQuality.High))
		{
			// Background
			painter.setColor(createColor(220, 210, 180));
			painter.fillRect(0, 0, 300, 300);

			// Draw source image at different positions
			painter.drawImage(sourceImage, 20, 20);
			painter.drawImage(sourceImage, 120, 120);
			painter.drawImage(sourceImage, 180, 50);
		}

		compareWithExpected(canvas, "imageCompositing");
	}

	@Test
	public void testSemiTransparentOverlay()
	{
		// Test drawing semi-transparent shapes to detect alpha blending differences
		Image image = Image.create(300, 300, ImageType.ARGB);

		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			// Background
			painter.setColor(createColor(220, 210, 180));
			painter.fillRect(0, 0, 300, 300);

			// Draw overlapping semi-transparent shapes
			painter.setColor(createColor(255, 0, 0, 128)); // 50% red
			painter.fillRect(50, 50, 150, 150);

			painter.setColor(createColor(0, 255, 0, 128)); // 50% green
			painter.fillRect(100, 100, 150, 150);

			painter.setColor(createColor(0, 0, 255, 128)); // 50% blue
			painter.fillRect(75, 125, 150, 100);

			// Draw some strokes on top with alpha
			painter.setColor(createColor(0, 0, 0, 180)); // Semi-transparent black
			painter.setBasicStroke(4.0f);
			painter.drawLine(30, 30, 270, 270);
			painter.drawLine(30, 270, 270, 30);
		}

		compareWithExpected(image, "semiTransparentOverlay");
	}

	// ==================== Combined Test ====================

	@Test
	public void testCombinedDrawing()
	{
		Image image = Image.create(200, 200, ImageType.ARGB);
		try (Painter painter = image.createPainter(DrawQuality.High))
		{
			painter.setColor(createColor(230, 230, 230));
			painter.fillRect(0, 0, 200, 200);

			painter.setColor(createColor(100, 100, 200));
			painter.fillOval(10, 10, 80, 80);

			painter.setColor(createColor(200, 100, 100));
			int[] xPoints = { 150, 190, 170, 130, 110 };
			int[] yPoints = { 20, 50, 90, 90, 50 };
			painter.fillPolygon(xPoints, yPoints);

			painter.setColor(createColor(100, 200, 100));
			painter.fillRect(10, 110, 80, 80);

			Color startColor = createColor(255, 200, 0);
			Color endColor = createColor(200, 0, 255);
			painter.setGradient(110, 110, startColor, 190, 190, endColor);
			painter.fillRect(110, 110, 80, 80);

			painter.setColor(createColor(0, 0, 0));
			painter.setBasicStroke(2.0f);
			painter.drawRect(10, 10, 80, 80);
			painter.drawRect(110, 10, 80, 80);
			painter.drawRect(10, 110, 80, 80);
			painter.drawRect(110, 110, 80, 80);

			Font font = PlatformFactory.getInstance().createFont("SansSerif", FontStyle.Bold, 12);
			painter.setFont(font);
			painter.setColor(createColor(0, 0, 0));
			painter.drawString("Shapes", 70, 198);
		}

		compareWithExpected(image, "combinedDrawing");
	}

	// ==================== Helper Methods ====================

	private Image createTestImage()
	{
		return Image.create(testImageWidth, testImageHeight, ImageType.RGB);
	}

	private Color createColor(int r, int g, int b)
	{
		return Color.create(r, g, b);
	}

	private Color createColor(int r, int g, int b, int a)
	{
		return PlatformFactory.getInstance().createColor(r, g, b, a);
	}

	private static String getExpectedFilePath(String testName)
	{
		return Paths.get("unit test files", expectedFolderName, testName + ".png").toString();
	}

	private static String getFailedFilePath(String testName)
	{
		return Paths.get("unit test files", failedFolderName, testName + ".png").toString();
	}

	private static String getDiffFilePath(String testName)
	{
		return Paths.get("unit test files", failedFolderName, testName + " - diff.png").toString();
	}

	private void compareWithExpected(Image actual, String testName)
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

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedFolderName).toString());
			ImageHelper.write(actual, getFailedFilePath(testName));
			MapTestUtil.createImageDiffIfImagesAreSameSize(expected, actual, testName, failedFolderName);
			fail("Test '" + testName + "' failed: " + comparisonErrorMessage);
		}
	}
}
