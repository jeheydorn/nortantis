package hoten.voronoi;

import hoten.geom.Point;
import hoten.geom.Rectangle;
import hoten.voronoi.nodename.as3delaunay.LineSegment;
import hoten.voronoi.nodename.as3delaunay.Voronoi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import nortantis.PlateType;
import nortantis.TectonicPlate;
import util.Function;
import util.Range;

/**
 * VoronoiGraph.java
 *
 * @author Connor
 */
public abstract class VoronoiGraph {

    final public ArrayList<Edge> edges = new ArrayList<>();
    final public ArrayList<Corner> corners = new ArrayList<>();
    final public ArrayList<Center> centers = new ArrayList<>();
    public Rectangle bounds;
    final protected Random rand;
    public BufferedImage img;
    protected Color OCEAN, RIVER, LAKE, BEACH;
    NoisyEdges noisyEdges;
    /**
     * This controls how many rivers there are. Bigger means more.
     */
	private double riverDensity = 1.0/14.0;
	private double scaleMultiplyer;
	private int riversThinnerThanThisWillNotBeDrawn = 2;


    public VoronoiGraph(Random r, double scaleMultiplyer) {
    	this.rand = r;
        bumps = r.nextInt(5) + 1;
        this.scaleMultiplyer = scaleMultiplyer;
        startAngle = r.nextDouble() * 2 * Math.PI;
        dipAngle = r.nextDouble() * 2 * Math.PI;
        dipWidth = r.nextDouble() * .5 + .2;
   }
    
    public void initVoronoiGraph(Voronoi v, int numLloydRelaxations)
    {
        bounds = v.get_plotBounds();
         for (int i = 0; i < numLloydRelaxations; i++) {
            ArrayList<Point> points = v.siteCoords();
            for (Point p : points) {
                ArrayList<Point> region = v.region(p);
                double x = 0;
                double y = 0;
                for (Point c : region) {
                    x += c.x;
                    y += c.y;
                }
                x /= region.size();
                y /= region.size();
                p.x = x;
                p.y = y;
            }
            v = new Voronoi(points, null, v.get_plotBounds());
        }
        buildGraph(v);
        improveCorners();

        assignCornerElevations();  
        redistributeElevations(landCorners());
        assignPolygonElevations();
        // Joseph note: I changed the order in which this is called.
        assignOceanCoastAndLand();
        
        createRivers(); 
		assignCornerMoisture();
        redistributeMoisture(landCorners());
        assignPolygonMoisture();
        assignBiomes();
        noisyEdges = new NoisyEdges(scaleMultiplyer);  
        noisyEdges.buildNoisyEdges(this, new Random(rand.nextLong()));	
    }
    
    abstract protected Enum<?> getBiome(Center p);

    abstract protected Color getColor(Enum<?> biome);

    public Center getCenterAt(double x, double y) 
    {
    	// Joseph note: img is not setup, so I'm re-writing this.
        //return centers.get(img.getRGB(x, y) & 0xffffff);
    	
    	final Point p = new Point(x, y);
    	return Collections.max(centers, new Comparator<Center>()
    			{
					@Override
					public int compare(Center c1, Center c2)
					{
						return -Double.compare(Point.distance(p, c1.loc), Point.distance(p, c2.loc));
					}
    			});
    }
    
    public TectonicPlate getTectonicPlateAt(double x, double y)
    {
    	return getCenterAt(x, y).tectonicPlate;
    }

    /* an additional smoothing method across corners */
    private void improveCorners() {
        Point[] newP = new Point[corners.size()];
        for (Corner c : corners) {
            if (c.border) {
                newP[c.index] = c.loc;
            } else {
                double x = 0;
                double y = 0;
                for (Center center : c.touches) {
                    x += center.loc.x;
                    y += center.loc.y;
                }
                newP[c.index] = new Point(x / c.touches.size(), y / c.touches.size());
            }
        }
        for (Corner c : corners) {
            c.loc = newP[c.index];
        }
        for (Edge e : edges) {
            if (e.v0 != null && e.v1 != null) {
                e.setVornoi(e.v0, e.v1);
            }
        }
    }

    private Edge edgeWithCenters(Center c1, Center c2) {
        for (Edge e : c1.borders) {
            if (e.d0 == c2 || e.d1 == c2) {
                return e;
            }
        }
        return null;
    }

    private void drawTriangle(Graphics2D g, Corner c1, Corner c2, Center center) {
        int[] x = new int[3];
        int[] y = new int[3];
        x[0] = (int) center.loc.x;
        y[0] = (int) center.loc.y;
        x[1] = (int) c1.loc.x;
        y[1] = (int) c1.loc.y;
        x[2] = (int) c2.loc.x;
        y[2] = (int) c2.loc.y;
        g.fillPolygon(x, y, 3);
    }

    private boolean closeEnough(double d1, double d2, double diff) {
        return Math.abs(d1 - d2) <= diff * scaleMultiplyer;
    }

    public void paint(Graphics2D g, boolean drawRivers, boolean drawPlates, boolean drawElevations, 
    		boolean drawNoisyEdges, boolean drawLandAndOceanBlackAndWhiteOnly, boolean drawCoastlineOnly,
    		boolean drawRiverMaskOnly) 
    {
    	paint(g, drawRivers, drawPlates, drawElevations, drawNoisyEdges, drawLandAndOceanBlackAndWhiteOnly,
    			drawCoastlineOnly, drawRiverMaskOnly, 1);
    }

    public void paint(Graphics2D g, boolean drawRivers, boolean drawPlates, boolean drawElevations,
    		boolean drawNoisyEdges, boolean drawLandAndOceanBlackAndWhiteOnly, boolean drawCoastlineOnly,
    		boolean drawRiverMaskOnly, double widthMultipierForMasks) 
    {
    	boolean drawBioms = true;
    	boolean drawSites = false; 
    	boolean drawCorners = false; 
    	boolean drawDeluanay = false;  
    	boolean drawVoronoi = false; 
        paint(g, drawBioms, drawRivers, drawSites, drawCorners, drawDeluanay, drawVoronoi, drawPlates,
        		drawElevations, drawNoisyEdges, drawLandAndOceanBlackAndWhiteOnly, drawCoastlineOnly,
        		widthMultipierForMasks);
    }

    public void paint(Graphics2D g, boolean drawBiomes, boolean drawRivers, boolean drawSites, 
    		boolean drawCorners, boolean drawDelaunay, boolean drawVoronoi, boolean drawPlates,
    		boolean drawElevation, boolean drawNoisyEdges, final boolean drawLandAndOceanBlackAndWhiteOnly,
    		boolean drawCoastlineOnly, double widthMultipierForMasks)
    {
        final int numSites = centers.size();

        Color[] defaultColors = null;
        if (!drawBiomes) {
            defaultColors = new Color[numSites];
            for (int i = 0; i < defaultColors.length; i++) {
                defaultColors[i] = new Color(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255));
            }
        }
        
        if (drawCoastlineOnly)
        {
        	g.setColor(Color.black);
        	g.fillRect(0, 0, (int)bounds.width, (int)bounds.height);
        	g.setColor(Color.white);
        	drawSpecifiedEdges(g, Math.max(1, (int) widthMultipierForMasks), EdgeSelect.COASTLINE);
        	return;
        }
        
        //draw via triangles
        for (Center c : centers) 
        {
         	if (drawLandAndOceanBlackAndWhiteOnly)
        		g.setColor(getColor(c.biome) == OCEAN ? Color.black : Color.white);
        	else
        	{
                g.setColor(drawBiomes ? getColor(c.biome) : defaultColors[c.index]);       		
        	}
                        
            if (drawElevation)
            {
            	float grayLevel = (float) (float)c.elevation;
            	g.setColor(new Color(grayLevel, grayLevel, grayLevel));
            }
            
            drawUsingTriangles(g, c);
        }

        if (drawNoisyEdges)
        {
        	// This will draw polygons on top of the polygons we just drew, but this method doesn't handle
        	// edge polygons correctly, I need to draw both.
        	
        	renderPolygons(g, new Function<Center, Color>() 
			{
				public Color apply(Center c)
				{
					if (drawLandAndOceanBlackAndWhiteOnly)
						return getColor(c.biome) == OCEAN ? Color.black : Color.white;
					else
						return getColor(c.biome);
				}
			});
        }
        
        if (drawRivers && !drawLandAndOceanBlackAndWhiteOnly)
        {
            g.setColor(RIVER);
        	drawRivers(g, widthMultipierForMasks);
        }
        
        for (Edge e : edges) {
            if (drawDelaunay) {
                g.setStroke(new BasicStroke(1));
                g.setColor(Color.YELLOW);
                g.drawLine((int) e.d0.loc.x, (int) e.d0.loc.y, (int) e.d1.loc.x, (int) e.d1.loc.y);
            }
            if (drawPlates && e.d0.tectonicPlate != e.d1.tectonicPlate && e.v0 != null && e.v1 != null)
            {
                g.setStroke(new BasicStroke(1));
                g.setColor(Color.GREEN);
                g.drawLine((int) e.v0.loc.x, (int) e.v0.loc.y, (int) e.v1.loc.x, (int) e.v1.loc.y);
            }
        }

        if (drawSites) {
            g.setColor(Color.YELLOW);
            for (Center s : centers) {
                g.fillOval((int) (s.loc.x - 2), (int) (s.loc.y - 2), 12, 12);
            }
        }

//        if (drawPlates) {
//            for (Center s : centers) {
//             	int grayLevel = (int)((((double)s.tectonicPlateId) / centers.size())*255);
//				g.setColor(new Color(grayLevel, grayLevel, grayLevel));
//				g.fillRect((int) (s.loc.x - 2), (int) (s.loc.y - 2), 3, 4);
//            }
//        }

        if (drawCorners) {
            g.setColor(Color.WHITE);
            for (Corner c : corners) 
            {
//            	float grayLevel = (float)c.elevation;
//            	g.setColor(new Color(grayLevel, grayLevel, grayLevel));
            	g.setColor(Color.PINK);

                g.fillOval((int) (c.loc.x - 2), (int) (c.loc.y - 2), 10, 10);
            }
        }
        
        if (drawVoronoi)
        {
        	g.setColor(Color.WHITE);
        	for (Corner c : corners)
        	{
        		for (Corner adjacent : c.adjacent)
        		{
        			g.drawLine((int)c.loc.x, (int)c.loc.y, (int) adjacent.loc.x, (int)adjacent.loc.y);
        		}
        	}
        	
        }
    }
    
    public void drawRivers(Graphics2D g, double riverWidthScale)
    {
        for (Edge e : edges) 
        {
        	if (e.river > riversThinnerThanThisWillNotBeDrawn)
        	{
        		int width = Math.max(1, (int)(riverWidthScale + Math.sqrt(e.river * 0.1)));
                g.setStroke(new BasicStroke(width));
                drawPath(g, noisyEdges.path0.get(e.index));
                drawPath(g, noisyEdges.path1.get(e.index));
        	}
        }
    }
        
    protected void drawUsingTriangles(Graphics2D g, Center c)
    {
        //only used if Center c is on the edge of the graph. allows for completely filling in the outer
        // polygons. This is a list because if c borders 2 edges, it may have 2 missing triangles.
 //   	List<Tuple2<Corner, Corner>> edgeCorners = null;
    	List<Corner> edgeCorners = null;

    	c.area = 0;
        for (Center n : c.neighbors) 
        {
            Edge e = edgeWithCenters(c, n);

            if (e.v0 == null) {
                //outermost voronoi edges aren't stored in the graph
                continue;
            }

            //find a corner on the exterior of the graph
            //if this Edge e has one, then it must have two,
            //finding these two corners will give us the missing
            //triangle to render. this special triangle is handled
            //outside this for loop
            // Joseph note: The above is wrong. It may be the case that
            // 1, or an odd number of corners touch the border.
            
            for (Corner cornerWithOneAdjacent : Arrays.asList(e.v0, e.v1))
            {
	            if (cornerWithOneAdjacent.border) 
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
 
            drawTriangle(g, e.v0, e.v1, c);
            
            c.area += Math.abs(c.loc.x * (e.v0.loc.y - e.v1.loc.y)
                    + e.v0.loc.x * (e.v1.loc.y - c.loc.y)
                    + e.v1.loc.x * (c.loc.y - e.v0.loc.y)) / 2;
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
					// TODOO: find a way to fix this

					if (closeEnough(edgeCorner1.loc.x, edgeCorner2.loc.x, 1)
							|| closeEnough(edgeCorner1.loc.y,
									edgeCorner2.loc.y, 1))
					{
						// Both corners on on a single border.
						drawTriangle(g, edgeCorner1, edgeCorner2, c);
					} 
					else
					{
						int[] x = new int[4];
						int[] y = new int[4];
						x[0] = (int) c.loc.x;
						y[0] = (int) c.loc.y;
						x[1] = (int) edgeCorner1.loc.x;
						y[1] = (int) edgeCorner1.loc.y;

						// determine which corner this is

						x[2] = (int) ((closeEnough(edgeCorner1.loc.x, bounds.x,
								1) || closeEnough(edgeCorner2.loc.x, bounds.x,
								.5)) ? bounds.x : bounds.right);
						y[2] = (int) ((closeEnough(edgeCorner1.loc.y, bounds.y,
								1) || closeEnough(edgeCorner2.loc.y, bounds.y,
								.5)) ? bounds.y : bounds.bottom);

						x[3] = (int) edgeCorner2.loc.x;
						y[3] = (int) edgeCorner2.loc.y;

						g.fillPolygon(x, y, 4);
						c.area += 0; // TODOO: area of polygon given vertices
					}
				}
			}
		}

    }
    
    private void drawPath(Graphics2D g, List<Point> path)
    {
        for (int i = 1; i < path.size(); i++)
        {
        	g.drawLine((int)path.get(i-1).x, (int)path.get(i-1).y, (int)path.get(i).x, 
        			(int)path.get(i).y);
        }    	
    }
    
    public void drawCoastline(Graphics2D g, double width)
    {
    	drawSpecifiedEdges(g, Math.max(1, (int) width), EdgeSelect.COASTLINE);
    }

    public void drawRegionBorders(Graphics2D g, double width)
    {
    	drawSpecifiedEdges(g, Math.max(1, (int) width), EdgeSelect.REGION_BORDERS);
    }
   
    private enum EdgeSelect
    {
    	COASTLINE,
    	REGION_BORDERS
    }
        
	private void drawSpecifiedEdges(Graphics2D g, int width, EdgeSelect edgeSelect)
	{		
		g.setStroke(new BasicStroke(width));
		
		for (final Center p : centers)
		{
			for (final Center r : p.neighbors)
			{
				if (edgeSelect == EdgeSelect.COASTLINE)
				{
					if (p.ocean == r.ocean)
						continue;
				}
				else if (edgeSelect == EdgeSelect.REGION_BORDERS)
				{
					if (p.ocean || r.ocean 
							|| p.tectonicPlate == r.tectonicPlate
							|| p.tectonicPlate.type == PlateType.Oceanic 
							|| r.tectonicPlate.type == PlateType.Oceanic)
						continue;
				}
				
				Edge edge = lookupEdgeFromCenter(p, r);
				
				if (edgeSelect == EdgeSelect.REGION_BORDERS && edge.river > riversThinnerThanThisWillNotBeDrawn)
				{
					// Don't draw region boundaries where there are rivers.
					continue;
				}

				if (noisyEdges.path0.get(edge.index) == null
						|| noisyEdges.path1.get(edge.index) == null)
				{
					// It's at the edge of the map, where we don't have
					// the noisy edges computed. TODOO: figure out how to
					// fill in these edges from the voronoi library.
					continue;
				}

				// Draw path0.
				{
					List<Point> path = noisyEdges.path0.get(edge.index);
					int[] xPoints = new int[path.size()];
					int[] yPoints = new int[path.size()];
					for (int i : new Range(path.size()))
					{
						xPoints[i] = (int) path.get(i).x;
						yPoints[i] = (int) path.get(i).y;
					}
					g.drawPolyline(xPoints, yPoints, xPoints.length);
				}

				// Draw path1.
				{
					List<Point> path = noisyEdges.path1.get(edge.index);
					int[] xPoints = new int[path.size()];
					int[] yPoints = new int[path.size()];
					for (int i : new Range(path.size()))
					{
						xPoints[i] = (int) path.get(i).x;
						yPoints[i] = (int) path.get(i).y;
					}
					g.drawPolyline(xPoints, yPoints, xPoints.length);
				}

			}
		}

	}
    
    // Render the interior of polygons.
    protected void renderPolygons(Graphics2D g, Function<Center, Color> colorChooser) 
    {
      
      for (final Center p : centers) 
      {
          for (final Center r : p.neighbors) 
          {
              Edge edge = lookupEdgeFromCenter(p, r);
              
              g.setColor(colorChooser.apply(p));
              
              if (noisyEdges.path0.get(edge.index) == null
                  || noisyEdges.path1.get(edge.index) == null) 
              {
                // It's at the edge of the map, where we don't have
                // the noisy edges computed. TODOO: figure out how to
                // fill in these edges from the voronoi library.
                continue;
              }

 
            // Draw path0.
            {
	            List<Point> path = noisyEdges.path0.get(edge.index);
	            java.awt.Polygon shape = new java.awt.Polygon();
	            shape.addPoint((int)p.loc.x, (int)p.loc.y);
	            for (Point point : path)
	            {
	            	shape.addPoint((int)point.x, (int)point.y);
	            }
	            g.fillPolygon(shape);
            }
 
            // Draw path1.
            {
            	List<Point> path = noisyEdges.path1.get(edge.index);
	            java.awt.Polygon shape = new java.awt.Polygon();
	            shape.addPoint((int)p.loc.x, (int)p.loc.y);
	            for (Point point : path)
	            {
	            	shape.addPoint((int)point.x, (int)point.y);
	            }
	            g.fillPolygon(shape);
            }

            }
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


    private void buildGraph(Voronoi v) {
        final HashMap<Point, Center> pointCenterMap = new HashMap<>();
        final ArrayList<Point> points = v.siteCoords();
        for (Point p : points) {
            Center c = new Center();
            c.loc = p;
            c.index = centers.size();
            centers.add(c);
            pointCenterMap.put(p, c);
        }

        //bug fix
        for (Center c : centers) {
            v.region(c.loc);
        }

        final ArrayList<hoten.voronoi.nodename.as3delaunay.Edge> libedges = v.edges();
        final TreeMap<Point, Corner> pointCornerMap = new TreeMap<>();

        for (hoten.voronoi.nodename.as3delaunay.Edge libedge : libedges) {
            final LineSegment vEdge = libedge.voronoiEdge();
            final LineSegment dEdge = libedge.delaunayLine();

            final Edge edge = new Edge();
            edge.index = edges.size();
            edges.add(edge);

            edge.v0 = makeCorner(pointCornerMap, vEdge.p0);
            edge.v1 = makeCorner(pointCornerMap, vEdge.p1);
            edge.d0 = pointCenterMap.get(dEdge.p0);
            edge.d1 = pointCenterMap.get(dEdge.p1);

            // Centers point to edges. Corners point to edges.
            if (edge.d0 != null) {
                edge.d0.borders.add(edge);
            }
            if (edge.d1 != null) {
                edge.d1.borders.add(edge);
            }
            if (edge.v0 != null) {
                edge.v0.protrudes.add(edge);
            }
            if (edge.v1 != null) {
                edge.v1.protrudes.add(edge);
            }

            // Centers point to centers.
            if (edge.d0 != null && edge.d1 != null) {
                addToCenterList(edge.d0.neighbors, edge.d1);
                addToCenterList(edge.d1.neighbors, edge.d0);
            }

            // Corners point to corners
            if (edge.v0 != null && edge.v1 != null) {
                addToCornerList(edge.v0.adjacent, edge.v1);
                addToCornerList(edge.v1.adjacent, edge.v0);
            }

            // Centers point to corners
            if (edge.d0 != null) {
                addToCornerList(edge.d0.corners, edge.v0);
                addToCornerList(edge.d0.corners, edge.v1);
            }
            if (edge.d1 != null) {
                addToCornerList(edge.d1.corners, edge.v0);
                addToCornerList(edge.d1.corners, edge.v1);
            }

            // Corners point to centers
            if (edge.v0 != null) {
                addToCenterList(edge.v0.touches, edge.d0);
                addToCenterList(edge.v0.touches, edge.d1);
            }
            if (edge.v1 != null) {
                addToCenterList(edge.v1.touches, edge.d0);
                addToCenterList(edge.v1.touches, edge.d1);
            }
        }
    }

    // Helper functions for the following for loop; ideally these
    // would be inlined
    private void addToCornerList(ArrayList<Corner> list, Corner c) {
        if (c != null && !list.contains(c)) {
            list.add(c);
        }
    }

    private void addToCenterList(ArrayList<Center> list, Center c) {
        if (c != null && !list.contains(c)) {
            list.add(c);
        }
    }

    //ensures that each corner is represented by only one corner object
    private Corner makeCorner(TreeMap<Point, Corner> pointCornerMap, Point p) {
        if (p == null) {
            return null;
        }
        // Joseph note: I changed this function to use a TreeMap and sizeMultiplyer
        // so that the graph won't have small changes when drawn at higher resolutions.
        
        // As pointKeyScale becomes larger, points become less likely to be merged. I added this because of a bug
        // where corners on the border of the graph which were needed to draw the polygons on the border were disappearing,
        // causing the background color to be shown in triangular a
        final double pointKeyScale = 10.0;
        Point key = new Point((int)((p.x / scaleMultiplyer) * pointKeyScale), (int)((p.y / scaleMultiplyer)) * pointKeyScale);
        Corner c = pointCornerMap.get(key);
        if (c == null) {
            c = new Corner();
            c.loc = p;
            c.border = bounds.liesOnAxes(p);
            c.index = corners.size();
            corners.add(c);
            pointCornerMap.put(key, c);
        }
        return c;
    }

    protected void assignCornerElevations() {
        LinkedList<Corner> queue = new LinkedList<>();
        for (Corner c : corners) {
            c.water = isWater(c.loc);
            if (c.border) {
                c.elevation = 0;
                queue.add(c);
            } else {
                c.elevation = Double.MAX_VALUE;
            }
        }

        // Assign each corner's elevation as its distance from water.
        while (!queue.isEmpty()) {
            Corner c = queue.pop();
            for (Corner a : c.adjacent) {
                double newElevation = 0.01 + c.elevation;
                if (!c.water && !a.water) {
                    newElevation += 1;
                }
                if (newElevation < a.elevation) {
                    a.elevation = newElevation;
                    queue.add(a);
                }
            }
        }
    }
    double[][] noise;
    double ISLAND_FACTOR = 1.07;  // 1.0 means no small islands; 2.0 leads to a lot
    final int bumps;
    final double startAngle;
    final double dipAngle;
    final double dipWidth;

    //only the radial implementation of amitp's map generation
    //TODOO implement more island shapes
    private boolean isWater(Point p) {
        p = new Point(2 * (p.x / bounds.width - 0.5), 2 * (p.y / bounds.height - 0.5));

        double angle = Math.atan2(p.y, p.x);
        double length = 0.5 * (Math.max(Math.abs(p.x), Math.abs(p.y)) + p.length());

        double r1 = 0.5 + 0.40 * Math.sin(startAngle + bumps * angle + Math.cos((bumps + 3) * angle));
        double r2 = 0.7 - 0.20 * Math.sin(startAngle + bumps * angle - Math.sin((bumps + 2) * angle));
        if (Math.abs(angle - dipAngle) < dipWidth
                || Math.abs(angle - dipAngle + 2 * Math.PI) < dipWidth
                || Math.abs(angle - dipAngle - 2 * Math.PI) < dipWidth) {
            r1 = r2 = 0.2;
        }
        return !(length < r1 || (length > r1 * ISLAND_FACTOR && length < r2));

        //return false;

        /*if (noise == null) {
         noise = new Perlin2d(.125, 8, MyRandom.seed).createArray(257, 257);
         }
         int x = (int) ((p.x + 1) * 128);
         int y = (int) ((p.y + 1) * 128);
         return noise[x][y] < .3 + .3 * p.l2();*/

        /*boolean eye1 = new Point(p.x - 0.2, p.y / 2 + 0.2).length() < 0.05;
         boolean eye2 = new Point(p.x + 0.2, p.y / 2 + 0.2).length() < 0.05;
         boolean body = p.length() < 0.8 - 0.18 * Math.sin(5 * Math.atan2(p.y, p.x));
         return !(body && !eye1 && !eye2);*/
    }

    protected void assignOceanCoastAndLand() {
        LinkedList<Center> queue = new LinkedList<>();
        final double waterThreshold = .3;
        for (final Center center : centers) {
            int numWater = 0;
            for (final Corner c : center.corners) {
                if (c.border) {
                    center.border = center.water = center.ocean = true;
                    queue.add(center);
                }
                if (c.water) {
                    numWater++;
                }
            }
            center.water = center.ocean || ((double) numWater / center.corners.size() >= waterThreshold);
        }
        // Do a flood fill search from the borders of the map to mark ocean polygons. 
        while (!queue.isEmpty()) {
            final Center center = queue.pop();
            for (final Center n : center.neighbors) {
                if (n.water && !n.ocean) {
                    n.ocean = true;
                    queue.add(n);
                }
            }
        }
        
        // Determine which polygons are coast.
        for (Center center : centers) {
            boolean oceanNeighbor = false;
            boolean landNeighbor = false;
            for (Center n : center.neighbors) {
                oceanNeighbor |= n.ocean;
                landNeighbor |= !n.water;
            }
            center.coast = oceanNeighbor && landNeighbor;
        }

        // Determine if each corner is ocean, coast, or water. 
        for (Corner c : corners) {
            int numOcean = 0;
            int numLand = 0;
            for (Center center : c.touches) {
                numOcean += center.ocean ? 1 : 0;
                numLand += !center.water ? 1 : 0;
            }
            c.ocean = numOcean == c.touches.size();
            c.coast = numOcean > 0 && numLand > 0;
            c.water = c.border || ((numLand != c.touches.size()) && !c.coast);
        }
    }

    private ArrayList<Corner> landCorners() {
        final ArrayList<Corner> list = new ArrayList<>();
        for (Corner c : corners) {
            if (!c.ocean && !c.coast) {
                list.add(c);
            }
        }
        return list;
    }

     protected void redistributeElevations(ArrayList<Corner> landCorners) {
        Collections.sort(landCorners, new Comparator<Corner>() {
            @Override
            public int compare(Corner o1, Corner o2) {
                if (o1.elevation > o2.elevation) {
                    return 1;
                } else if (o1.elevation < o2.elevation) {
                    return -1;
                }
                return 0;
            }
        });

        final double SCALE_FACTOR = 1.1;
        for (int i = 0; i < landCorners.size(); i++) {
            double y = (double) i / landCorners.size();
            double x = Math.sqrt(SCALE_FACTOR) - Math.sqrt(SCALE_FACTOR * (1 - y));
            x = Math.min(x, 1);
            landCorners.get(i).elevation = x;
        }

        for (Corner c : corners) {
            if (c.ocean || c.coast) {
                c.elevation = 0.0;
            }
        }
    }
    
    protected double maxElevation = 0.0;

    private void assignPolygonElevations() {
        for (Center center : centers) {
            double total = 0;
            for (Corner c : center.corners) {
                total += c.elevation;
            }
            center.elevation = total / center.corners.size();
            if (center.elevation > maxElevation) {
            	maxElevation = center.elevation;
            }
        }
    }

    private void createRivers() {    	
         for (int i = 0; i < corners.size() * riverDensity ; i++) {
        	int index = rand.nextInt(corners.size());
            Corner c = corners.get(index);
            c.createRivers();
        }
    }

    private void assignCornerMoisture() {
        LinkedList<Corner> queue = new LinkedList<>();
        for (Corner c : corners) {
            if ((c.water || c.river > 2) && !c.ocean) {
                c.moisture = c.river > 2 ? Math.min(3.0, (0.05 * c.river)) : 1.0;
                queue.push(c);
            } else {
                c.moisture = 0.0;
            }
        }

        while (!queue.isEmpty()) {
            Corner c = queue.pop();
            for (Corner a : c.adjacent) {
                double newM = .9 * c.moisture;
                if (newM > a.moisture) {
                    a.moisture = newM;
                    queue.add(a);
                }
            }
        }

        // Salt water
        for (Corner c : corners) {
            if (c.ocean || c.coast) {
                c.moisture = 1.0;
            }
        }
    }

    private void redistributeMoisture(ArrayList<Corner> landCorners) {
        Collections.sort(landCorners, new Comparator<Corner>() {
            @Override
            public int compare(Corner o1, Corner o2) {
                if (o1.moisture > o2.moisture) {
                    return 1;
                } else if (o1.moisture < o2.moisture) {
                    return -1;
                }
                return 0;
            }
        });
        for (int i = 0; i < landCorners.size(); i++) {
            landCorners.get(i).moisture = (double) i / landCorners.size();
        }
    }

    private void assignPolygonMoisture() {
        for (Center center : centers) {
            double total = 0;
            for (Corner c : center.corners) {
                total += c.moisture;
            }
            center.moisture = total / center.corners.size();
        }
    }

    private void assignBiomes() {
        for (Center center : centers) {
            center.biome = getBiome(center);
        }
    }
}
