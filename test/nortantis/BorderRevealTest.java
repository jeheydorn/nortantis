package nortantis;

import nortantis.geom.Dimension;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PixelReader;
import nortantis.platform.PixelWriter;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the reveal masks that let border art with a soft inner edge show the map through the transparent part of that edge, using
 * synthetic border art whose measurements are known exactly.
 */
public class BorderRevealTest
{
	/** The synthetic edge art is this tall, and the notches along its inner side reach this far into it. */
	private static final int edgeArtHeight = 32;
	private static final int notchDepth = 8;
	private static final int edgeArtWidth = 64;
	/** The notches take up the left half of the edge art's inner side. */
	private static final int notchWidth = edgeArtWidth / 2;
	private static final int cornerArtWidth = 32;

	@TempDir
	Path artPackFolder;

	@BeforeAll
	public static void setUpBeforeClass()
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
		Assets.disableAddedArtPacksForUnitTests();
	}

	@BeforeEach
	public void createSoftEdgedBorderArt() throws Exception
	{
		ImageCache.clear();
		Assets.clearArtPackCache();

		Path borderFolder = artPackFolder.resolve("borders").resolve("soft");
		Files.createDirectories(borderFolder);

		// A top edge that is solid except for notches cut out of the bottom (map-facing) side of its left half.
		Image topEdge = Image.create(edgeArtWidth, edgeArtHeight, ImageType.ARGB);
		try (PixelWriter pixels = topEdge.createPixelWriter())
		{
			for (int y = 0; y < edgeArtHeight; y++)
			{
				for (int x = 0; x < edgeArtWidth; x++)
				{
					boolean isNotch = y >= edgeArtHeight - notchDepth && x < notchWidth;
					pixels.setRGB(x, y, 0, 0, 0, isNotch ? 0 : 255);
				}
			}
		}
		topEdge.write(borderFolder.resolve("top_edge.png").toString());

		// A solid corner the same width as the edge band, so it is not inset and reveals nothing.
		Image corner = Image.create(cornerArtWidth, cornerArtWidth, ImageType.ARGB);
		try (Painter p = corner.createPainter())
		{
			p.setColor(Color.black);
			p.fillRect(0, 0, cornerArtWidth, cornerArtWidth);
		}
		corner.write(borderFolder.resolve("upper_left_corner.png").toString());
	}

	private MapSettings createSettings(BorderPosition position, int borderWidth)
	{
		MapSettings settings = new MapSettings();
		settings.solidColorBackground = true;
		settings.resolution = 1.0;
		settings.landColor = Color.create(0, 255, 0);
		settings.oceanColor = Color.create(0, 0, 255);
		settings.borderColorOption = BorderColorOption.Choose_color;
		settings.borderColor = Color.create(255, 0, 0);
		settings.drawBorder = true;
		settings.borderWidth = borderWidth;
		settings.borderPosition = position;
		settings.borderResource = new NamedResource(Assets.customArtPack, "soft");
		settings.customImagesPath = artPackFolder.toString();
		return settings;
	}

	private static Background createBackground(MapSettings settings, Dimension mapBounds)
	{
		return new Background(settings, mapBounds, new WarningLogger()
		{
			private final List<String> messages = new ArrayList<>();

			@Override
			public void addWarningMessage(String message)
			{
				messages.add(message);
			}

			@Override
			public List<String> getWarningMessages()
			{
				return messages;
			}
		});
	}

	/**
	 * The scalloped inner edge of FA01-Temperate's "rose" border, which is the real art this feature exists for. See
	 * "unit test files/border art/ATTRIBUTION.txt" for where it comes from and the license it is used under. The synthetic art above
	 * pins the behavior with measurements that are exact by construction; this pins it against artwork nobody here controls.
	 */
	private static final String realArtBorderName = "rose";
	/**
	 * The scallops themselves reach 25.37% of the way in; the extra pixel is the rim growth, which the frame has to shift far enough to
	 * cover.
	 */
	private static final double realArtInsetDepthFraction = 0.2585;

	private static NamedResource realArtBorderResource()
	{
		return new NamedResource(Assets.customArtPack, realArtBorderName);
	}

	private static String realArtCustomImagesPath()
	{
		return Paths.get("unit test files", "border art").toString();
	}

	private static BorderArt loadBorderArt(NamedResource borderResource, String customImagesPath)
	{
		return ImageCache.getInstance(borderResource.artPack, customImagesPath).getBorderArt(borderResource);
	}

	private MapSettings createSettingsForRealArt(BorderPosition position, int borderWidth)
	{
		MapSettings settings = createSettings(position, borderWidth);
		settings.borderResource = realArtBorderResource();
		settings.customImagesPath = realArtCustomImagesPath();
		return settings;
	}

	@Test
	public void insetDepthComesFromTheNotchesInTheEdgeArt()
	{
		BorderArt art = loadBorderArt(new NamedResource(Assets.customArtPack, "soft"), artPackFolder.toString());
		assertEquals(((double) notchDepth) / edgeArtHeight, art.getInsetDepthFraction(), 0.0001);
		assertEquals(edgeArtHeight, art.getEdgeOriginalWidth());
		assertEquals(cornerArtWidth, art.getCornerOriginalWidth());

		// The corner is not inset, so it touches the map at a single point and reveals nothing.
		assertTrue(art.createCornerRevealMasks(art.getEdgeOriginalWidth()).isEmpty());

		// The same measurements against the real art. It ships only a top edge, so the other three are derived from it and must agree.
		BorderArt realArt = loadBorderArt(realArtBorderResource(), realArtCustomImagesPath());
		assertEquals(realArtInsetDepthFraction, realArt.getInsetDepthFraction(), 0.001);
		for (BorderArt.BorderEdgeType type : BorderArt.BorderEdgeType.values())
		{
			assertNotNull(realArt.getEdgeRevealMask(type), "Every edge of " + realArtBorderName + " should reveal part of the map");
		}
		assertTrue(realArt.createCornerRevealMasks(realArt.getEdgeOriginalWidth()).isEmpty());
	}

	@Test
	public void outsideMapShrinksTheImageByTwiceTheInsetDepth()
	{
		final int borderWidth = 64;
		final int expectedPadding = borderWidth - (borderWidth * notchDepth) / edgeArtHeight;
		Dimension mapBounds = new Dimension(400, 300);
		Background background = createBackground(createSettings(BorderPosition.Outside_map, borderWidth), mapBounds);

		assertEquals(expectedPadding, background.getBorderPaddingScaledByResolution());
		assertEquals(borderWidth, background.getBorderWidthScaledByResolution());
		assertEquals(mapBounds.width + expectedPadding * 2, background.getMapBoundsIncludingBorder().width);
		assertEquals(mapBounds.height + expectedPadding * 2, background.getMapBoundsIncludingBorder().height);

		Background realArtBackground = createBackground(createSettingsForRealArt(BorderPosition.Outside_map, borderWidth), mapBounds);
		int realArtPadding = realArtBackground.getBorderPaddingScaledByResolution();
		assertEquals(borderWidth - (int) Math.round(realArtInsetDepthFraction * borderWidth), realArtPadding);
		assertEquals(mapBounds.width + realArtPadding * 2, realArtBackground.getMapBoundsIncludingBorder().width);
	}

	@Test
	public void insideMapDoesNotShiftTheFrame()
	{
		final int borderWidth = 64;
		Dimension mapBounds = new Dimension(400, 300);
		Background background = createBackground(createSettings(BorderPosition.Inside_map, borderWidth), mapBounds);

		assertEquals(0, background.getBorderPaddingScaledByResolution());
		assertEquals(mapBounds.width, background.getMapBoundsIncludingBorder().width);
	}

	/**
	 * Draws a solid map through addBorder and reports the color of a pixel in a notch and of one beside it under solid art, both taken
	 * from the middle of the top edge band's innermost row.
	 */
	private Color[] drawAndSampleTopEdge(BorderPosition position, int borderWidth, Color mapColor)
	{
		Dimension mapBounds = new Dimension(400, 300);
		Background background = createBackground(createSettings(position, borderWidth), mapBounds);

		Image map = Image.create((int) mapBounds.width, (int) mapBounds.height, ImageType.RGB);
		try (Painter p = map.createPainter())
		{
			p.setColor(mapColor);
			p.fillRect(0, 0, (int) mapBounds.width, (int) mapBounds.height);
		}

		Image withBorder = background.addBorder(map);

		// The top edge band's innermost row, at a place the edge art tiles rather than a corner.
		int y = borderWidth - 1;
		int scaledNotchWidth = (int) Math.round(((double) notchWidth) * borderWidth / edgeArtHeight);
		int tileWidth = (int) Math.round(((double) edgeArtWidth) * borderWidth / edgeArtHeight);
		int tileStart = borderWidth + tileWidth;
		try (PixelReader pixels = withBorder.createPixelReader())
		{
			Color inNotch = pixels.getPixelColor(tileStart + scaledNotchWidth / 2, y);
			Color underArt = pixels.getPixelColor(tileStart + scaledNotchWidth + (tileWidth - scaledNotchWidth) / 2, y);
			return new Color[] { inNotch, underArt };
		}
	}

	/**
	 * A map with a transparent ocean is composited into the border with AlphaComposite.Src, so its transparent pixels punch through the
	 * backdrop. After the frame shifts inward that punching happens inside the border band, where the masked erase has to put opaque
	 * border background back everywhere the art does not show the map.
	 */
	@Test
	public void transparentMapPixelsOnlyPunchThroughWhereTheArtShowsTheMap()
	{
		final int borderWidth = 64;
		Dimension mapBounds = new Dimension(400, 300);
		Background background = createBackground(createSettings(BorderPosition.Outside_map, borderWidth), mapBounds);

		Image map = Image.create((int) mapBounds.width, (int) mapBounds.height, ImageType.ARGB);
		try (PixelWriter pixels = map.createPixelWriter())
		{
			for (int y = 0; y < mapBounds.height; y++)
			{
				for (int x = 0; x < mapBounds.width; x++)
				{
					pixels.setRGB(x, y, 0, 255, 0, 0);
				}
			}
		}

		Image withBorder = background.addBorder(map);

		int y = borderWidth - 1;
		int scaledNotchWidth = (int) Math.round(((double) notchWidth) * borderWidth / edgeArtHeight);
		int tileWidth = (int) Math.round(((double) edgeArtWidth) * borderWidth / edgeArtHeight);
		int tileStart = borderWidth + tileWidth;
		try (PixelReader pixels = withBorder.createPixelReader())
		{
			assertEquals(0, pixels.getAlpha(tileStart + scaledNotchWidth / 2, y), "A notch should show the map's transparency");
			assertEquals(255, pixels.getAlpha(tileStart + scaledNotchWidth + (tileWidth - scaledNotchWidth) / 2, y),
					"Solid art should keep the border background opaque");
			assertEquals(255, pixels.getAlpha(tileStart + scaledNotchWidth / 2, 0), "The outer part of the band should stay opaque");
		}
	}

	@Test
	public void notchesShowTheMapAndTheRestStaysBorderColored()
	{
		Color mapColor = Color.create(0, 255, 0);
		for (BorderPosition position : BorderPosition.values())
		{
			Color[] samples = drawAndSampleTopEdge(position, 64, mapColor);
			assertEquals(mapColor.getRGB(), samples[0].getRGB(), "A notch should show the map with the border " + position);
			assertNotEquals(mapColor.getRGB(), samples[1].getRGB(), "Solid art should still cover the map with the border " + position);
		}

		assertRealArtLeavesNoBackdropAgainstTheMap(mapColor);
	}

	/**
	 * Every transparent pixel along the band's innermost row is a seed of the flood fill, so after the frame shifts inward every one of
	 * them should show the map. Any that shows the border color instead is a surviving piece of the moat this feature removes.
	 */
	private void assertRealArtLeavesNoBackdropAgainstTheMap(Color mapColor)
	{
		final int borderWidth = 100;
		// Wide enough that a useful run of the top edge remains between this border's inset corners.
		Dimension mapBounds = new Dimension(900, 600);
		MapSettings settings = createSettingsForRealArt(BorderPosition.Outside_map, borderWidth);
		Background background = createBackground(settings, mapBounds);

		Image map = Image.create((int) mapBounds.width, (int) mapBounds.height, ImageType.RGB);
		try (Painter p = map.createPainter())
		{
			p.setColor(mapColor);
			p.fillRect(0, 0, (int) mapBounds.width, (int) mapBounds.height);
		}

		Image withBorder = background.addBorder(map);

		int innermostRowOfTheBand = borderWidth - 1;
		int mapPixelCount = 0;
		int borderColorPixelCount = 0;
		try (PixelReader pixels = withBorder.createPixelReader())
		{
			// The middle of the image, which is well clear of the corners at these sizes.
			for (int x = withBorder.getWidth() * 3 / 10; x < withBorder.getWidth() * 7 / 10; x++)
			{
				int rgb = pixels.getPixelColor(x, innermostRowOfTheBand).getRGB();
				if (rgb == mapColor.getRGB())
				{
					mapPixelCount++;
				}
				else if (rgb == settings.borderColor.getRGB())
				{
					borderColorPixelCount++;
				}
			}
		}

		assertTrue(mapPixelCount > 0, "The scallops in " + realArtBorderName + " should show the map along the band's innermost row");
		assertEquals(0, borderColorPixelCount, "No flat backdrop should be left between " + realArtBorderName + " and the map");
	}
}
