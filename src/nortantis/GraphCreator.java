package nortantis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.nio.file.Paths;
import java.util.Random;

import hoten.voronoi.nodename.as3delaunay.Voronoi;
import nortantis.MapSettings.LineStyle;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

/**
 * TestDriver.java
 *
 * @author Connor
 */
public class GraphCreator 
{
    // Zero is most random. Higher values make the polygons more uniform shaped.
    private static final int numLloydRelaxations = 0;
    // Higher values will make larger plates, but fewer of them.
	private static final int tectonicPlateIterationMultiplier = 30;
	
    public static WorldGraph createGraph(double width, double height, int numSites, double borderPlateContinentalProbability,
    		double nonBorderPlateContinentalProbability, Random r, double sizeMultiplyer, LineStyle lineStyle, double pointPrecision)
    {
		double startTime = System.currentTimeMillis();
        
        //make the initial underlying voronoi structure
        final Voronoi v = new Voronoi(numSites, width, height, r, null);

         //assemble the voronoi structure into a usable graph object representing a map
        final WorldGraph graph = new WorldGraph(v, numLloydRelaxations, r, numSites * tectonicPlateIterationMultiplier,
    		   nonBorderPlateContinentalProbability, borderPlateContinentalProbability, sizeMultiplyer, lineStyle, pointPrecision);
        
		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Time to generate graph (in seconds): " + elapsedTime
				/ 1000.0);

//
//        final BufferedImage img = new BufferedImage((int)width, (int)height, BufferedImage.TYPE_INT_RGB);
//        Graphics2D g = img.createGraphics();
//        boolean drawPlates = false;
//        boolean drawElevations = false;
//        boolean drawNoisyEdges = true;
//        boolean drawLandAndOceanBlackAndWhiteOnly = false;
//        graph.paint(g, true, drawPlates, drawElevations, drawNoisyEdges, drawLandAndOceanBlackAndWhiteOnly, false,
//        		false);
//        // Save the image to a file.
//        File outputfile = new File("biomes.png");
//        ImageIO.write(img, "png", outputfile);
        
        return graph;
    }
    
    public static BufferedImage createHeightMap(WorldGraph graph, Random rand)
    {
		double startTime = System.currentTimeMillis();
		
        // Draw elevation map with tectonic plate boundaries. 
        BufferedImage heightMap = new BufferedImage(graph.getWidth(), graph.getHeight(), BufferedImage.TYPE_USHORT_GRAY);
        Graphics2D g = heightMap.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, graph.getWidth(), graph.getHeight());
        graph.paintElevationUsingTrianges(g);
         
        heightMap = ImageHelper.blur(heightMap, (int)IconDrawer.findMeanPolygonWidth(graph) / 2);
       
        // Use a texture generated from mountain elevation to carve mountain shapes into the areas with high elevation.
        BufferedImage mountains = ImageHelper.read(Paths.get(AssetsPath.get(), "internal/mountain texture.png").toString());
        if (mountains.getType() != BufferedImage.TYPE_USHORT_GRAY)
        {
        	mountains = ImageHelper.convertImageToType(mountains, BufferedImage.TYPE_USHORT_GRAY);
        }
        mountains = ImageHelper.scaleByWidth(mountains, (int)(mountains.getWidth() * MapCreator.calcSizeMultiplier(graph.getWidth()) * 0.25f));
        BufferedImage mountainTexture = BackgroundGenerator.generateUsingWhiteNoiseConvolution(rand, mountains, graph.getHeight(), graph.getWidth(), false);
        //ImageHelper.write(mountainTexture, "mountainTexture.png");
        subtractTextureFromHeightMapUsingSeaLevel(heightMap, mountainTexture);
        mountainTexture = null;
        
		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Time to draw heightmap: " + elapsedTime
				/ 1000.0);

        return heightMap;	
    }
    
    private static void subtractTextureFromHeightMapUsingSeaLevel(BufferedImage image, BufferedImage texture)
    {
        Raster textureRaster = texture.getRaster();
		WritableRaster out = image.getRaster();
		float maxPixelValue = (float)ImageHelper.getMaxPixelValue(image);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				float elevation = out.getSample(x, y, 0);
				float scale;
				if (elevation > WorldGraph.seaLevel*maxPixelValue)
				{
					scale = Math.abs(elevation - WorldGraph.seaLevel*maxPixelValue) / maxPixelValue;
				}
				else
				{
					scale = 0f;
				}

				float tValue = maxPixelValue - textureRaster.getSample(x, y, 0);
				int newValue = (int)((elevation - scale * (tValue)));
				if (newValue < 0)
				{
					newValue = 0;
				}
				out.setSample(x, y, 0, newValue);
			}
		}

    }
    
    public static WorldGraph createSimpleGraph(double width, double height, int numSites, Random r, double sizeMultiplyer, double pointPrecision)
    {
        // Zero is most random. Higher values make the polygons more uniform shaped.
        final int numLloydRelaxations = 0;
 
        //make the initial underlying voronoi structure
        final Voronoi v = new Voronoi(numSites, width, height, r, null);

         //assemble the voronoi structure into a usable graph object representing a map
        final WorldGraph graph = new WorldGraph(v, numLloydRelaxations, r, sizeMultiplyer, pointPrecision);
        
        return graph;
    }


}
