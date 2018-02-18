package nortantis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;

import hoten.geom.Point;
import hoten.voronoi.Center;
import hoten.voronoi.Corner;
import hoten.voronoi.Edge;
import hoten.voronoi.NoisyEdges;
import hoten.voronoi.VoronoiGraph;
import hoten.voronoi.nodename.as3delaunay.Voronoi;
import util.Function;
import util.Helper;
import util.Range;

/**
 * TestGraphImpl.java
 *
 * Supplies information for Voronoi graphing logic:
 *
 * 1) biome mapping information based on a Site's elevation and moisture
 *
 * 2) color mapping information based on biome, and for bodies of water
 *
 * @author Connor
 */
public class GraphImpl extends VoronoiGraph
{

	// Modify seeFloorLevel to change the number of islands in the ocean.
	final double seaFloorLevel = 0.10;
	final double continentalPlateLevel = 0.45;
   	public static final float seaLevel = 0.39f;
   	// This field must be set before creating instance of GraphImpl. This is necessary because it must be set
   	// before calling VoronoiGraph's constructor, which Java requires to be the first call in GraphImpl's
   	// constructor.
	int numIterationsForTectonicPlateCreation;
	// The probability that a plate not touching the border will be continental.
   	double nonBorderPlateContinentalProbability;
   	// The probability that a plate touching the border will be continental.
   	double borderPlateContinentalProbability;
   	// This scales how much elevation is added or subtracted at convergent/divergent boundaries.
	final double collisionScale = 0.4;
	// This controlls how smooth the plates boundaries are. Higher is smoother. 1 is minumum. Larger values
	// will slow down plate generation.
	final int plateBoundarySmoothness = 20;
	final int minPoliticalRegionSize = 10;
	    
   // Maps plate ids to plates.
    Set<TectonicPlate> plates;
    public List<Region> regions;
       
    /*
       Colors converted to rgb:
        OCEAN: [r=68,g=68,b=122]
		LAKE: [r=51,g=102,b=153]
		BEACH: [r=160,g=144,b=119]
		SNOW: [r=255,g=255,b=255]
		TUNDRA: [r=187,g=187,b=170]
		BARE: [r=136,g=136,b=136]
		SCORCHED: [r=85,g=85,b=85]
		TAIGA: [r=153,g=170,b=119]
		SHURBLAND: [r=136,g=153,b=119]
		TEMPERATE_DESERT: [r=201,g=210,b=155]
		HIGH_TEMPERATE_DESERT: [r=189,g=196,b=153]
	 	TEMPERATE_RAIN_FOREST: [r=68,g=136,b=85]
		TEMPERATE_DECIDUOUS_FOREST: [r=103,g=148,b=89]
		HIGH_TEMPERATE_DECIDUOUS_FOREST: [r=74,b=119,b=61],
		GRASSLAND: [r=136,g=170,b=85]
		SUBTROPICAL_DESERT: [r=210,g=185,b=139]
		SHRUBLAND: [r=136,g=153,b=119]
		ICE: [r=153,g=255,b=255]
		MARSH: [r=47,g=102,b=102]
		TROPICAL_RAIN_FOREST: [r=51,g=119,b=85]
		TROPICAL_SEASONAL_FOREST: [r=85,g=153,b=68]
		COAST: [r=51,g=51,b=90]
		LAKESHORE: [r=34,g=85,b=136]

     */
    enum ColorData {

        OCEAN(0x44447a), LAKE(0x336699), BEACH(0xa09077), SNOW(0xffffff),
        TUNDRA(0xbbbbaa), BARE(0x888888), SCORCHED(0x555555), TAIGA(0x99aa77),
        SHURBLAND(0x889977), TEMPERATE_DESERT(0xc9d29b), HIGH_TEMPERATE_DESERT(0xbdc499),
        TEMPERATE_RAIN_FOREST(0x448855), TEMPERATE_DECIDUOUS_FOREST(0x679459), 
        HIGH_TEMPERATE_DECIDUOUS_FOREST(0x4a773d),
        GRASSLAND(0x88aa55), SUBTROPICAL_DESERT(0xd2b98b), SHRUBLAND(0x889977),
        ICE(0x99ffff), MARSH(0x2f6666), TROPICAL_RAIN_FOREST(0x337755),
        TROPICAL_SEASONAL_FOREST(0x559944), COAST(0x33335a),
        LAKESHORE(0x225588);
        Color color;

        ColorData(int color) {
            this.color = new Color(color);
        }
    }
    

    public GraphImpl(Voronoi v, int numLloydRelaxations, Random r, int numIterationsForTectonicPlateCreation,
    		double nonBorderPlateContinentalProbability, double borderPlateContinentalProbability,
    		double sizeMultiplyer) 
    {
        super(r, sizeMultiplyer);
        this.numIterationsForTectonicPlateCreation = numIterationsForTectonicPlateCreation;
        this.nonBorderPlateContinentalProbability = nonBorderPlateContinentalProbability;
        this. borderPlateContinentalProbability = borderPlateContinentalProbability;
        TectonicPlate.resetIds();
        initVoronoiGraph(v, numLloydRelaxations, true);
        setupColors();
        createPoliticalRegions();
        setupRandomSeeds(r);
       	buildNoisyEdges();	
     }
 
    /**
     * This constructor doens't create tectonic plates or elevation.
      */
    public GraphImpl(Voronoi v, int numLloydRelaxations, Random r, double sizeMultiplyer) 
    {
        super(r, sizeMultiplyer);
        initVoronoiGraph(v, numLloydRelaxations, false);
		assignBorderToCorners();
        setupColors();
        setupRandomSeeds(r);
        buildNoisyEdges();
    }
    
    
    private void setupRandomSeeds(Random rand)
    {
    	for (Center c : centers)
    	{
    		c.noisyEdgeSeed = rand.nextLong();
    		c.treeSeed = rand.nextLong();
    		c.mountainSeed = rand.nextLong();
    		c.hillSeed = rand.nextLong();
    	}
    }
    
    private void setupColors()
    {
        OCEAN = ColorData.OCEAN.color;
        LAKE = ColorData.LAKE.color;
        BEACH = ColorData.BEACH.color;
        RIVER = new Color(0x225588);
   	
    }
    
    public void rebuildNoisyEdgesForCenter(Center center)
    {
    	noisyEdges.buildNoisyEdgesForCenter(center);
    }
    
    private void buildNoisyEdges()
    {
        noisyEdges = new NoisyEdges(scaleMultiplyer);  
        noisyEdges.buildNoisyEdges(this);	

    }
    

    @SuppressWarnings("unused")
	private void testPoliticalRegions()
    {
        for (Region region : regions)
        {
        	for (Center c : region.getCenters())
        	{
        		assert !c.isWater;
        		assert c.region == region;
        	}
        	
        	assert centers.stream().filter(c -> c.region == region).count() == region.size();
        }
        assert new HashSet<>(regions).size() == regions.size();
        for (Center c : centers)
        {
        	if (!c.isWater)
        	{
        		assert c.region != null;
        	}
        	else
        	{
        		assert c.region == null;
        	}
        	
        	if (c.region != null)
        	{
        		assert regions.stream().filter(reg -> reg.contains(c)).count() == 1;
        	}
        }
        assert regions.stream().mapToInt(reg -> reg.size()).sum() 
        		+ centers.stream().filter(c -> c.region == null).count() == centers.size();   	
    }
    
	public void drawRegionIndexes(Graphics2D g)
	{       
       	renderPolygons(g, c -> c.region == null ? Color.black : new Color(c.region.id, c.region.id, c.region.id));
	}
    
    /**
     * Creates political regions. When done, all non-ocean centers will have a political region
     * assigned.
     */
    private void createPoliticalRegions()
	{
		// Regions start as land Centers on continental plates.
    	regions = new ArrayList<>();
    	for (TectonicPlate plate : plates)
    	{
    		if (plate.type == PlateType.Continental)
    		{
    			Region region = new Region();	
    			plate.centers.stream().filter(c -> !c.isWater).forEach(c -> region.add(c));
    			regions.add(region);
    		}
    	}
    	    	    	
       	for (Region region : regions)
    	{
    		// For each region, divide it by land masses separated by water.
    		List<Set<Center>> dividedRegion = divideRegionByLand(region);
    		
        	if (dividedRegion.size() > 1)
	    	{
	    		// The region gets to keep only the largest land mass.
	    		Set<Center> biggest = dividedRegion.stream().max((l1, l2) -> Integer.compare(l1.size(), l2.size())).get();
	    		
    			// then for each small land mass:
    			for (Set<Center> regionPart : dividedRegion)
    			{
    				if (regionPart == biggest)
    					continue;
		        	// If that small land mass is connected by land to a different region, then add that land mass to that region.
	    			Region touchingRegion = findRegionTouching(regionPart);
	    			if (touchingRegion != null)
	    			{
	    				assert region != touchingRegion;
	    	        	region.removeAll(regionPart);
	    				touchingRegion.addAll(regionPart);
	    			}
		        	//Else leave it in this region
 	    		}
	    	}
    	}
       	
    	// Add to smallLandMasses any land which is not in a region.
    	List<Set<Center>> smallLandMasses = new ArrayList<>(); // stores small pieces of land not in a region.
       	for (Center center : centers)
       	{
       		if (!center.isWater && center.region == null)
       		{
       			Set<Center> landMass = breadthFirstSearch(c -> !c.isWater && c.region == null, center);
       			smallLandMasses.add(landMass);
       		}
       	}
       	
    	// For each region, if region is smaller than minPoliticalRegionSize, make it not a region and add it to smallLandMasses.
    	List<Integer> toRemove = new ArrayList<>();
    	for (int i : new Range(regions.size()))
    	{
    		if (regions.get(i).size() < minPoliticalRegionSize)
    		{
    			toRemove.add(i);
    			Set<Center> smallLandMass = new HashSet<>(regions.get(i).getCenters());
    			smallLandMasses.add(smallLandMass);
    		}
    	}
    	Collections.reverse(toRemove);
    	for (int i : toRemove)
    	{
    		regions.get(i).clear(); // This updates the region pointers in the Centers.
    		regions.remove(i);
    	}

    	// For each land mass in smallLandMasses, add it to the region nearest its centroid.
    	for (Set<Center> landMass : smallLandMasses)
    	{
    		Point centroid = GraphImpl.findCentroid(landMass);
    		Region closest = findClosestRegion(centroid);
    		if (closest != null)
    		{
    			closest.addAll(landMass);
    		}
    		else
    		{
    			// This will probably never happen because it means there are no regions on the map at all.
    			Region region = new Region();
    			region.addAll(landMass);
    			regions.add(region);
    		}
    	}
    	
    	// Set the id of each region.
    	for (int i : new Range(regions.size()))
    	{
    		regions.get(i).id = i;
    	}
	}
    
    /**
     * Finds the region closest (in terms of Cartesian distance) to the given point.
     */
    private Region findClosestRegion(Point point)
    {
    	Optional<Center> opt = centers.stream().filter(c -> c.region != null)
    		.min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));
    	
    	if (opt.isPresent())
    	{
    		assert opt.get().region != null;
    		return opt.get().region;
    	}
    	
    	// This could only happen if there are no regions on the graph.
    	return null;
    }
    
    public Center findClosestCenter(Point point)
    {
    	if (point.x > getWidth() || point.y > getHeight() || point.x < 0 || point.y < 0)
    	{
    		return null;
    	}
    	
    	Optional<Center> opt = centers.stream()
        		.min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));
    	
    	if (opt.isPresent())
    	{
    		return opt.get();
    	}
    	
    	return null;
    }
    
    /**
     * Searches for any region touching and polygon in landMass and returns it if found.
     * Otherwise returns null.
     * 
     * Assumes all Centers in landMass either all have the same region, or are all null.
     */
    private Region findRegionTouching(Set<Center> landMass)
    {
    	for (Center center : landMass)
    	{
    		for (Center n : center.neighbors)
    		{
    			if (n.region != center.region && n.region != null)
    			{
    				return n.region;
    			}
    		}
    	}
    	return null;
    }
    
    /**
     * Splits apart a region by parts connect by land (not including land from another region).
     * @param region
     * @return
     */
    private List<Set<Center>> divideRegionByLand(Region region)
    {
    	Set<Center> remaining = new HashSet<>(region.getCenters());
    	List<Set<Center>> dividedRegion = new ArrayList<>();
    	
    	// Start with the first center. Do a breadth-first search adding all connected
    	// centers which are of the same region and are not ocean.
    	while(!remaining.isEmpty())
    	{
    		Set<Center> landMass = breadthFirstSearch(c -> !c.isWater && c.region == region, 
	    			remaining.iterator().next());
	    	dividedRegion.add(landMass);
	    	remaining.removeAll(landMass);
    	}
    	
    	return dividedRegion;
    }
    
    public Set<Center> breadthFirstSearch(Function<Center, Boolean> accept, Center start)
    {
    	Set<Center> explored = new HashSet<>();
    	explored.add(start);
    	Set<Center> frontier = new HashSet<>();
    	frontier.add(start);
    	while (!frontier.isEmpty())  	
    	{
    		Set<Center> nextFrontier = new HashSet<>();
    		for (Center c : frontier)
    		{
    			explored.add(c);
    			// Add neighbors to the frontier.
    			for (Center n : c.neighbors)
    			{
    				if (!explored.contains(n) && accept.apply(n))
    				{
    					nextFrontier.add(n);
    				}
    			}
    		}
    		frontier = nextFrontier;
    	}
    	
    	return explored;
    }

	public void paintElevationUsingTrianges(Graphics2D g)
    {
    	super.paint(g, false, false, true, false, false, false, false);
    	
    	// Draw plate velocities.
//    	g.setColor(Color.yellow);
//    	for (TectonicPlate plate : plates)
//    	{
//    		Point centroid =  plate.findCentroid();
//    		g.fillOval((int)centroid.x - 5, (int)centroid.y - 5, 10, 10);
//    		PolarCoordinate vTemp = new PolarCoordinate(plate.velocity);
//    		// Increase the velocity to make it visible.
//    		vTemp.radius *= 100;
//    		Point velocity = vTemp.toCartesian();
//    		g.drawLine((int)centroid.x, (int)centroid.y, (int)(centroid.x + velocity.x), (int)(centroid.y + velocity.y));
//    	}
    }
    
    public void drawBorderWhite(Graphics2D g)
    {
        for (Center c : centers) 
        {
         	if (c.border)
                g.setColor(Color.white);
         	else
         		g.setColor(Color.BLACK);
                        
            drawUsingTriangles(g, c, false);
        } 	
        
        renderPolygons(g, c -> c.border ? Color.white : Color.black);
    }
    
    public int getWidth()
    {
    	return (int) bounds.width;
    }
    
    public int getHeight()
    {
    	return (int) bounds.height;
    }

    @Override
    protected Color getColor(Enum<?> biome) {
        return ((ColorData) biome).color;
    }

    @Override
    protected Enum<?> getBiome(Center p) {
    	double elevation = Math.sqrt((p.elevation - seaLevel) / (maxElevation - seaLevel));
    	
		if (p.isWater)
		{
			return ColorData.OCEAN;
		}
		else if (p.isWater)
		{
			if (elevation < 0.1)
			{
				return ColorData.MARSH;
			}
			if (elevation > 0.8)
			{
				return ColorData.ICE;
			}
			return ColorData.LAKE;
		}
		else if (p.coast)
		{
			return ColorData.BEACH;
		}
		else if (elevation > 0.8)
		{
			if (p.moisture > 0.50)
			{
				return ColorData.SNOW;
			}
			else if (p.moisture > 0.33)
			{
				return ColorData.TUNDRA;
			}
			else if (p.moisture > 0.16)
			{
				return ColorData.BARE;
			}
			else
			{
				return ColorData.SCORCHED;
			}
		}
		else if (elevation > 0.6)
		{
			if (p.moisture > 0.66)
			{
				return ColorData.TAIGA;
			}
			else if (p.moisture > 0.33)
			{
				return ColorData.SHRUBLAND;
			}
			else
			{
				return ColorData.HIGH_TEMPERATE_DESERT;
			}
		}
		else if (elevation > 0.45)
		{
			// Note: I added this else if case. It is not in Red Blob's blog.
			if (p.moisture > 0.83)
			{
				return ColorData.TEMPERATE_RAIN_FOREST;
			}
			else if (p.moisture > 0.50)
			{
				return ColorData.HIGH_TEMPERATE_DECIDUOUS_FOREST;
			}
			else if (p.moisture > 0.16)
			{
				return ColorData.GRASSLAND;
			}
			else
			{
				return ColorData.TEMPERATE_DESERT;
			}			
		}
		else if (elevation > 0.3)
		{
			if (p.moisture > 0.83)
			{
				return ColorData.TEMPERATE_RAIN_FOREST;
			}
			else if (p.moisture > 0.50)
			{
				return ColorData.TEMPERATE_DECIDUOUS_FOREST;
			}
			else if (p.moisture > 0.16)
			{
				return ColorData.GRASSLAND;
			}
			else
			{
				return ColorData.TEMPERATE_DESERT;
			}
		}
		else
		{
			if (p.moisture > 0.66)
			{
				return ColorData.TROPICAL_RAIN_FOREST;
			}
			else if (p.moisture > 0.33)
			{
				return ColorData.TROPICAL_SEASONAL_FOREST;
			}
			else if (p.moisture > 0.16)
			{
				return ColorData.GRASSLAND;
			}
			else
			{
				return ColorData.SUBTROPICAL_DESERT;
			}
		}
    }
    
    @Override 
    protected void assignCornerElevations()
    {
     	createTectonicPlates();
     	assignOceanAndContinentalPlates();
     	lowerOceanPlates();
		assignPlateCornerElivations();
  }
    
    private void assignPlateCornerElivations()
    {    	
//    	long startTime = System.currentTimeMillis();
    	    	
     	for (final TectonicPlate plate : plates)
    	{    		
     		
    		Set<Corner> explored = new HashSet<>();
    		
    		// Find all corners along plate boundaries.
    		Set<Corner> plateBoundaryCorners = new HashSet<>();
            for (Edge e : edges) 
            {
                if (e.d0.tectonicPlate == plate
                		&& e.d0.tectonicPlate != e.d1.tectonicPlate
                		&& e.v0 != null && e.v1 != null)
                {
                	if (e.v0 != null)
                		plateBoundaryCorners.add(e.v0);
                	if (e.v1 != null)
                		plateBoundaryCorners.add(e.v1);
                }
            }
    		    	
            // Simulate tectonic plate collisions.
            for (Edge e : edges) 
            {
                if (e.d0.tectonicPlate == plate 
                		&& e.d0.tectonicPlate != e.d1.tectonicPlate
                		&& e.v0 != null && e.v1 != null)
                {
                	double d0ConvergeLevel = calcLevelOfConvergence(e.d0.tectonicPlate.findCentroid(), e.d0.tectonicPlate.velocity, e.d1.tectonicPlate.findCentroid(),
                			e.d1.tectonicPlate.velocity);
                	
                	// If the plates are converging, rough them up a bit by calculating divergence per
                	// polygon. This brakes up long snake like islands.
                	if (d0ConvergeLevel > 0)
                	{
	                	d0ConvergeLevel =  calcLevelOfConvergence(e.d0.loc, e.d0.tectonicPlate.velocity, e.d1.loc,
	                			e.d1.tectonicPlate.velocity);
                	}
                	
                	
                 	e.v0.elevation += d0ConvergeLevel * collisionScale;
                	e.v1.elevation += d0ConvergeLevel * collisionScale;
                	explored.add(e.v0);
                	explored.add(e.v1);
                	
                	// Make sure the corner elevations don't go out of range.
                	e.v0.elevation = Math.min(e.v0.elevation, 1.0);
                   	e.v0.elevation = Math.max(e.v0.elevation, 0.0);
                	e.v1.elevation = Math.min(e.v1.elevation, 1.0);
                   	e.v1.elevation = Math.max(e.v1.elevation, 0.0);
                   	                   	
                	// Handle subduction of an ocean plate under a continental one.
                   	if (d0ConvergeLevel > 0 && e.d0.tectonicPlate.type == PlateType.Oceanic
                   			 && e.d1.tectonicPlate.type == PlateType.Continental)
                   	{
                   		for (Corner corner : e.d0.corners)
                   		{
                   			if (!plateBoundaryCorners.contains(corner))
                   			{
                   				corner.elevation -= d0ConvergeLevel * collisionScale;
                   				corner.elevation = Math.min(corner.elevation, 1.0);
                   				corner.elevation = Math.max(corner.elevation, 0.0);
                   				explored.add(corner);
                   			}
                   		}
                   	}
               }           	
            }

            // Do a search starting at the corners along the borders. At each step, assign each corner's
            // elevation to the average of its self and its explored neighbors.
  			boolean cornerFound;
   			Set<Corner> exploredThisIteration = new HashSet<>();
   		    do
            {
   		    	cornerFound = false;
   		    	explored.addAll(exploredThisIteration);
   		    	exploredThisIteration.clear();
    			for (Corner exCorner : explored)
	    		{
	    			for (Corner corner : exCorner.adjacent)
	    			{
	    				if (!explored.contains(corner) && !exploredThisIteration.contains(corner))
	    				{
		    				
		    				for (Center center : corner.touches)
		    					if (center.tectonicPlate == plate)
		    					{
		    						// Set the corner's elevation to the average of its self and its
		    						// explored neighbors.
		    						double sum = corner.elevation;
		    						double count = 1;
		    						for (Corner a : corner.adjacent)
		    							if (explored.contains(a) || exploredThisIteration.contains(a))
		    							{
		    								sum += a.elevation;
		    								count++;
		    							}
		    						corner.elevation = sum / count;
		    						
		    						exploredThisIteration.add(corner);
		    						cornerFound = true;
		    						continue;
		    					}
	    				}
	    			}
	    		}
	        }
            while (cornerFound);
 
    	}
//     	Logger.println("Corner elevation time: " + (System.currentTimeMillis() - startTime)/1000.0);
   }
        
    /**
     * Lower the elevation of all ocean plates so they are likely to be water.
     */ 
    private void lowerOceanPlates()
    {
        for (Corner corner : corners) {
            int numOceanic = 0;
            for (Center center : corner.touches) 
            {
            	if (center.tectonicPlate.type == PlateType.Oceanic)
            		numOceanic++;
            }
            double oceanicRatio = ((double)numOceanic)/corner.touches.size();
            corner.elevation = oceanicRatio*seaFloorLevel + (1.0 - oceanicRatio)*continentalPlateLevel;
       }
    }
       
    @Override
    protected void assignOceanCoastAndLand()
    {
		for (Center c1 : centers)
		{
			c1.isWater = c1.elevation < seaLevel;
		}
		
		assignBorderToCorners();

		// Copied from super.assignOceanCoastAndLand()
		// Determine if each corner is coast or water.
		for (Center c : centers)
		{
			updateCoast(c);
		}

		// Copied from super.assignOceanCoastAndLand()
		// Determine if each corner is ocean, coast, or water.
		for (Corner c : corners)
		{
			int numOcean = 0;
			int numLand = 0;
			for (Center center : c.touches)
			{
				numOcean += center.isWater ? 1 : 0;
				numLand += !center.isWater ? 1 : 0;
			}
			c.ocean = numOcean == c.touches.size();
			c.coast = numOcean > 0 && numLand > 0;
			c.water = (numLand != c.touches.size()) && !c.coast;
		}
    }
    
    public void updateCoast(Center c)
    {
		int numOcean = 0;
		int numLand = 0;
		for (Center center : c.neighbors)
		{
			numOcean += center.isWater ? 1 : 0;
			numLand += !center.isWater ? 1 : 0;
		}
		c.coast = numOcean > 0 && numLand > 0;
    }
    
    private void assignBorderToCorners()
    {
		for (Center c1 : centers)
		{
			for (final Corner corner : c1.corners)
			{
				if (corner.border)
				{
					c1.border = true;
					break;
				}
			}
		}

    }
    	
    private void assignOceanAndContinentalPlates()
    {
    	for (TectonicPlate plate : plates)
    	{
    		if (rand.nextDouble() > nonBorderPlateContinentalProbability)
    		{
    			plate.type = PlateType.Oceanic;
    		}
    		else
    		{
     			plate.type = PlateType.Continental;
    		}
    	}
    	
    	// Set the type for plates that touch the borders, overwriting any settings from above.
    	Set<TectonicPlate> borderPlates = new HashSet<TectonicPlate>();
    	for (Center c : centers)
    	{
    		for (Corner corner : c.corners)
	    		if (corner.border)
	    		{
	    			borderPlates.add(c.tectonicPlate);
		    			continue;
	    		}
    	}
    	for (TectonicPlate plate : borderPlates)
    	{
    		if (rand.nextDouble() < borderPlateContinentalProbability)
    			plate.type = PlateType.Continental; 
    		else
    			plate.type = PlateType.Oceanic;
    	}
    }
    
    private void createTectonicPlates()
    {   	   	
//    	long startTime = System.currentTimeMillis();
    	// First, assign a unique plate id and a random growth probability to each center.
    	RandomGenerator randomData = new JDKRandomGenerator(); 
    	randomData.setSeed(rand.nextLong());
    	// A beta distribution is nice because (with the parameters I use) it creates a few plates
    	// with high growth probabilities and many with low growth probabilities. This makes plate creation
    	// faster and creates a larger variety of plate sizes than a uniform distribution would.
		BetaDistribution betaDist = new BetaDistribution(randomData, 1, 3, BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
    	for (Center c : centers)
    	{
    		c.tectonicPlate = new TectonicPlate(betaDist.sample(), centers);
       	}
    	
   		for (Center c : centers)
   		{
			c.updateNeighborsNotInSamePlateCount();
   		}

    	
     	for (int curIteration = 0; curIteration < numIterationsForTectonicPlateCreation; curIteration++)
    	{
     		// Sample some centers and choose the one with the least number of neighbors which
     		// are not on this plate (greater than 0). This makes the plate boundaries more smooth.
      		Center least = null;
     		for (int i = 0; i < plateBoundarySmoothness; i++)
     		{
        		final Center cTemp = centers.get(rand.nextInt(centers.size()));
           		if (cTemp.neighborsNotInSamePlateCount == 0)
        			continue;
          		
          		if (least == null || cTemp.neighborsNotInSamePlateCount < least.neighborsNotInSamePlateCount)
          		{
          			least = cTemp;
          		}
        		
    		}
     		if (least == null)
     		{
     			continue;
     		}
     		final Center c = least;
     		
    		// Keep the merge with probability equal to the score of the new tectonic plate.
    		if (rand.nextDouble() < c.tectonicPlate.growthProbability)
    		{
    			// Choose a center at random.
	    		// Choose one of it's neighbors not in the same plate.
	    		List<Center> neighborsNotInSamePlate = Helper.filter(c.neighbors, new Function<Center, Boolean>()
	    				{
							public Boolean apply(Center otherC) 
							{
								return c.tectonicPlate != otherC.tectonicPlate;
							}
	    				});
	    		Center neighbor = neighborsNotInSamePlate.get(rand.nextInt(neighborsNotInSamePlate.size()));
    		
     		
    	   		// Merge the neighbor into c's plate.
        		neighbor.tectonicPlate = c.tectonicPlate;
        		c.updateNeighborsNotInSamePlateCount();
        		for (Center n : c.neighbors)
        			n.updateNeighborsNotInSamePlateCount();
        		neighbor.updateNeighborsNotInSamePlateCount();
        		for (Center n : neighbor.neighbors)
        			n.updateNeighborsNotInSamePlateCount();
     		}
    	}
 
     	// Find the plates still on the map.
     	plates = new HashSet<TectonicPlate>();
     	for (Center c : centers)
     	{
     		plates.add(c.tectonicPlate);
     	}
     	
     	// Setup tectonic plate velocities randomly. The maximum speed of a plate is 1.
     	for (TectonicPlate plate : plates)
     	{
     		plate.velocity = new PolarCoordinate(rand.nextDouble() * 2 * Math.PI, rand.nextDouble());
     	}
     	
     	// Store which Centers are in each plate.
     	for (Center c : centers)
     	{
     		c.tectonicPlate.centers.add(c);
     	}
     	
//     	Logger.println("Plate time: " + (System.currentTimeMillis() - startTime)/1000.0);
    }        
    
    /**
     * Returns the amount c1 and c2 are converging. This is between -1 and 1.
     * @param c1 A center along a tectonic plate border.
     * @param c1Velocity The velocity of the plate c1 is on.
     * @param c2 A center along a tectonic plate border: not the same tectonic plate as c1
     * @param c2Velocity The velocity of the plate c2 is on.
     */
    private double calcLevelOfConvergence(Point p1, PolarCoordinate p1Velocity, Point p2,
    		PolarCoordinate c2Velocity)
    {
		return 0.5 * calcUnilateralLevelOfConvergence(p1, p1Velocity, p2)
				+ 0.5 * calcUnilateralLevelOfConvergence(p2, c2Velocity, p1);
    }
    
    /**
     * Returns the amount c1 is moving toward c2, ignoring movement of c2.
     */
   public static double calcUnilateralLevelOfConvergence(Point p1, PolarCoordinate p1Velocity, Point p2)
    {

		// Find the angle from c1.location to c2.location:
    	Point cDiff = p2.subtract(p1);
     	double diffAngle = Math.atan2(cDiff.y, cDiff.x);
		
		// If the 2 angles are the same, return at most 0.5. If they are opposite, return  at least -0.5. 
		// If they are orthogonal, return 0.0.
    	
		double theta = calcAngleDifference(p1Velocity.angle, diffAngle);
		double scale = p1Velocity.radius * Math.cos(theta);
		return scale;
    }
    
    
    /**
     * Calculates the minimum distance (in radians) from angle a1 to angle a2.
     * The result will be in the range [0, pi].
     * @param a1 An angle in radians. This must be between 0 and 2*pi.
     * @param a2 An angle in radians. This must be between 0 and 2*pi.
     * @return
     */
    private static double calcAngleDifference(double a1, double a2)
    {
    	while (a1 < 0)
    		a1 += 2*Math.PI;
    	while (a2 < 0)
    		a2 += 2*Math.PI;
    	
    	if (a1 < 0 || a1 > 2*Math.PI)
    		throw new IllegalArgumentException();
    	if (a2 < 0 || a2 > 2*Math.PI)
    		throw new IllegalArgumentException();
    	
    	if (a1 - a2 > Math.PI)
    		a1 -= 2*Math.PI;
    	else if (a2 - a1 > Math.PI)
    		a2 -= 2*Math.PI;
    	double result = Math.abs(a1 - a2);
    	assert result <= Math.PI;
    	return result;
    }
    
	public static Point findCentroid(Set<Center> centers)
	{
		Point centroid = new Point(0, 0);
		for (Center c : centers)
		{
			Point p = c.loc;
			centroid.x += p.x;
			centroid.y += p.y;
		}
		centroid.x /= centers.size();
		centroid.y /= centers.size();
		
		return centroid;
	}
	
	public Region findRegionById(int id)
	{
		for (Region region : regions)
		{
			if (region.id == id)
			{
				return region;
			}
		}

		return null;
	}


}
