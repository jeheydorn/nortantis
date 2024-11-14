package nortantis.graph.voronoi.nodename.as3delaunay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import nortantis.geom.Point;
import nortantis.geom.Rectangle;

public final class Voronoi
{

	private SiteList _sites;
	private HashMap<Point, Site> _sitesIndexedByLocation;
	private ArrayList<Edge> _edges;
	// TODOO generalize this so it doesn't have to be a rectangle;
	// then we can make the fractal voronois-within-voronois
	private Rectangle _plotBounds;

	public Rectangle get_plotBounds()
	{
		return _plotBounds;
	}

	public Voronoi(ArrayList<Point> points, Rectangle plotBounds)
	{
		init(points, plotBounds);
		fortunesAlgorithm();
	}

	public Voronoi(int numSites, double maxWidth, double maxHeight, Random r)
	{
		ArrayList<Point> points = new ArrayList<Point>();

		for (int i = 0; i < numSites; i++)
		{
			points.add(new Point(r.nextDouble() * maxWidth, r.nextDouble() * maxHeight));
		}
		init(points, new Rectangle(0, 0, maxWidth, maxHeight));
		fortunesAlgorithm();
	}

	private void init(ArrayList<Point> points, Rectangle plotBounds)
	{
		_sites = new SiteList();
		_sitesIndexedByLocation = new HashMap<Point, Site>();
		addSites(points);
		_plotBounds = plotBounds;
		_edges = new ArrayList<Edge>();
	}

	private void addSites(ArrayList<Point> points)
	{
		int length = points.size();
		for (int i = 0; i < length; ++i)
		{
			addSite(points.get(i), i);
		}
	}

	private void addSite(Point p, int index)
	{
		double weight = Math.random() * 100;
		Site site = Site.create(p, index, weight);
		_sites.push(site);
		_sitesIndexedByLocation.put(p, site);
	}

	public ArrayList<Edge> edges()
	{
		return _edges;
	}

	public ArrayList<Point> region(Point p)
	{
		Site site = _sitesIndexedByLocation.get(p);
		if (site == null)
		{
			return new ArrayList<Point>();
		}
		return site.region(_plotBounds);
	}

	// TODOO: bug: if you call this before you call region(), something goes wrong :(
	public ArrayList<Point> neighborSitesForSite(Point coord)
	{
		ArrayList<Point> points = new ArrayList<Point>();
		Site site = _sitesIndexedByLocation.get(coord);
		if (site == null)
		{
			return points;
		}
		ArrayList<Site> sites = site.neighborSites();
		for (Site neighbor : sites)
		{
			points.add(neighbor.get_coord());
		}
		return points;
	}

	public ArrayList<Circle> circles()
	{
		return _sites.circles();
	}

	private ArrayList<Edge> selectEdgesForSitePoint(Point coord, ArrayList<Edge> edgesToTest)
	{
		ArrayList<Edge> filtered = new ArrayList<Edge>();

		for (Edge e : edgesToTest)
		{
			if (((e.get_leftSite() != null && e.get_leftSite().get_coord() == coord)
					|| (e.get_rightSite() != null && e.get_rightSite().get_coord() == coord)))
			{
				filtered.add(e);
			}
		}
		return filtered;

		/*
		 * function myTest(edge:Edge, index:int, vector:Vector.<Edge>):Boolean { return ((edge.leftSite && edge.leftSite.coord == coord) ||
		 * (edge.rightSite && edge.rightSite.coord == coord)); }
		 */
	}

	private ArrayList<LineSegment> visibleLineSegments(ArrayList<Edge> edges)
	{
		ArrayList<LineSegment> segments = new ArrayList<LineSegment>();

		for (Edge edge : edges)
		{
			if (edge.get_visible())
			{
				Point p1 = edge.get_clippedEnds().get(LR.LEFT);
				Point p2 = edge.get_clippedEnds().get(LR.RIGHT);
				segments.add(new LineSegment(p1, p2));
			}
		}

		return segments;
	}

	private ArrayList<LineSegment> delaunayLinesForEdges(ArrayList<Edge> edges)
	{
		ArrayList<LineSegment> segments = new ArrayList<LineSegment>();

		for (Edge edge : edges)
		{
			segments.add(edge.delaunayLine());
		}

		return segments;
	}

	public ArrayList<LineSegment> voronoiBoundaryForSite(Point coord)
	{
		return visibleLineSegments(selectEdgesForSitePoint(coord, _edges));
	}

	public ArrayList<LineSegment> delaunayLinesForSite(Point coord)
	{
		return delaunayLinesForEdges(selectEdgesForSitePoint(coord, _edges));
	}

	public ArrayList<LineSegment> voronoiDiagram()
	{
		return visibleLineSegments(_edges);
	}

	/*
	 * public ArrayList<LineSegment> delaunayTriangulation(keepOutMask:BitmapData = null) { return
	 * delaunayLinesForEdges(selectNonIntersectingEdges(keepOutMask, _edges)); }
	 */
	public ArrayList<LineSegment> hull()
	{
		return delaunayLinesForEdges(hullEdges());
	}

	private ArrayList<Edge> hullEdges()
	{
		ArrayList<Edge> filtered = new ArrayList<Edge>();

		for (Edge e : _edges)
		{
			if (e.isPartOfConvexHull())
			{
				filtered.add(e);
			}
		}

		return filtered;

		/*
		 * function myTest(edge:Edge, index:int, vector:Vector.<Edge>):Boolean { return (edge.isPartOfConvexHull()); }
		 */
	}

	public ArrayList<Point> hullPointsInOrder()
	{
		ArrayList<Edge> hullEdges = hullEdges();

		ArrayList<Point> points = new ArrayList<Point>();
		if (hullEdges.isEmpty())
		{
			return points;
		}

		EdgeReorderer reorderer = new EdgeReorderer(hullEdges, Site.class);
		hullEdges = reorderer.get_edges();
		ArrayList<LR> orientations = reorderer.get_edgeOrientations();
		reorderer.dispose();

		LR orientation;

		int n = hullEdges.size();
		for (int i = 0; i < n; ++i)
		{
			Edge edge = hullEdges.get(i);
			orientation = orientations.get(i);
			points.add(edge.site(orientation).get_coord());
		}
		return points;
	}

	/**
	 *
	 * @param proximityMap
	 *            a BitmapData whose regions are filled with the site index values; see PlanePointsCanvas::fillRegions()
	 * @param x
	 * @param y
	 * @return coordinates of nearest Site to (x, y)
	 *
	 */
	/*
	 * public Point nearestSitePoint(proximityMap:BitmapData,double x, double y) { return _sites.nearestSitePoint(proximityMap, x, y); }
	 */

	/**
	 * Get the center point of every site.
	 */
	public ArrayList<Point> siteCoords()
	{
		return _sites.siteCoords();
	}

	private void fortunesAlgorithm()
	{
		Site newSite, bottomSite, topSite, tempSite;
		Vertex v, vertex;
		Point newintstar = null;
		LR leftRight;
		Halfedge lbnd, rbnd, llbnd, rrbnd, bisector;
		Edge edge;

		Rectangle dataBounds = _sites.getSitesBounds();

		int sqrt_nsites = (int) Math.sqrt(_sites.get_length() + 4);
		HalfedgePriorityQueue heap = new HalfedgePriorityQueue(dataBounds.y, dataBounds.height, sqrt_nsites);
		EdgeList edgeList = new EdgeList(dataBounds.x, dataBounds.width, sqrt_nsites);
		ArrayList<Halfedge> halfEdges = new ArrayList<Halfedge>();
		ArrayList<Vertex> vertices = new ArrayList<Vertex>();

		Site bottomMostSite = _sites.next();
		newSite = _sites.next();
		int edgeIndex = 0;

		for (;;)
		{
			if (heap.empty() == false)
			{
				newintstar = heap.min();
			}

			if (newSite != null && (heap.empty() || compareByYThenX(newSite, newintstar) < 0))
			{
				/* new site is smallest */
				// trace("smallest: new site " + newSite);

				// Step 8:
				lbnd = edgeList.edgeListLeftNeighbor(newSite.get_coord()); // the Halfedge just to the left of newSite
				// trace("lbnd: " + lbnd);
				rbnd = lbnd.edgeListRightNeighbor; // the Halfedge just to the right
				// trace("rbnd: " + rbnd);
				bottomSite = rightRegion(lbnd, bottomMostSite); // this is the same as leftRegion(rbnd)
				// this Site determines the region containing the new site
				// trace("new Site is in region of existing site: " + bottomSite);

				// Step 9:
				edge = Edge.createBisectingEdge(bottomSite, newSite, edgeIndex++);
				// trace("new edge: " + edge);
				_edges.add(edge);

				bisector = Halfedge.create(edge, LR.LEFT);
				halfEdges.add(bisector);
				// inserting two Halfedges into edgeList constitutes Step 10:
				// insert bisector to the right of lbnd:
				edgeList.insert(lbnd, bisector);

				// first half of Step 11:
				if ((vertex = Vertex.intersect(lbnd, bisector)) != null)
				{
					vertices.add(vertex);
					heap.remove(lbnd);
					lbnd.vertex = vertex;
					lbnd.ystar = vertex.get_y() + newSite.dist(vertex);
					heap.insert(lbnd);
				}

				lbnd = bisector;
				bisector = Halfedge.create(edge, LR.RIGHT);
				halfEdges.add(bisector);
				// second Halfedge for Step 10:
				// insert bisector to the right of lbnd:
				edgeList.insert(lbnd, bisector);

				// second half of Step 11:
				if ((vertex = Vertex.intersect(bisector, rbnd)) != null)
				{
					vertices.add(vertex);
					bisector.vertex = vertex;
					bisector.ystar = vertex.get_y() + newSite.dist(vertex);
					heap.insert(bisector);
				}

				newSite = _sites.next();
			}
			else if (heap.empty() == false)
			{
				/* intersection is smallest */
				lbnd = heap.extractMin();
				llbnd = lbnd.edgeListLeftNeighbor;
				rbnd = lbnd.edgeListRightNeighbor;
				rrbnd = rbnd.edgeListRightNeighbor;
				bottomSite = leftRegion(lbnd, bottomMostSite);
				topSite = rightRegion(rbnd, bottomMostSite);
				// these three sites define a Delaunay triangle
				// (not actually using these for anything...)
				// _triangles.push(new Triangle(bottomSite, topSite, rightRegion(lbnd)));

				v = lbnd.vertex;
				v.setIndex();
				lbnd.edge.setVertex(lbnd.leftRight, v);
				rbnd.edge.setVertex(rbnd.leftRight, v);
				edgeList.remove(lbnd);
				heap.remove(rbnd);
				edgeList.remove(rbnd);
				leftRight = LR.LEFT;
				if (bottomSite.get_y() > topSite.get_y())
				{
					tempSite = bottomSite;
					bottomSite = topSite;
					topSite = tempSite;
					leftRight = LR.RIGHT;
				}
				edge = Edge.createBisectingEdge(bottomSite, topSite, edgeIndex);
				_edges.add(edge);
				bisector = Halfedge.create(edge, leftRight);
				halfEdges.add(bisector);
				edgeList.insert(llbnd, bisector);
				edge.setVertex(LR.other(leftRight), v);
				if ((vertex = Vertex.intersect(llbnd, bisector)) != null)
				{
					vertices.add(vertex);
					heap.remove(llbnd);
					llbnd.vertex = vertex;
					llbnd.ystar = vertex.get_y() + bottomSite.dist(vertex);
					heap.insert(llbnd);
				}
				if ((vertex = Vertex.intersect(bisector, rrbnd)) != null)
				{
					vertices.add(vertex);
					bisector.vertex = vertex;
					bisector.ystar = vertex.get_y() + bottomSite.dist(vertex);
					heap.insert(bisector);
				}
			}
			else
			{
				break;
			}
		}

		// heap should be empty now
		heap.dispose();
		edgeList.dispose();

		halfEdges.clear();

		// we need the vertices to clip the edges
		for (Edge e : _edges)
		{
			e.clipVertices(_plotBounds);
		}
		// but we don't actually ever use them again!
		for (Vertex v0 : vertices)
		{
			v0.dispose();
		}
		vertices.clear();
	}

	Site leftRegion(Halfedge he, Site bottomMostSite)
	{
		Edge edge = he.edge;
		if (edge == null)
		{
			return bottomMostSite;
		}
		return edge.site(he.leftRight);
	}

	Site rightRegion(Halfedge he, Site bottomMostSite)
	{
		Edge edge = he.edge;
		if (edge == null)
		{
			return bottomMostSite;
		}
		return edge.site(LR.other(he.leftRight));
	}

	public static int compareByYThenX(Site s1, Site s2)
	{
		if (s1.get_y() < s2.get_y())
		{
			return -1;
		}
		if (s1.get_y() > s2.get_y())
		{
			return 1;
		}
		if (s1.get_x() < s2.get_x())
		{
			return -1;
		}
		if (s1.get_x() > s2.get_x())
		{
			return 1;
		}
		return 0;
	}

	public static int compareByYThenX(Site s1, Point s2)
	{
		if (s1.get_y() < s2.y)
		{
			return -1;
		}
		if (s1.get_y() > s2.y)
		{
			return 1;
		}
		if (s1.get_x() < s2.x)
		{
			return -1;
		}
		if (s1.get_x() > s2.x)
		{
			return 1;
		}
		return 0;
	}
}
