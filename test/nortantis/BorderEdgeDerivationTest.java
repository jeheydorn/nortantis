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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A border only has to ship one edge image; the other three are produced from it by flipping and reflecting. This checks that whichever
 * edge a border ships, all four sides end up with their map-facing side against the map.
 *
 * The art is generated rather than stored: each band gets a thick dark rail along its outer side and a thin bright rail along its
 * map-facing side, so the assertions are a direct reading of which side ended up where. The same art is worth having in an art pack when
 * looking at this by eye, because a wrongly derived edge also puts its arrow motif pointing away from the map.
 */
public class BorderEdgeDerivationTest
{
	private static final int bandWidth = 128;
	private static final int tileLength = 256;
	private static final int outerRailThickness = 18;
	private static final int innerRailThickness = 10;

	@TempDir
	Path artPackFolder;

	@BeforeAll
	public static void setUpBeforeClass()
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
	}

	private static Color outerRailColor()
	{
		return Color.create(35, 35, 45);
	}

	private static Color innerRailColor()
	{
		return Color.create(245, 200, 40);
	}

	/**
	 * Which side of an edge image faces the map. A top edge faces the map with its bottom side, a left edge with its right side, and so
	 * on.
	 */
	private enum MapFacingSide
	{
		Top, Bottom, Left, Right
	}

	/**
	 * Sets a pixel given in motif space, where u runs along the band and v runs from the outer side (0) to the map-facing side
	 * (bandWidth - 1), whichever sides of the image those are for the given orientation.
	 */
	private static void setMotifPixel(PixelWriter pixels, MapFacingSide mapFacingSide, int u, int v, Color color)
	{
		switch (mapFacingSide)
		{
			case Bottom:
				pixels.setRGB(u, v, color.getRed(), color.getGreen(), color.getBlue(), 255);
				break;
			case Top:
				pixels.setRGB(u, bandWidth - 1 - v, color.getRed(), color.getGreen(), color.getBlue(), 255);
				break;
			case Right:
				pixels.setRGB(v, u, color.getRed(), color.getGreen(), color.getBlue(), 255);
				break;
			case Left:
				pixels.setRGB(bandWidth - 1 - v, u, color.getRed(), color.getGreen(), color.getBlue(), 255);
				break;
		}
	}

	private static Image createEdge(MapFacingSide mapFacingSide)
	{
		boolean isHorizontalBand = mapFacingSide == MapFacingSide.Bottom || mapFacingSide == MapFacingSide.Top;
		Image edge = Image.create(isHorizontalBand ? tileLength : bandWidth, isHorizontalBand ? bandWidth : tileLength, ImageType.ARGB);
		try (PixelWriter pixels = edge.createPixelWriter())
		{
			for (int u = 0; u < tileLength; u++)
			{
				for (int v = 0; v < outerRailThickness; v++)
				{
					setMotifPixel(pixels, mapFacingSide, u, v, outerRailColor());
				}
				for (int v = bandWidth - innerRailThickness; v < bandWidth; v++)
				{
					setMotifPixel(pixels, mapFacingSide, u, v, innerRailColor());
				}
			}

			// An arrow pointing from the outer rail toward the map, so a wrongly derived edge is obvious by eye as well.
			final int arrowBaseV = 30;
			final int arrowTipV = 112;
			final int arrowHalfWidth = 100;
			for (int v = arrowBaseV; v <= arrowTipV; v++)
			{
				double fractionOfTheWayToTheTip = ((double) (v - arrowBaseV)) / (arrowTipV - arrowBaseV);
				int halfWidth = (int) Math.round(arrowHalfWidth * (1.0 - fractionOfTheWayToTheTip));
				for (int u = tileLength / 2 - halfWidth; u <= tileLength / 2 + halfWidth; u++)
				{
					setMotifPixel(pixels, mapFacingSide, u, v, Color.create(205, 45, 45));
				}
			}
		}
		return edge;
	}

	/**
	 * An upper-left corner carrying the same dark rail along its two outer sides, so the corners are never the thing under test.
	 */
	private static Image createUpperLeftCorner()
	{
		Image corner = Image.create(bandWidth, bandWidth, ImageType.ARGB);
		try (PixelWriter pixels = corner.createPixelWriter())
		{
			for (int y = 0; y < bandWidth; y++)
			{
				for (int x = 0; x < bandWidth; x++)
				{
					if (y < outerRailThickness || x < outerRailThickness)
					{
						Color color = outerRailColor();
						pixels.setRGB(x, y, color.getRed(), color.getGreen(), color.getBlue(), 255);
					}
				}
			}
		}
		return corner;
	}

	private void writeBorder(String borderName, String edgeFileName, MapFacingSide mapFacingSide) throws Exception
	{
		Path folder = artPackFolder.resolve("borders").resolve(borderName);
		Files.createDirectories(folder);
		createEdge(mapFacingSide).write(folder.resolve(edgeFileName).toString());
		createUpperLeftCorner().write(folder.resolve("upper_left_corner.png").toString());
	}

	private Image drawMapWithBorder(String borderName, int borderWidth, Dimension mapBounds)
	{
		MapSettings settings = new MapSettings();
		settings.solidColorBackground = true;
		settings.resolution = 1.0;
		settings.landColor = Color.create(120, 120, 120);
		settings.oceanColor = Color.create(120, 120, 120);
		settings.borderColorOption = BorderColorOption.Choose_color;
		settings.borderColor = Color.create(250, 250, 250);
		settings.drawBorder = true;
		settings.borderWidth = borderWidth;
		settings.borderPosition = BorderPosition.Outside_map;
		settings.borderResource = new NamedResource(Assets.customArtPack, borderName);
		settings.customImagesPath = artPackFolder.toString();

		Background background = new Background(settings, mapBounds, new WarningLogger()
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

		Image map = Image.create((int) mapBounds.width, (int) mapBounds.height, ImageType.RGB);
		try (Painter p = map.createPainter())
		{
			p.setColor(settings.landColor);
			p.fillRect(0, 0, (int) mapBounds.width, (int) mapBounds.height);
		}
		return background.addBorder(map);
	}

	/**
	 * Whichever single edge image a border ships, all four bands of the drawn frame must put their dark rail against the outside of the
	 * image and their bright rail against the map.
	 */
	@Test
	public void everyBandFacesTheMapWhicheverEdgeTheBorderShips() throws Exception
	{
		writeBorder("top only", "top_edge.png", MapFacingSide.Bottom);
		writeBorder("bottom only", "bottom_edge.png", MapFacingSide.Top);
		writeBorder("left only", "left_edge.png", MapFacingSide.Right);
		writeBorder("right only", "right_edge.png", MapFacingSide.Left);
		ImageCache.clear();

		final int borderWidth = bandWidth;
		Dimension mapBounds = new Dimension(700, 520);

		for (String borderName : List.of("top only", "bottom only", "left only", "right only"))
		{
			Image withBorder = drawMapWithBorder(borderName, borderWidth, mapBounds);
			int width = withBorder.getWidth();
			int height = withBorder.getHeight();
			// Points in the middle of each band, well clear of the corners.
			int middleX = width / 2;
			int middleY = height / 2;

			try (PixelReader pixels = withBorder.createPixelReader())
			{
				assertRail(pixels, middleX, 0, outerRailColor(), borderName, "the top band's outer side");
				assertRail(pixels, middleX, borderWidth - 1, innerRailColor(), borderName, "the top band's map-facing side");

				assertRail(pixels, middleX, height - 1, outerRailColor(), borderName, "the bottom band's outer side");
				assertRail(pixels, middleX, height - borderWidth, innerRailColor(), borderName, "the bottom band's map-facing side");

				assertRail(pixels, 0, middleY, outerRailColor(), borderName, "the left band's outer side");
				assertRail(pixels, borderWidth - 1, middleY, innerRailColor(), borderName, "the left band's map-facing side");

				assertRail(pixels, width - 1, middleY, outerRailColor(), borderName, "the right band's outer side");
				assertRail(pixels, width - borderWidth, middleY, innerRailColor(), borderName, "the right band's map-facing side");
			}
		}
	}

	private static void assertRail(PixelReader pixels, int x, int y, Color expected, String borderName, String whichSide)
	{
		assertEquals(expected.getRGB(), pixels.getPixelColor(x, y).getRGB(), "Wrong rail on " + whichSide + " of the \"" + borderName + "\" border");
	}
}
