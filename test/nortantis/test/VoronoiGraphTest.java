package nortantis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nortantis.platform.*;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.platform.awt.AwtFactory;

public class VoronoiGraphTest
{
	@BeforeAll
	public static void setUpBeforeClass() throws Exception
	{
		// Tell drawing code to use AWT.
		PlatformFactory.setInstance(new AwtFactory());
	}

	@Test
	public void drawTriangleElevationWithXAndYGradientTest()
	{
		Image image = Image.create(101, 101, ImageType.RGB);
		Corner corner1 = new Corner();
		corner1.loc = new Point(0, 0);
		corner1.elevation = 0.0;
		Corner corner2 = new Corner();
		corner2.elevation = 0.5;
		corner2.loc = new Point(100, 0);
		Center center = new Center(new Point(100, 100));
		center.elevation = 1.0;
		Painter p = image.createPainter();
		VoronoiGraph.drawTriangleElevation(p, corner1, corner2, center);
		try (PixelReader pixels = image.createPixelReader())
		{
			assertEquals(0, Color.create(pixels.getRGB((int) corner1.loc.x, (int) corner1.loc.y)).getBlue());
			assertEquals(125, Color.create(pixels.getRGB((int) corner2.loc.x - 1, (int) corner2.loc.y)).getBlue());
			assertEquals(251, Color.create(pixels.getRGB((int) center.loc.x - 1, (int) center.loc.y - 2)).getBlue());
		}
	}

	@Test
	public void drawTriangleElevationZeroXGradientTest()
	{
		Image image = Image.create(101, 101, ImageType.RGB);
		Corner corner1 = new Corner();
		corner1.loc = new Point(0, 0);
		corner1.elevation = 0.5;
		Corner corner2 = new Corner();
		corner2.elevation = 0.5;
		corner2.loc = new Point(50, 0);
		Center center = new Center(new Point(50, 100));
		center.elevation = 1.0;
		Painter p = image.createPainter();
		VoronoiGraph.drawTriangleElevation(p, corner1, corner2, center);
		try (PixelReader pixels = image.createPixelReader())
		{
			assertEquals((int) (corner1.elevation * 255), Color.create(pixels.getRGB((int) corner1.loc.x, (int) corner1.loc.y)).getBlue());
			assertEquals((int) (corner2.elevation * 255), Color.create(pixels.getRGB((int) corner2.loc.x - 1, (int) corner2.loc.y)).getBlue());
			assertEquals((int) (center.elevation * 253), Color.create(pixels.getRGB((int) center.loc.x - 1, (int) center.loc.y - 2)).getBlue());
		}
	}

	@Test
	public void drawTriangleElevationZeroYGradientTest()
	{
		Image image = Image.create(101, 101, ImageType.RGB);
		Corner corner1 = new Corner();
		corner1.loc = new Point(0, 0);
		corner1.elevation = 0.0;
		Corner corner2 = new Corner();
		corner2.elevation = 0.0;
		corner2.loc = new Point(0, 100);
		Center center = new Center(new Point(50, 100));
		center.elevation = 1.0;
		Painter p = image.createPainter();
		VoronoiGraph.drawTriangleElevation(p, corner1, corner2, center);
		try (PixelReader pixels = image.createPixelReader())
		{
			assertEquals((int) (corner1.elevation * 255), Color.create(pixels.getRGB((int) corner1.loc.x, (int) corner1.loc.y)).getBlue());
			assertEquals((int) (corner2.elevation * 255), Color.create(pixels.getRGB((int) corner2.loc.x, (int) corner2.loc.y)).getBlue());
			assertEquals((int) (center.elevation * 249), Color.create(pixels.getRGB((int) center.loc.x - 1, (int) center.loc.y - 1)).getBlue());
		}
	}

	/**
	 * Unit test for findHighestZ.
	 */
	@Test
	public void findHighestZTest()
	{
		Vector3D v1 = new Vector3D(0, 0, -3);
		Vector3D v2 = new Vector3D(0, 0, 1);
		Vector3D v3 = new Vector3D(0, 0, 2);

		List<Vector3D> list = Arrays.asList(v1, v2, v3);

		Collections.shuffle(list);

		assertEquals(v3, VoronoiGraph.findHighestZ(list.get(0), list.get(1), list.get(2)));
	}

}
