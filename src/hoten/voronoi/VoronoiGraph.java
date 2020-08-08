package hoten.voronoi;

import static org.junit.Assert.assertEquals;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import hoten.geom.Point;
import hoten.geom.Rectangle;
import hoten.voronoi.nodename.as3delaunay.LineSegment;
import hoten.voronoi.nodename.as3delaunay.Voronoi;
import nortantis.Biome;
import nortantis.util.Range;

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
    protected NoisyEdges noisyEdges;
    /**
     * This controls how many rivers there are. Bigger means more.
     */
	private double riverDensity = 1.0/14.0;
	protected double scaleMultiplyer;
	public static final int riversThinnerThanThisWillNotBeDrawn = 2;
	
	final static double verySmall = 0.0000001;
	double pointPrecision;

	/**
	 * @param r Random number generator
	 * @param scaleMultiplyer Used to scale the graph larger smaller according to the resolution being used.
	 * @param pointPrecision Used to determine when points should be considered duplicates. Larger numbers mean less duplicate detection, making tiny polygons more likely. 
	 * 		  This number will be scaled by scaleMultiplyer.
	 */
    public VoronoiGraph(Random r, double scaleMultiplyer, double pointPrecision) {
    	this.rand = r;
        bumps = r.nextInt(5) + 1;
        this.scaleMultiplyer = scaleMultiplyer;
        startAngle = r.nextDouble() * 2 * Math.PI;
        dipAngle = r.nextDouble() * 2 * Math.PI;
        dipWidth = r.nextDouble() * .5 + .2;
        this.pointPrecision = pointPrecision;
   }
    
    public void initVoronoiGraph(Voronoi v, int numLloydRelaxations, boolean createElevationRiversAndBiomes)
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

        if (createElevationRiversAndBiomes)
        {
	        assignCornerElevations();  
	        assignPolygonElevations();
	        // Joseph note: I changed the order in which this is called.
	        assignOceanCoastAndLand();
	        
	        createRivers(); 
			assignCornerMoisture();
	        redistributeMoisture(landCorners());
	        assignPolygonMoisture();
	        assignBiomes();
        }
        // Joseph note: I moved noisy edge building code to GraphImpl because it now depends on the political regions.
    }
    
    abstract protected Biome getBiome(Center p);

    abstract protected Color getColor(Biome biome);


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

    private Edge edgeWithCenters(Center c1, Center c2) 
    {
        for (Edge e : c1.borders) {
            if (e.d0 == c2 || e.d1 == c2) {
                return e;
            }
        }
        return null;
    }

    private static void drawTriangle(Graphics2D g, Corner c1, Corner c2, Center center) 
    {
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
    
    private static void drawTriangleElevation(Graphics2D g, Corner c1, Corner c2, Center center) 
    {
    	Vector3D v1 = new Vector3D(c1.loc.x, c1.loc.y, c1.elevation);
    	Vector3D v2 = new Vector3D(c2.loc.x, c2.loc.y, c2.elevation);
    	Vector3D v3 = new Vector3D(center.loc.x, center.loc.y, center.elevation);
    	
    	// Normal of the plane containing the triangle
    	Vector3D N = v2.subtract(v1).crossProduct(v3.subtract(v1));
    	
    	Vector3D highestPoint = findHighestZ(v1, v2, v3);
    	int highestPointGrayLevel = (int)(highestPoint.getZ() * 255);
    	Color highestPointColor = new Color(highestPointGrayLevel, highestPointGrayLevel, highestPointGrayLevel);
    	
    	// Gradient of x and y with respect to z.
    	Vector3D G = new Vector3D(-N.getX()/N.getZ(), -N.getY()/N.getZ(), 0);

       	if ((Math.abs(G.getX()) < verySmall || Double.isInfinite(G.getX()) || Double.isNaN(G.getX()))
       			&& Math.abs(G.getY()) < verySmall || Double.isInfinite(G.getY()) || Double.isNaN(G.getY()))
    	{
       		// The triangle is either flat or vertical. 
    		int grayLevel = (int)(255 * center.elevation);
    		g.setColor(new Color(grayLevel, grayLevel, grayLevel));
        	drawTriangle(g, c1, c2, center);
        	return;
    	}
       	
       	Vector3D zIntercept = findZIntersectionWithXYPlain(highestPoint, G);

    	g.setPaint(new GradientPaint(
    			(float)highestPoint.getX(), (float)highestPoint.getY(), highestPointColor, 
    			(float)zIntercept.getX(), (float)zIntercept.getY(), Color.black, 
    			false));
    	drawTriangle(g, c1, c2, center);
    }
        
    private static Vector3D findZIntersectionWithXYPlain(Vector3D point, Vector3D gradient)
    {
    	// To calculate this, I first had that 
    	//     deltaZ = a*deltaX + b*deltaY
    	// where delta* variables are the change in x, y, and z, and a and b are the x and y components of the gradient.
    	// I then constrained deltaX and delta Y by requiring them to follow the gradient, so 
    	//     deltaY = (a/b)*deltaX.
    	// Plugging that into the first equation and solving for delta X, I got the equation below for x. The equation for y is very similar.
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
    	BufferedImage image = new BufferedImage(101,101, BufferedImage.TYPE_INT_RGB);
    	Corner corner1 = new Corner();
    	corner1.loc = new Point(0, 0);
    	corner1.elevation = 0.0;
    	Corner corner2 = new Corner();
    	corner2.elevation = 0.5;
    	corner2.loc = new Point(100, 0);
    	Center center = new Center(new Point(100, 100));
    	center.elevation = 1.0;
    	Graphics2D g = image.createGraphics();
    	drawTriangleElevation(g, corner1, corner2, center);
        assertEquals(
    			0, 
    			new Color(image.getRGB((int)corner1.loc.x, (int)corner1.loc.y)).getBlue());
    	assertEquals(
    			125,
    			new Color(image.getRGB((int)corner2.loc.x - 1, (int)corner2.loc.y)).getBlue());
    	assertEquals(
    			251,
    			new Color(image.getRGB((int)center.loc.x - 1, (int)center.loc.y - 2)).getBlue());
    }

    private static void drawTriangleElevationZeroXGradientTest()
    {
    	BufferedImage image = new BufferedImage(101,101, BufferedImage.TYPE_INT_RGB);
    	Corner corner1 = new Corner();
    	corner1.loc = new Point(0, 0);
    	corner1.elevation = 0.5;
    	Corner corner2 = new Corner();
    	corner2.elevation = 0.5;
    	corner2.loc = new Point(50, 0);
    	Center center = new Center(new Point(50, 100));
    	center.elevation = 1.0;
    	Graphics2D g = image.createGraphics();
    	drawTriangleElevation(g, corner1, corner2, center);
    	assertEquals(
    			(int)(corner1.elevation * 255),
    			new Color(image.getRGB((int)corner1.loc.x, (int)corner1.loc.y)).getBlue());
    	assertEquals(
    			(int)(corner2.elevation * 255),
    			new Color(image.getRGB((int)corner2.loc.x - 1, (int)corner2.loc.y)).getBlue());
    	assertEquals((int)(center.elevation * 253),
    			new Color(image.getRGB((int)center.loc.x - 1, (int)center.loc.y - 2)).getBlue());
    }

    private static void drawTriangleElevationZeroYGradientTest()
    {
    	BufferedImage image = new BufferedImage(101,101, BufferedImage.TYPE_INT_RGB);
    	Corner corner1 = new Corner();
    	corner1.loc = new Point(0, 0);
    	corner1.elevation = 0.0;
    	Corner corner2 = new Corner();
    	corner2.elevation = 0.0;
    	corner2.loc = new Point(0, 100);
    	Center center = new Center(new Point(50, 100));
    	center.elevation = 1.0;
    	Graphics2D g = image.createGraphics();
    	drawTriangleElevation(g, corner1, corner2, center);
    	assertEquals(
    			(int)(corner1.elevation * 255), 
    			new Color(image.getRGB((int)corner1.loc.x, (int)corner1.loc.y)).getBlue());
    	assertEquals(
    			(int)(corner2.elevation * 255),
    			new Color(image.getRGB((int)corner2.loc.x, (int)corner2.loc.y)).getBlue());
    	assertEquals((int)(center.elevation * 249),
    			new Color(image.getRGB((int)center.loc.x - 1, (int)center.loc.y-1)).getBlue());
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

    private boolean closeEnough(double d1, double d2, double diff) {
        return Math.abs(d1 - d2) <= diff * scaleMultiplyer;
    }

    public void paint(Graphics2D g, boolean drawRivers, boolean drawPlates, boolean drawElevations, 
    		boolean drawNoisyEdges, boolean drawCoastlineOnly,
    		boolean drawRiverMaskOnly) 
    {
    	paint(g, drawRivers, drawPlates, drawElevations, drawNoisyEdges,
    			drawCoastlineOnly, drawRiverMaskOnly, 1);
    }

    public void paint(Graphics2D g, boolean drawRivers, boolean drawPlates, boolean drawElevations,
    		boolean drawNoisyEdges, boolean drawCoastlineOnly,
    		boolean drawRiverMaskOnly, double widthMultipierForMasks) 
    {
    	boolean drawBioms = true;
    	boolean drawSites = false; 
    	boolean drawCorners = false; 
    	boolean drawDeluanay = false;  
    	boolean drawVoronoi = false;  
        paint(g, drawBioms, drawRivers, drawSites, drawCorners, drawDeluanay, drawVoronoi, drawPlates,
        		drawElevations, drawNoisyEdges, drawCoastlineOnly,
        		widthMultipierForMasks);
    }

    public void paint(Graphics2D g, boolean drawBiomes, boolean drawRivers, boolean drawSites, 
    		boolean drawCorners, boolean drawDelaunay, boolean drawVoronoi, boolean drawPlates,
    		boolean drawElevation, boolean drawNoisyEdges,
    		boolean drawCoastlineOnly, double widthMultipierForMasks)
    {
    	
        final int numSites = centers.size();
        
        
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
        	drawCoastline(g, Math.max(1, (int)widthMultipierForMasks));
        	return;
        }
       
        if (drawElevation)
        {
	        for (Center c : centers) 
	        {
            	float grayLevel = (float) (float)c.elevation;
            	g.setColor(new Color(grayLevel, grayLevel, grayLevel));	            
	            drawUsingTriangles(g, c, true);
	        }
        }
        else if (drawBiomes)
        {
        	drawBiomes(g);
        }

        if (drawNoisyEdges)
        {       	
        	renderPolygons(g, new Function<Center, Color>() 
			{
				public Color apply(Center c)
				{
					return getColor(c.biome);
				}
			});
        }
        
        if (drawRivers)
        {
            g.setColor(RIVER);
        	drawRivers(g, widthMultipierForMasks);
        }
        
        if (drawDelaunay)
        {
	        for (Edge e : edges) 
	        {
	            if (drawDelaunay) {
	                g.setStroke(new BasicStroke(1));
	                g.setColor(Color.YELLOW);
	                g.drawLine((int) e.d0.loc.x, (int) e.d0.loc.y, (int) e.d1.loc.x, (int) e.d1.loc.y);
	            }
	        }
        }
        
	    if (drawPlates)
	    {
	        for (Edge e : edges) 
	        {
	            if (drawPlates && e.d0.tectonicPlate != e.d1.tectonicPlate && e.v0 != null && e.v1 != null)
	            {
	                g.setStroke(new BasicStroke(1));
	                g.setColor(Color.GREEN);
	                g.drawLine((int) e.v0.loc.x, (int) e.v0.loc.y, (int) e.v1.loc.x, (int) e.v1.loc.y);
	            }
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
            	g.setColor(Color.PINK);

                g.fillOval((int) (c.loc.x - 2), (int) (c.loc.y - 2), 10, 10);
            }
        }
    }
    
    public void drawLandAndOceanBlackAndWhite(Graphics2D g, Collection<Center> centersToRender)
    {
       	renderPolygons(g, centersToRender, new Function<Center, Color>() 
			{
				public Color apply(Center c)
				{
					return c.isWater ? Color.black : Color.white;
				}
			});
		

       	// Code usefull for debugging
//    	g.setColor(Color.WHITE);
//    	for (Corner c : corners)
//    	{
//    		for (Corner adjacent : c.adjacent)
//    		{
//    			g.drawLine((int)c.loc.x, (int)c.loc.y, (int) adjacent.loc.x, (int)adjacent.loc.y);
//    		}
//    	}
//    	
//        for (Edge e : edges) 
//        {
//            g.setStroke(new BasicStroke(1));
//            g.setColor(Color.YELLOW);
//            g.drawLine((int) e.d0.loc.x, (int) e.d0.loc.y, (int) e.d1.loc.x, (int) e.d1.loc.y);
//        }
    }
    
    public void drawBiomes(Graphics2D g)
    {
    	renderPolygons(g, (center) -> 
    	{
    		return getColor(center.biome);
    	});
    }
    
    public void drawRivers(Graphics2D g, double riverWidthScale)
    {
        for (Edge e : edges) 
        {
        	if (e.river > riversThinnerThanThisWillNotBeDrawn)
        	{
        		int width = Math.max(1, (int)(riverWidthScale + Math.sqrt(e.river * 0.1)));
                g.setStroke(new BasicStroke(width));
                drawPath(g, noisyEdges.getNoisyEdge(e.index));
        	}
        }
    }
        
    protected void drawUsingTriangles(Graphics2D g, Center c, boolean drawElevation)
    {
        //only used if Center c is on the edge of the graph. allows for completely filling in the outer
        // polygons. This is a list because if c borders 2 edges, it may have 2 missing triangles.
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
 
            if (drawElevation)
            {
            	drawTriangleElevation(g, e.v0, e.v1, c);
            }
            else
            {
            	drawTriangle(g, e.v0, e.v1, c);
            }
            
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

					if (closeEnough(edgeCorner1.loc.x, edgeCorner2.loc.x, 1)
							|| closeEnough(edgeCorner1.loc.y,
									edgeCorner2.loc.y, 1))
					{
						// Both corners on on a single border.
						
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

						// determine which corner this is

						x[2] = (int) ((closeEnough(edgeCorner1.loc.x, bounds.x,
								1) || closeEnough(edgeCorner2.loc.x, bounds.x,
								.5)) ? bounds.x : bounds.right);
						y[2] = (int) ((closeEnough(edgeCorner1.loc.y, bounds.y,
								1) || closeEnough(edgeCorner2.loc.y, bounds.y,
								.5)) ? bounds.y : bounds.bottom);

						x[3] = (int) edgeCorner2.loc.x;
						y[3] = (int) edgeCorner2.loc.y;

						if (drawElevation)
						{
							// I really should break the polygon into triangles and call drawElevationOfTriangle on each, but for now I'm just doing this.
				           	float grayLevel = (float) (float)c.elevation;
			            	g.setColor(new Color(grayLevel, grayLevel, grayLevel));	            
						}
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
    	drawSpecifiedEdges(g, Math.max(1, (int) width), edge -> edge.d0.isWater != edge.d1.isWater);
    }

    public void drawRegionBorders(Graphics2D g, double width, boolean ignoreRiverEdges)
    {
    	drawSpecifiedEdges(g, Math.max(1, (int) width), edge -> 
    	{
			if (ignoreRiverEdges && edge.river > riversThinnerThanThisWillNotBeDrawn)
			{
				// Don't draw region boundaries where there are rivers.
				return false;
			}

    		return edge.d0.region != edge.d1.region;
    	});
    }
           
	private void drawSpecifiedEdges(Graphics2D g, int width, 
			Function<Edge, Boolean> shouldDraw)
	{		
		g.setStroke(new BasicStroke(width));
		
		for (final Center p : centers)
		{
			for (final Center r : p.neighbors)
			{
				
				Edge edge = lookupEdgeFromCenter(p, r);
				
				if (!shouldDraw.apply(edge))
					continue;

				drawEdge(g, edge);
			}
		}

	}
	
	public void drawEdge(Graphics2D g, Edge edge)
	{
		if (noisyEdges.getNoisyEdge(edge.index) == null)
		{
			// It's at the edge of the map, where we don't have
			// the noisy edges computed. 
			return;
		}

		{
			List<Point> path = noisyEdges.getNoisyEdge(edge.index);
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
    
    /**
     * Render the interior of polygons including noisy edges.
     * @param g
     * @param colorChooser Decides the color for each polygons. If it returns null, then the
     * polygons will not be drawn.
     */
    public void renderPolygons(Graphics2D g, Function<Center, Color> colorChooser)
    {    	
    	renderPolygons(g, centers, colorChooser);
    }
    
    protected void renderPolygons(Graphics2D g, Collection<Center> centersToRender, Function<Center, Color> colorChooser)
    {
    	// First I must draw border polygons without noisy edges because the noisy edges don't exist on the borders.
    	for (Center c : centersToRender)
    	{
    		if (c.isBorder)
    		{
				Color color = colorChooser.apply(c);
				if (color != null)
				{
					g.setColor(color);
					drawUsingTriangles(g, c, false);
				}
    		}
    	}
    	
    	// Draw noisy edges.
		for (final Center c : centersToRender)
		{			
			for (final Center r : c.neighbors)
			{
				Edge edge = lookupEdgeFromCenter(c, r);

				Color color = colorChooser.apply(c);
				if (color != null)
				{
					g.setColor(color);
					if (noisyEdges == null || noisyEdges.getNoisyEdge(edge.index) == null)
					{
						// This can happen if noisy edges haven't been created yet or if the polygon is on the border.
						drawPieceWithoutNoisyEdges(g, edge, c);
					}
					else
					{
						dawPieceUsingNoisyEdges(g, edge, c);
					}
				}
			}
		}
    }
    
    private void drawPieceWithoutNoisyEdges(Graphics2D g, Edge edge, Center c)
    {
		java.awt.Polygon shape = new java.awt.Polygon();
    	shape.addPoint((int) c.loc.x, (int) c.loc.y);
    	if (edge.v0 != null)
    		shape.addPoint((int)edge.v0.loc.x, (int)edge.v0.loc.y);
    	if (edge.v1 != null)
    		shape.addPoint((int)edge.v1.loc.x, (int)edge.v1.loc.y);
    	g.fill(shape);
		return;
    }
    
    private void dawPieceUsingNoisyEdges(Graphics2D g, Edge edge, Center c)
    {
		// Draw path0.
		{
			List<Point> path = noisyEdges.getNoisyEdge(edge.index);
			java.awt.Polygon shape = new java.awt.Polygon();
			shape.addPoint((int) c.loc.x, (int) c.loc.y);
			for (Point point : path)
			{
				shape.addPoint((int) point.x, (int) point.y);
			}
			g.fillPolygon(shape);
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
        // Joseph note: I changed this function to use a TreeMap and sizeMultiplier
        // so that the graph won't have small changes when drawn at higher resolutions.
        
        // As pointPrecision becomes larger, points become less likely to be merged. I added this because of a bug
        // where corners on the border of the graph which were needed to draw the polygons on the border were disappearing,
        // causing the background color to be shown
        Point key = new Point((int)((p.x / scaleMultiplyer) * pointPrecision), (int)((p.y / scaleMultiplyer)) * pointPrecision);
        Corner c = pointCornerMap.get(key);
        if (c == null) {
            c = new Corner();
            c.loc = p;
            c.border = bounds.liesOnAxes(p, scaleMultiplyer);
            c.index = corners.size();
            corners.add(c);
            pointCornerMap.put(key, c);
        }
        return c;
    }

    protected abstract void assignCornerElevations();

    double[][] noise;
    double ISLAND_FACTOR = 1.07;  // 1.0 means no small islands; 2.0 leads to a lot
    final int bumps;
    final double startAngle;
    final double dipAngle;
    final double dipWidth;

    protected abstract void assignOceanCoastAndLand();
    
    private ArrayList<Corner> landCorners() {
        final ArrayList<Corner> list = new ArrayList<>();
        for (Corner c : corners) {
            if (!c.ocean && !c.coast) {
                list.add(c);
            }
        }
        return list;
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
