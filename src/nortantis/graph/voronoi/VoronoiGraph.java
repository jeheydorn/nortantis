package nortantis.graph.voronoi;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import nortantis.Biome;
import nortantis.MapSettings.LineStyle;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.nodename.as3delaunay.LineSegment;
import nortantis.graph.voronoi.nodename.as3delaunay.Voronoi;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.Transform;
import nortantis.util.Range;

/**
 * VoronoiGraph.java
 *
 * @author Connor
 */
public abstract class VoronoiGraph
{

	final public ArrayList<Edge> edges = new ArrayList<>();
	final public ArrayList<Corner> corners = new ArrayList<>();
	final public ArrayList<Center> centers = new ArrayList<>();
	public Rectangle bounds;
	final protected Random rand;
	protected Color OCEAN, RIVER, LAKE, BEACH;
	public NoisyEdges noisyEdges;
	/**
	 * This controls how many rivers there are. Bigger means more.
	 */
	private double riverDensity = 1.0 / 14.0;
	protected double resolutionScale;
	public static final int riversThisSizeOrSmallerWillNotBeDrawn = 2;

	final static double verySmall = 0.0000001;
	double pointPrecision;

	/**
	 * @param r
	 *            Random number generator
	 * @param resolutionScale
	 *            Used to scale the graph larger smaller according to the resolution being used.
	 * @param pointPrecision
	 *            Used to determine when points should be considered duplicates. Larger numbers mean less duplicate detection, making tiny
	 *            polygons more likely. This number will be scaled by scaleMultiplyer.
	 */
	public VoronoiGraph(Random r, double resolutionScale, double pointPrecision)
	{
		this.rand = r;
		bumps = r.nextInt(5) + 1;
		this.resolutionScale = resolutionScale;
		startAngle = r.nextDouble() * 2 * Math.PI;
		dipAngle = r.nextDouble() * 2 * Math.PI;
		dipWidth = r.nextDouble() * .5 + .2;
		this.pointPrecision = pointPrecision;
	}

	public void initVoronoiGraph(Voronoi v, int numLloydRelaxations, double lloydRelaxationsScale, boolean createElevationRiversAndBiomes)
	{
		bounds = v.get_plotBounds();
		if (lloydRelaxationsScale > 0.0)
		{
			for (int i = 0; i < numLloydRelaxations; i++)
			{
				ArrayList<Point> points = v.siteCoords();
				for (Point p : points)
				{
					ArrayList<Point> region = v.region(p);
					double x = 0;
					double y = 0;
					for (Point c : region)
					{
						x += c.x * lloydRelaxationsScale;
						y += c.y * lloydRelaxationsScale;
					}
					x /= region.size() * lloydRelaxationsScale;
					y /= region.size() * lloydRelaxationsScale;
					p.x = x;
					p.y = y;
				}
				v = new Voronoi(points, v.get_plotBounds());
			}
		}
		buildGraph(v);
		improveCorners();
		storeOriginalCornerLocations();
		assignBorderToCenters();
		setupRandomSeeds(rand);

		if (createElevationRiversAndBiomes)
		{
			assignCornerElevations();
			assignPolygonElevations();
			assignOceanCoastAndLand();

			createRivers();
			assignCornerMoisture();
			redistributeMoisture(landCorners());
			assignPolygonMoisture();
			assignBiomes();
		}
	}

	private void setupRandomSeeds(Random rand)
	{
		for (Center c : centers)
		{
			c.treeSeed = rand.nextLong();
		}

		for (Edge e : edges)
		{
			e.noisyEdgesSeed = rand.nextLong();
			;
		}
	}

	private void storeOriginalCornerLocations()
	{
		for (Corner c : corners)
		{
			c.originalLoc = c.loc;
		}
	}

	private void assignBorderToCenters()
	{
		for (Center c1 : centers)
		{
			for (final Corner corner : c1.corners)
			{
				if (corner.isBorder)
				{
					c1.isBorder = true;
					break;
				}
			}
		}

	}

	abstract protected Biome getBiome(Center p);

	abstract protected Color getColor(Biome biome);

	/* an additional smoothing method across corners */
	private void improveCorners()
	{
		Point[] newP = new Point[corners.size()];
		for (Corner c : corners)
		{
			if (c.isBorder)
			{
				newP[c.index] = c.loc;
			}
			else
			{
				double x = 0;
				double y = 0;
				for (Center center : c.touches)
				{
					x += center.loc.x;
					y += center.loc.y;
				}
				newP[c.index] = new Point(x / c.touches.size(), y / c.touches.size());
			}
		}
		for (Corner c : corners)
		{
			c.loc = newP[c.index];
		}
		for (Edge e : edges)
		{
			if (e.v0 != null && e.v1 != null)
			{
				e.setVornoi(e.v0, e.v1);
			}
		}
	}

	private Edge edgeWithCenters(Center c1, Center c2)
	{
		for (Edge e : c1.borders)
		{
			if (e.d0 == c2 || e.d1 == c2)
			{
				return e;
			}
		}
		return null;
	}

	public static Edge edgeWithCorners(Corner c1, Corner c2)
	{
		for (Edge e : c1.protrudes)
		{
			if (e.v0 == c2 || e.v1 == c2)
			{
				return e;
			}
		}
		return null;
	}

	private static void drawTriangle(Painter p, Corner c1, Corner c2, Center center)
	{
		int[] x = new int[3];
		int[] y = new int[3];
		x[0] = (int) center.loc.x;
		y[0] = (int) center.loc.y;
		x[1] = (int) c1.loc.x;
		y[1] = (int) c1.loc.y;
		x[2] = (int) c2.loc.x;
		y[2] = (int) c2.loc.y;

		p.fillPolygon(x, y);
	}

	private static void drawTriangleElevation(Painter p, Corner c1, Corner c2, Center center)
	{
		Vector3D v1 = new Vector3D(c1.loc.x, c1.loc.y, c1.elevation);
		Vector3D v2 = new Vector3D(c2.loc.x, c2.loc.y, c2.elevation);
		Vector3D v3 = new Vector3D(center.loc.x, center.loc.y, center.elevation);

		// Normal of the plane containing the triangle
		Vector3D N = v2.subtract(v1).crossProduct(v3.subtract(v1));

		Vector3D highestPoint = findHighestZ(v1, v2, v3);
		int highestPointGrayLevel = (int) (highestPoint.getZ() * 255);
		Color highestPointColor = Color.create(highestPointGrayLevel, highestPointGrayLevel, highestPointGrayLevel);

		// Gradient of x and y with respect to z.
		Vector3D G = new Vector3D(-N.getX() / N.getZ(), -N.getY() / N.getZ(), 0);

		if ((Math.abs(G.getX()) < verySmall || Double.isInfinite(G.getX()) || Double.isNaN(G.getX())) && Math.abs(G.getY()) < verySmall
				|| Double.isInfinite(G.getY()) || Double.isNaN(G.getY()))
		{
			// The triangle is either flat or vertical.
			int grayLevel = (int) (255 * center.elevation);
			p.setColor(Color.create(grayLevel, grayLevel, grayLevel));
			drawTriangle(p, c1, c2, center);
			return;
		}

		Vector3D zIntercept = findZIntersectionWithXYPlain(highestPoint, G);

		p.setGradient((float) highestPoint.getX(), (float) highestPoint.getY(), highestPointColor, (float) zIntercept.getX(),
				(float) zIntercept.getY(), Color.black);
		drawTriangle(p, c1, c2, center);
	}

	private static Vector3D findZIntersectionWithXYPlain(Vector3D point, Vector3D gradient)
	{
		// To calculate this, I first had that
		// deltaZ = a*deltaX + b*deltaY
		// where delta* variables are the change in x, y, and z, and a and b are
		// the x and y components of the gradient.
		// I then constrained deltaX and delta Y by requiring them to follow the
		// gradient, so
		// deltaY = (a/b)*deltaX.
		// Plugging that into the first equation and solving for delta X, I got
		// the equation below for x. The equation for y is very similar.
		// Note that deltaZ = -point.getZ() to find the point where z is 0.

		double xChange = gradient.getX() * (-point.getZ()) / (gradient.getX() * gradient.getX() + gradient.getY() * gradient.getY());
		double yChange = gradient.getY() * (-point.getZ()) / (gradient.getX() * gradient.getX() + gradient.getY() * gradient.getY());
		return new Vector3D(point.getX() + xChange, point.getY() + yChange, 0.0);
	}

	private static Vector3D findHighestZ(Vector3D v1, Vector3D v2, Vector3D v3)
	{
		if (v1.getZ() > v2.getZ())
		{
			if (v1.getZ() > v3.getZ())
			{
				return v1;
			}
			return v3;
		}
		else
		{
			if (v2.getZ() > v3.getZ())
			{
				return v2;
			}
			return v3;
		}
	}

	public static void runPrivateUnitTests()
	{
		findHighestZTest();
		drawTriangleElevationZeroXGradientTest();
		drawTriangleElevationZeroYGradientTest();
		drawTriangleElevationWithXAndYGradientTest();
	}

	private static void drawTriangleElevationWithXAndYGradientTest()
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
		drawTriangleElevation(p, corner1, corner2, center);
		assertEquals(0, Color.create(image.getRGB((int) corner1.loc.x, (int) corner1.loc.y)).getBlue());
		assertEquals(125, Color.create(image.getRGB((int) corner2.loc.x - 1, (int) corner2.loc.y)).getBlue());
		assertEquals(251, Color.create(image.getRGB((int) center.loc.x - 1, (int) center.loc.y - 2)).getBlue());
	}

	private static void drawTriangleElevationZeroXGradientTest()
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
		drawTriangleElevation(p, corner1, corner2, center);
		assertEquals((int) (corner1.elevation * 255), Color.create(image.getRGB((int) corner1.loc.x, (int) corner1.loc.y)).getBlue());
		assertEquals((int) (corner2.elevation * 255), Color.create(image.getRGB((int) corner2.loc.x - 1, (int) corner2.loc.y)).getBlue());
		assertEquals((int) (center.elevation * 253), Color.create(image.getRGB((int) center.loc.x - 1, (int) center.loc.y - 2)).getBlue());
	}

	private static void drawTriangleElevationZeroYGradientTest()
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
		drawTriangleElevation(p, corner1, corner2, center);
		assertEquals((int) (corner1.elevation * 255), Color.create(image.getRGB((int) corner1.loc.x, (int) corner1.loc.y)).getBlue());
		assertEquals((int) (corner2.elevation * 255), Color.create(image.getRGB((int) corner2.loc.x, (int) corner2.loc.y)).getBlue());
		assertEquals((int) (center.elevation * 249), Color.create(image.getRGB((int) center.loc.x - 1, (int) center.loc.y - 1)).getBlue());
	}

	/**
	 * Unit test for findHighestZ.
	 */
	private static void findHighestZTest()
	{
		Vector3D v1 = new Vector3D(0, 0, -3);
		Vector3D v2 = new Vector3D(0, 0, 1);
		Vector3D v3 = new Vector3D(0, 0, 2);

		List<Vector3D> list = Arrays.asList(v1, v2, v3);

		Collections.shuffle(list);

		assertEquals(v3, findHighestZ(list.get(0), list.get(1), list.get(2)));
	}

	private boolean closeEnough(double d1, double d2, double diff)
	{
		return Math.abs(d1 - d2) <= diff * resolutionScale;
	}

	/**
	 * Draw tectonic plates. For debugging
	 */
	public void drawPlates(Painter p)
	{
		for (Edge e : edges)
		{
			if (e.d0.tectonicPlate != e.d1.tectonicPlate && e.v0 != null && e.v1 != null)
			{
				p.setBasicStroke(1);
				p.setColor(Color.green);
				p.drawLine((int) e.v0.loc.x, (int) e.v0.loc.y, (int) e.v1.loc.x, (int) e.v1.loc.y);
			}
		}
	}

	/**
	 * For debugging
	 */
	public void drawDelaunay(Painter g)
	{
		g.setBasicStroke(1);
		g.setColor(Color.yellow);
		for (Edge e : edges)
		{
			g.drawLine((int) e.d0.loc.x, (int) e.d0.loc.y, (int) e.d1.loc.x, (int) e.d1.loc.y);
		}
	}
	
	public void drawEdgeDeluanay(Painter g, Edge e)
	{
		if (e.d0 == null || e.d1 == null)
		{
			return;
		}
		g.drawLine((int) e.d0.loc.x, (int) e.d0.loc.y, (int) e.d1.loc.x, (int) e.d1.loc.y);
	}

	/**
	 * For debugging
	 */
	public void drawVoronoi(Painter g, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		Transform orig = null;
		if (drawBounds != null)
		{
			orig = g.getTransform();
			g.translate(-drawBounds.x, -drawBounds.y);
		}

		g.setColor(Color.white);

		Collection<Corner> cornersToDraw = centersToDraw == null ? corners : getCornersFromCenters(centersToDraw);
		for (Corner c : cornersToDraw)
		{
			for (Corner adjacent : c.adjacent)
			{
				g.drawLine((int) c.loc.x, (int) c.loc.y, (int) adjacent.loc.x, (int) adjacent.loc.y);
			}
		}

		if (drawBounds != null)
		{
			g.setTransform(orig);
		}
	}

	protected Set<Corner> getCornersFromCenters(Collection<Center> centers)
	{
		Set<Corner> result = new HashSet<>();
		centers.stream().forEach(c -> result.addAll(c.corners));
		return result;
	}

	/**
	 * For debugging
	 */
	public void drawCorners(Painter g, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		Transform orig = null;
		if (drawBounds != null)
		{
			orig = g.getTransform();
			g.translate(-drawBounds.x, -drawBounds.y);
		}

		Collection<Corner> cornersToDraw = centersToDraw == null ? corners : getCornersFromCenters(centersToDraw);
		for (Corner c : cornersToDraw)
		{
			g.setColor(Color.pink);

			g.fillOval((int) (c.loc.x - 5 * resolutionScale), (int) (c.loc.y - 5 * resolutionScale), (int) (10 * resolutionScale),
					(int) (10 * resolutionScale));
		}

		if (drawBounds != null)
		{
			g.setTransform(orig);
		}
	}

	public void drawElevation(Painter g)
	{
		for (Center c : centers)
		{
			float grayLevel = (float) (float) c.elevation;
			g.setColor(Color.create(grayLevel, grayLevel, grayLevel));
			drawUsingTriangles(g, c, true);
		}
	}

	public void drawBiomes(Painter g)
	{
		drawPolygons(g, (center) ->
		{
			return getColor(center.biome);
		});
	}

	public void drawRivers(Painter p, Collection<Edge> edgesToDraw, Rectangle drawBounds, Color riverColor,
			boolean areRegionBoundariesVisible, Color regionBoundaryColor)
	{
		if (edgesToDraw == null)
		{
			edgesToDraw = edges;
		}

		Transform orig = null;
		if (drawBounds != null)
		{
			orig = p.getTransform();
			p.translate(-drawBounds.x, -drawBounds.y);
		}

		for (Edge e : edgesToDraw)
		{
			if (e.isRiver() && !e.isOceanOrLakeOrShore())
			{
				// If a river is also a region boundary, and region boundaries are visible, then draw the river with the region boundary
				// color.
				if (areRegionBoundariesVisible && e.isRegionBoundary())
				{
					p.setColor(regionBoundaryColor);
				}
				else
				{
					p.setColor(riverColor);
				}

				float currentWidth = calcRiverStrokeWidth(e);

				Edge fromEdge = null;
				if (e.v0 != null)
				{
					fromEdge = noisyEdges.findEdgeToFollow(e.v0, e);
				}
				float fromWidth = (fromEdge == null || !fromEdge.isRiver()) ? currentWidth : calcRiverStrokeWidth(fromEdge);

				Edge toEdge = null;
				if (e.v1 != null)
				{
					toEdge = noisyEdges.findEdgeToFollow(e.v1, e);
				}
				float toWidth = (toEdge == null || !toEdge.isRiver()) ? currentWidth : calcRiverStrokeWidth(toEdge);

				drawPathWithSmoothLineTransitions(p, noisyEdges.getNoisyEdge(e.index), fromWidth, currentWidth, toWidth);

			}
		}

		if (drawBounds != null)

		{
			p.setTransform(orig);
		}
	}

	private float calcRiverStrokeWidth(Edge e)
	{
		return (float) (resolutionScale * Math.sqrt(e.river * 0.5));
	}

	protected void drawUsingTriangles(Painter g, Center c, boolean drawElevation)
	{
		// Only used if Center c is on the edge of the graph. allows for
		// completely filling in the outer
		// polygons. This is a list because if c borders 2 edges, it may have 2
		// missing triangles.
		List<Corner> edgeCorners = null;

		c.area = 0;
		for (Center n : c.neighbors)
		{
			Edge e = edgeWithCenters(c, n);

			if (e.v0 == null)
			{
				// Outermost Voronoi edges aren't stored in the graph
				continue;
			}

			// find a corner on the exterior of the graph
			// if this Edge e has one, then it must have two,
			// finding these two corners will give us the missing
			// triangle to render. this special triangle is handled
			// outside this for loop
			// Joseph note: The above is wrong. It may be the case that
			// 1, or an odd number of corners touch the border.

			for (Corner cornerWithOneAdjacent : Arrays.asList(e.v0, e.v1))
			{
				if (cornerWithOneAdjacent.isBorder)
				{
					if (edgeCorners == null)
					{
						edgeCorners = new ArrayList<>();
					}

					// Check if the corner is already added.
					boolean found = false;
					for (Corner corner : edgeCorners)
					{
						if (corner.loc.equals(cornerWithOneAdjacent.loc))
						{
							found = true;
							break;
						}
					}

					if (!found)
						edgeCorners.add(cornerWithOneAdjacent);
				}
			}

			if (drawElevation)
			{
				drawTriangleElevation(g, e.v0, e.v1, c);
			}
			else
			{
				drawTriangle(g, e.v0, e.v1, c);
			}

			c.area += Math.abs(
					c.loc.x * (e.v0.loc.y - e.v1.loc.y) + e.v0.loc.x * (e.v1.loc.y - c.loc.y) + e.v1.loc.x * (c.loc.y - e.v0.loc.y)) / 2;
		}

		// Handle the missing triangles along borders.
		if (edgeCorners != null)
		{
			// I'm drawing a pairwise triangle for every pair of corners on a
			// border because it could be the case
			// that we have 2 corners on one border plus one corner on another.
			for (int i : new Range(edgeCorners.size()))
			{
				for (int j : new Range(i + 1, edgeCorners.size()))
				{
					Corner edgeCorner1 = edgeCorners.get(i);
					Corner edgeCorner2 = edgeCorners.get(j);

					if (edgeCorner2 == null)
					{
						// This happens when a corner is on a border, but there
						// is no other corner
						// in c.neighbors on that same border. This case does
						// not need to be drawn
						// differently.
						continue;
					}

					// If there is an edge from edgeCorner1 to edgeCorner2,
					// don't draw the triangle.
					// This case means that the edge goes from one border to
					// another, but there is
					// another center in the corner behind that edge.
					if (edgeCorner1.lookupEdgeFromCorner(edgeCorner2) != null)
						continue;

					// if these two outer corners are NOT on the same exterior
					// edge of the graph,
					// then we actually must render a polygon (w/ 4 points) and
					// take into consideration
					// one of the four corners (either 0,0 or 0,height or
					// width,0 or width,height)
					// note: the 'missing polygon' may have more than just 4
					// points. this
					// is common when the number of sites are quite low (less
					// than 5), but not a problem
					// with a more useful number of sites.

					if (closeEnough(edgeCorner1.loc.x, edgeCorner2.loc.x, 1) || closeEnough(edgeCorner1.loc.y, edgeCorner2.loc.y, 1))
					{
						// Both corners are on a single border.

						if (drawElevation)
						{
							drawTriangleElevation(g, edgeCorner1, edgeCorner2, c);
						}
						else
						{
							drawTriangle(g, edgeCorner1, edgeCorner2, c);
						}
					}
					else
					{
						int[] x = new int[4];
						int[] y = new int[4];
						x[0] = (int) c.loc.x;
						y[0] = (int) c.loc.y;
						x[1] = (int) edgeCorner1.loc.x;
						y[1] = (int) edgeCorner1.loc.y;

						// One of the corners of the graph is the next point. Determine which corner that is.
						x[2] = (int) (Math.abs(c.loc.x - bounds.x) < Math.abs(bounds.getRight() - c.loc.x) ? bounds.x : bounds.getRight());
						y[2] = (int) (Math.abs(c.loc.y - bounds.y) < Math.abs(bounds.getBottom() - c.loc.y) ? bounds.y
								: bounds.getBottom());

						x[3] = (int) edgeCorner2.loc.x;
						y[3] = (int) edgeCorner2.loc.y;

						if (drawElevation)
						{
							// I really should break the polygon into triangles
							// and call drawElevationOfTriangle on each, but for
							// now I'm just doing this.
							float grayLevel = (float) c.elevation;
							g.setColor(Color.create(grayLevel, grayLevel, grayLevel));
						}
						g.fillPolygon(x, y);
						c.area += 0; // TODOO: area of polygon given vertices
					}
				}
			}
		}

	}

	private void drawPathWithSmoothLineTransitions(Painter p, List<Point> path, float previousEdgeWidth, float currentEdgeWidth,
			float nextEdgeWidth)
	{
		if (path == null)
		{
			return;
		}

		if (path.size() < 2)
		{
			return;
		}

		float widthAtStart = (previousEdgeWidth + currentEdgeWidth) / 2f;
		float widthAtEnd = (nextEdgeWidth + currentEdgeWidth) / 2f;
		float previousWidth = widthAtStart;
		List<Point> pathSoFar = new ArrayList<Point>();
		pathSoFar.add(path.get(0));

		float pathLength = getPathLength(path);
		float lengthSoFar = 0;

		for (int i = 1; i < path.size(); i++)
		{
			float width;
			float distanceRatio = lengthSoFar / pathLength;
			if (distanceRatio < 0.5f)
			{
				float ratio = distanceRatio * 2f;
				width = (1f - ratio) * widthAtStart + ratio * currentEdgeWidth;
			}
			else
			{
				float ratio = distanceRatio - 0.5f;
				width = (1f - ratio) * currentEdgeWidth + ratio * widthAtEnd;
			}

			pathSoFar.add(path.get(i));
			if (width != previousWidth)
			{
				p.setBasicStroke(width);
				drawPolyline(p, pathSoFar);
				pathSoFar.add(path.get(i));
			}

			lengthSoFar += (float) path.get(i - 1).distanceTo(path.get(i));
		}

		if (pathSoFar.size() > 1)
		{
			p.setBasicStroke(widthAtEnd);
			drawPolyline(p, pathSoFar);
		}
	}

	private float getPathLength(List<Point> path)
	{
		if (path.size() < 2)
		{
			return 0;
		}

		float length = 0;
		for (int i = 1; i < path.size(); i++)
		{
			length += (float) path.get(i - 1).distanceTo(path.get(i));
		}

		return length;
	}

	public Set<Center> getCentersFromEdges(Collection<Edge> edges)
	{
		Set<Center> centers = new HashSet<Center>();
		for (Edge edge : edges)
		{
			if (edge.d0 != null)
			{
				centers.add(edge.d0);
			}

			if (edge.d1 != null)
			{
				centers.add(edge.d1);
			}
		}

		return centers;
	}

	public Set<Center> getCentersFromEdgeIds(Collection<Integer> edgeIds)
	{
		Set<Center> centers = new HashSet<Center>();
		for (Integer id : edgeIds)
		{
			Edge edge = edges.get(id);
			if (edge.d0 != null)
			{
				centers.add(edge.d0);
			}

			if (edge.d1 != null)
			{
				centers.add(edge.d1);
			}
		}

		return centers;
	}

	public Set<Edge> getEdgesFromCenters(Collection<Center> centers)
	{
		Set<Edge> edges = new HashSet<>();
		for (Center center : centers)
		{
			edges.addAll(center.borders);
		}
		return edges;
	}

	protected void drawSpecifiedEdges(Painter g, double strokeWidth, Collection<Center> centersToDraw, Rectangle drawBounds,
			Function<Edge, Boolean> shouldDraw)
	{
		if (centersToDraw == null)
		{
			centersToDraw = centers;
		}

		Transform orig = null;
		if (drawBounds != null)
		{
			orig = g.getTransform();
			g.translate(-drawBounds.x, -drawBounds.y);
		}

		Set<Edge> drawn = new HashSet<>();

		g.setBasicStroke((float) strokeWidth);

		for (final Center p : centersToDraw)
		{
			for (final Edge edge : p.borders)
			{
				if (!shouldDraw.apply(edge))
					continue;

				if (!drawn.contains(edge))
				{
					drawn.add(edge);
					drawEdge(g, edge);
				}
			}
		}

		if (drawBounds != null)
		{
			g.setTransform(orig);
		}
	}

	public void drawEdge(Painter p, Edge edge)
	{
		if (noisyEdges.getNoisyEdge(edge.index) == null)
		{
			// It's at the edge of the map, where we don't have
			// the noisy edges computed.
			return;
		}

		List<Point> path = noisyEdges.getNoisyEdge(edge.index);
		drawPolyline(p, path);
	}

	protected void drawPolyline(Painter p, List<Point> line)
	{
		int[] xPoints = new int[line.size()];
		int[] yPoints = new int[line.size()];
		for (int i : new Range(line.size()))
		{
			xPoints[i] = (int) line.get(i).x;
			yPoints[i] = (int) line.get(i).y;
		}
		p.drawPolyline(xPoints, yPoints);
	}

	/**
	 * Render the interior of polygons including noisy edges.
	 * 
	 * @param p
	 * @param colorChooser
	 *            Decides the color for each polygons. If it returns null, then the polygons will not be drawn.
	 */
	public void drawPolygons(Painter p, Function<Center, Color> colorChooser)
	{
		drawPolygons(p, centers, colorChooser);
	}

	public void drawPolygons(Painter p, Collection<Center> centersToRender, Rectangle drawBounds, Function<Center, Color> colorChooser)
	{
		Transform orig = null;
		if (drawBounds != null)
		{
			orig = p.getTransform();
			p.translate(-drawBounds.x, -drawBounds.y);
		}

		if (centersToRender == null)
		{
			drawPolygons(p, colorChooser);
		}
		else
		{
			drawPolygons(p, centersToRender, colorChooser);
		}

		if (drawBounds != null)
		{
			p.setTransform(orig);
		}
	}

	public void drawPolygons(Painter p, Collection<Center> centersToRender, Function<Center, Color> colorChooser)
	{
		// First I must draw border polygons without noisy edges because the
		// noisy edges don't exist on the borders.
		for (Center c : centersToRender)
		{
			if (c.isBorder)
			{
				Color color = colorChooser.apply(c);
				if (color != null)
				{
					p.setColor(color);
					drawUsingTriangles(p, c, false);
				}
			}
		}

		// Draw noisy edges.
		for (final Center c : centersToRender)
		{
			Color color = colorChooser.apply(c);
			if (color != null)
			{
				p.setColor(color);
				// I want to just draw using drawPolygon, but sometimes the graph has Centers with polygons that don't make sense, so I have
				// a bunch of checks to fall back to drawing piecewise.
				if (c.isWellFormedForDrawingPiecewise() || !c.isWellFormedForDrawingAsPolygon())
				{
					drawPolygonPiecewise(p, c);
				}
				else
				{
					drawPolygon(p, c);
				}
			}

		}
	}

	/**
	 * Fills in a polygon (a Center) by filling in the space between the polygon's center and each edge separately.
	 * 
	 * @param p
	 * @param c
	 */
	private void drawPolygonPiecewise(Painter p, Center c)
	{
		for (final Center r : c.neighbors)
		{
			Edge edge = lookupEdgeFromCenter(c, r);

			if (noisyEdges == null || noisyEdges.getNoisyEdge(edge.index) == null)
			{
				// This can happen if noisy edges haven't been created
				// yet or if the polygon is on the border.
				drawPieceWithoutNoisyEdges(p, edge, c);
			}
			else
			{
				dawPieceUsingNoisyEdges(p, edge, c);
			}
		}

	}

	private void drawPieceWithoutNoisyEdges(Painter p, Edge edge, Center c)
	{
		List<IntPoint> vertices = new ArrayList<>();
		vertices.add(new IntPoint((int) c.loc.x, (int) c.loc.y));
		if (edge.v0 != null)
			vertices.add(new IntPoint((int) edge.v0.loc.x, (int) edge.v0.loc.y));
		if (edge.v1 != null)
			vertices.add(new IntPoint((int) edge.v1.loc.x, (int) edge.v1.loc.y));
		p.fillPolygon(vertices);
		return;
	}

	private void dawPieceUsingNoisyEdges(Painter p, Edge edge, Center c)
	{
		List<Point> path = noisyEdges.getNoisyEdge(edge.index);
		List<IntPoint> vertices = new ArrayList<>();
		vertices.add(new IntPoint((int) c.loc.x, (int) c.loc.y));
		for (Point point : path)
		{
			vertices.add(new IntPoint((int) point.x, (int) point.y));
		}
		p.fillPolygon(vertices);
	}

	private void drawPolygon(Painter p, Center c)
	{
		List<Edge> edges = c.orderEdgesAroundCenter();
		List<Point> vertices = edgeListToDrawPoints(edges, false, 0.0);
		p.fillPolygonDouble(vertices);
	}
	
	public List<Point> edgeListToDrawPoints(List<Edge> edges)
	{
		return edgeListToDrawPoints(edges, false, 0);
	}

	public List<Point> edgeListToDrawPoints(List<Edge> edges, boolean ignoreNoisyEdges, double maxDistanceToIgnoreNoisyEdgesForJaggedLines)
	{
		if (edges.isEmpty())
		{
			return Collections.emptyList();
		}

		List<Point> result = new ArrayList<Point>();
		for (int i = 0; i < edges.size(); i++)
		{
			Edge current = edges.get(i);
			if (current.v0 == null || current.v1 == null)
			{
				continue;
			}

			boolean reverse;
			if (i == 0)
			{
				if (edges.size() == 1)
				{
					reverse = false;
				}
				else
				{
					Edge next = edges.get(i + 1);
					if (current.v0 == next.v0 || current.v0 == next.v1)
					{
						reverse = true;
					}
					else
					{
						reverse = false;
					}
				}
			}
			else
			{
				Edge prev = edges.get(i - 1);
				if (current.v1 == prev.v0 || current.v1 == prev.v1)
				{
					reverse = true;
				}
				else
				{
					reverse = false;
				}
			}

			addEdgePoints(result, current, reverse, ignoreNoisyEdges, maxDistanceToIgnoreNoisyEdgesForJaggedLines);
		}

		return result;
	}

	private void addEdgePoints(List<Point> points, Edge edge, boolean reverse, boolean ignoreNoisyEdges, double maxDistanceToIgnoreNoisyEdgesForJaggedLines)
	{
		List<Point> noisyEdge = noisyEdges.getNoisyEdge(edge.index);
		if (noisyEdge == null || (ignoreNoisyEdges && (noisyEdges.getLineStyle() != LineStyle.Jagged || edge.v1.loc.distanceTo(edge.v0.loc) <= maxDistanceToIgnoreNoisyEdgesForJaggedLines)))
		{
			if (reverse)
			{
				addPointIfNotSameAsLast(points, edge.v1.loc);
				addPointIfNotSameAsLast(points, edge.v0.loc);
			}
			else
			{
				addPointIfNotSameAsLast(points, edge.v0.loc);
				addPointIfNotSameAsLast(points, edge.v1.loc);
			}
		}
		else
		{
			if (reverse)
			{
				noisyEdge = new ArrayList<>(noisyEdge);
				Collections.reverse(noisyEdge);
			}
			points.addAll(noisyEdge);
		}
	}

	public List<Point> edgeListToDrawPointsDelaunay(List<Edge> edges)
	{
		if (edges.isEmpty())
		{
			return Collections.emptyList();
		}

		List<Point> result = new ArrayList<Point>();
		for (int i = 0; i < edges.size(); i++)
		{
			Edge current = edges.get(i);
			if (current.d0 == null || current.d1 == null)
			{
				continue;
			}

			boolean reverse;
			if (i == 0)
			{
				if (edges.size() == 1)
				{
					reverse = false;
				}
				else
				{
					Edge next = edges.get(i + 1);
					if (current.d0 == next.d0 || current.d0 == next.d1)
					{
						reverse = true;
					}
					else
					{
						reverse = false;
					}
				}
			}
			else
			{
				Edge prev = edges.get(i - 1);
				if (current.d1 == prev.d0 || current.d1 == prev.d1)
				{
					reverse = true;
				}
				else
				{
					reverse = false;
				}
			}

			addEdgePointsDelaunay(result, current, reverse);
		}

		return result;
	}

	private void addEdgePointsDelaunay(List<Point> points, Edge edge, boolean reverse)
	{
		if (reverse)
		{
			addPointIfNotSameAsLast(points, edge.d1.loc);
			addPointIfNotSameAsLast(points, edge.d0.loc);
		}
		else
		{
			addPointIfNotSameAsLast(points, edge.d0.loc);
			addPointIfNotSameAsLast(points, edge.d1.loc);
		}
	}
	
	private void addPointIfNotSameAsLast(List<Point> points, Point toAdd)
	{
		if (points.isEmpty())
		{
			points.add(toAdd);
		}
		else if (!points.get(points.size() - 1).equals(toAdd))
		{
			points.add(toAdd);
		}
	}

	// Look up a Voronoi Edge object given two adjacent Voronoi
	// polygons, or two adjacent Voronoi corners
	public Edge lookupEdgeFromCenter(Center p, Center r)
	{
		for (Edge edge : p.borders)
		{
			if (edge.d0 == r || edge.d1 == r)
				return edge;
		}
		return null;
	}

	private void buildGraph(Voronoi v)
	{
		final HashMap<Point, Center> pointCenterMap = new HashMap<>();
		final ArrayList<Point> points = v.siteCoords();
		for (Point p : points)
		{
			Center c = new Center();
			c.loc = p;
			c.index = centers.size();
			centers.add(c);
			pointCenterMap.put(p, c);
		}

		// bug fix
		for (Center c : centers)
		{
			v.region(c.loc);
		}

		final ArrayList<nortantis.graph.voronoi.nodename.as3delaunay.Edge> libedges = v.edges();
		final TreeMap<Point, Corner> pointCornerMap = new TreeMap<>();

		for (nortantis.graph.voronoi.nodename.as3delaunay.Edge libedge : libedges)
		{
			final LineSegment vEdge = libedge.voronoiEdge();
			final LineSegment dEdge = libedge.delaunayLine();

			final Edge edge = new Edge();
			edge.index = edges.size();
			edges.add(edge);

			edge.v0 = makeCorner(pointCornerMap, vEdge.p0);
			edge.v1 = makeCorner(pointCornerMap, vEdge.p1);
			edge.d0 = pointCenterMap.get(dEdge.p0);
			edge.d1 = pointCenterMap.get(dEdge.p1);
			if (edge.v0 == edge.v1)
			{
				// Zero length edges are worthless because they can't be drawn, so don't hook them up to the rest of the graph. Ideally, I
				// would just throw them away, but that would break backwards compatibility with previously existing map edits because it
				// would shift the edge indexes of subsequent edges.
				continue;
			}

			// Centers point to edges. Corners point to edges.
			if (edge.d0 != null)
			{
				edge.d0.borders.add(edge);
			}
			if (edge.d1 != null)
			{
				edge.d1.borders.add(edge);
			}
			if (edge.v0 != null)
			{
				edge.v0.protrudes.add(edge);
			}
			if (edge.v1 != null)
			{
				edge.v1.protrudes.add(edge);
			}

			// Centers point to centers.
			if (edge.d0 != null && edge.d1 != null)
			{
				addToCenterList(edge.d0.neighbors, edge.d1);
				addToCenterList(edge.d1.neighbors, edge.d0);
			}

			// Corners point to corners
			if (edge.v0 != null && edge.v1 != null)
			{
				addToCornerList(edge.v0.adjacent, edge.v1);
				addToCornerList(edge.v1.adjacent, edge.v0);
			}

			// Centers point to corners
			if (edge.d0 != null)
			{
				addToCornerList(edge.d0.corners, edge.v0);
				addToCornerList(edge.d0.corners, edge.v1);
			}
			if (edge.d1 != null)
			{
				addToCornerList(edge.d1.corners, edge.v0);
				addToCornerList(edge.d1.corners, edge.v1);
			}

			// Corners point to centers
			if (edge.v0 != null)
			{
				addToCenterList(edge.v0.touches, edge.d0);
				addToCenterList(edge.v0.touches, edge.d1);
			}
			if (edge.v1 != null)
			{
				addToCenterList(edge.v1.touches, edge.d0);
				addToCenterList(edge.v1.touches, edge.d1);
			}
		}
	}

	// Helper functions for the following for loop; ideally these
	// would be inlined
	private void addToCornerList(ArrayList<Corner> list, Corner c)
	{
		if (c != null && !list.contains(c))
		{
			list.add(c);
		}
	}

	private void addToCenterList(ArrayList<Center> list, Center c)
	{
		if (c != null && !list.contains(c))
		{
			list.add(c);
		}
	}

	// ensures that each corner is represented by only one corner object
	private Corner makeCorner(TreeMap<Point, Corner> pointCornerMap, Point p)
	{
		if (p == null)
		{
			return null;
		}
		// Joseph note: I changed this function to use a TreeMap and
		// sizeMultiplier
		// so that the graph won't have small changes when drawn at higher
		// resolutions.

		// As pointPrecision becomes larger, points become less likely to be
		// merged. I added this because of a bug
		// where corners on the border of the graph which were needed to draw
		// the polygons on the border were disappearing,
		// causing the background color to be shown

		// This magic number exists because I originally designed point precision to be based on
		// 'Size multiplier', which is an old number used to determined at what scale to draw things.
		// When a map is at 1.0 resolution scale, size multiplier use to be (8.0 / 3.0). I've since
		// changed it, but it needs to remain (8.0 / 3.0) here for backwards compatibility.
		// Now graphs are created at a constant size, so that isn't necessary for new maps,
		// but is still necessary for backwards compatibility with older maps.
		final double scaleForBackwardsCompatibility = (8.0 / 3.0);

		Point key = new Point((int) (p.x / scaleForBackwardsCompatibility) * pointPrecision,
				(int) (p.y / scaleForBackwardsCompatibility) * pointPrecision);
		Corner c = pointCornerMap.get(key);
		if (c == null)
		{
			c = new Corner();
			c.loc = p;
			c.isBorder = bounds.liesOnAxes(p, scaleForBackwardsCompatibility);
			c.index = corners.size();
			corners.add(c);
			pointCornerMap.put(key, c);
		}
		return c;
	}

	protected abstract void assignCornerElevations();

	double[][] noise;
	double ISLAND_FACTOR = 1.07; // 1.0 means no small islands; 2.0 leads to a
									// lot
	final int bumps;
	final double startAngle;
	final double dipAngle;
	final double dipWidth;

	protected abstract void assignOceanCoastAndLand();

	private ArrayList<Corner> landCorners()
	{
		final ArrayList<Corner> list = new ArrayList<>();
		for (Corner c : corners)
		{
			if (!c.isOcean && !c.isCoast)
			{
				list.add(c);
			}
		}
		return list;
	}

	protected double maxElevation = 0.0;

	private void assignPolygonElevations()
	{
		for (Center center : centers)
		{
			double total = 0;
			for (Corner c : center.corners)
			{
				total += c.elevation;
			}
			center.elevation = total / center.corners.size();
			if (center.elevation > maxElevation)
			{
				maxElevation = center.elevation;
			}
		}
	}

	private void createRivers()
	{
		for (int i = 0; i < corners.size() * riverDensity; i++)
		{
			int index = rand.nextInt(corners.size());
			Corner c = corners.get(index);
			c.createRivers();
		}
	}

	private void assignCornerMoisture()
	{
		LinkedList<Corner> queue = new LinkedList<>();
		for (Corner c : corners)
		{
			if ((c.isWater || c.river > 2) && !c.isOcean)
			{
				c.moisture = c.river > 2 ? Math.min(3.0, (0.05 * c.river)) : 1.0;
				queue.push(c);
			}
			else
			{
				c.moisture = 0.0;
			}
		}

		while (!queue.isEmpty())
		{
			Corner c = queue.pop();
			for (Corner a : c.adjacent)
			{
				double newM = .9 * c.moisture;
				if (newM > a.moisture)
				{
					a.moisture = newM;
					queue.add(a);
				}
			}
		}

		// Salt water
		for (Corner c : corners)
		{
			if (c.isOcean || c.isCoast)
			{
				c.moisture = 1.0;
			}
		}
	}

	private void redistributeMoisture(ArrayList<Corner> landCorners)
	{
		Collections.sort(landCorners, new Comparator<Corner>()
		{
			@Override
			public int compare(Corner o1, Corner o2)
			{
				if (o1.moisture > o2.moisture)
				{
					return 1;
				}
				else if (o1.moisture < o2.moisture)
				{
					return -1;
				}
				return 0;
			}
		});
		for (int i = 0; i < landCorners.size(); i++)
		{
			landCorners.get(i).moisture = (double) i / landCorners.size();
		}
	}

	private void assignPolygonMoisture()
	{
		for (Center center : centers)
		{
			double total = 0;
			for (Corner c : center.corners)
			{
				total += c.moisture;
			}
			center.moisture = total / center.corners.size();
		}
	}

	private void assignBiomes()
	{
		for (Center center : centers)
		{
			center.biome = getBiome(center);
		}
	}
}
