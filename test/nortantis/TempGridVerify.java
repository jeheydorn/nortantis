package nortantis;

import nortantis.editor.MapParts;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PixelReader;
import nortantis.platform.PixelWriter;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TEMPORARY visual verification of {@link WorldGraph#findClosestCenter}. Renders land vs water two independent ways and diffs them:
 * <ul>
 * <li>A - fills each center's polygon (the drawn ground truth): water = black, land = white.</li>
 * <li>B - calls findClosestCenter on every pixel and colors it by the returned center's isWater (same water = black, land = white).</li>
 * <li>diff - red wherever A and B disagree, otherwise the shared black/white value.</li>
 * </ul>
 * If findClosestCenter is correct, the only red is a thin one-pixel fringe along coastlines (polygon-fill anti-aliasing vs. the hard
 * per-pixel classification). A solid red blob means findClosestCenter returned the wrong center for a whole region - the bug this checks.
 */
public class TempGridVerify
{
	private static final String mapPath = "C:\\Users\\jehey\\Documents\\Mine\\FantasyMapCreator\\workspace\\nortantis\\unit test files\\map settings\\propertiesConversion_allTypesOfEdits.properties";
	private static final double resolution = 1.0; // Medium - the quality where the bug appeared.

	@BeforeAll
	public static void setUp() throws Exception
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
	}

	@Test
	public void verify() throws Exception
	{
		MapSettings settings = new MapSettings(mapPath);
		settings.resolution = resolution;

		MapParts mapParts = new MapParts();
		Image map = new MapCreator().createMap(settings, null, mapParts);
		map.close(); // Only the graph is needed.
		WorldGraph graph = mapParts.graph;

		int width = (int) graph.getWidth();
		int height = (int) graph.getHeight();
		String method = "method2_bestFirst";
		System.out.println("=== TempGridVerify: " + width + "x" + height + " @ resolution " + resolution + " (" + method + ") ===");

		// A: ground-truth land/water by filling the center polygons.
		Image polygonsImage = Image.create(width, height, ImageType.Grayscale8Bit);
		try (Painter p = polygonsImage.createPainter())
		{
			p.setColor(Color.white);
			p.fillRect(0, 0, width, height);
			graph.drawLandAndOceanBlackAndWhite(p, graph.centers, null);
		}
		polygonsImage.write("gridVerify_A_polygons.png");

		// B: land/water from findClosestCenter on every pixel.
		Image lookupImage = Image.create(width, height, ImageType.Grayscale8Bit);
		Point query = new Point(0, 0);
		try (PixelWriter lookupWriter = lookupImage.createPixelWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					query.x = x;
					query.y = y;
					Center center = graph.findClosestCenter(query);
					lookupWriter.setGrayLevel(x, y, center.isWater ? 0 : 255);
				}
			}
		}
		lookupImage.write("gridVerify_B_findClosestCenter.png");

		// diff: red where A and B disagree (threshold the polygon image at 128 so coastline anti-aliasing is ignored).
		Image diffImage = Image.create(width, height, ImageType.RGB);
		PixelReader polygonsReader = polygonsImage.createPixelReader();
		PixelReader lookupReader = lookupImage.createPixelReader();
		long diffCount = 0;
		try (PixelWriter diffWriter = diffImage.createPixelWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					boolean polygonsWater = polygonsReader.getGrayLevel(x, y) < 128;
					boolean lookupWater = lookupReader.getGrayLevel(x, y) < 128;
					if (polygonsWater != lookupWater)
					{
						diffWriter.setPixelColor(x, y, Color.red);
						diffCount++;
					}
					else
					{
						diffWriter.setPixelColor(x, y, lookupWater ? Color.black : Color.white);
					}
				}
			}
		}
		diffImage.write("gridVerify_diff_" + method + ".png");

		long total = (long) width * height;
		System.out.println("=== differing pixels: " + diffCount + " / " + total + " (" + String.format("%.4f", 100.0 * diffCount / total) + "%) ===");
		System.out.println("=== wrote gridVerify_diff_" + method + ".png (plus A_polygons and B_findClosestCenter) to the project root ===");

		polygonsImage.close();
		lookupImage.close();
		diffImage.close();
	}
}
