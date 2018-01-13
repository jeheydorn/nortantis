package nortantis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Random;

import javax.imageio.ImageIO;

import hoten.voronoi.nodename.as3delaunay.Voronoi;
import util.ImageHelper;
import util.Logger;

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
	
    public static GraphImpl createGraph(double width, double height, int numSites, double borderPlateContinentalProbability,
    		double nonBorderPlateContinentalProbability, Random r, double sizeMultiplyer)
    {
		double startTime = System.currentTimeMillis();
        
        //make the initial underlying voronoi structure
        final Voronoi v = new Voronoi(numSites, width, height, r, null);

         //assemble the voronoi structure into a usable graph object representing a map
        final GraphImpl graph = new GraphImpl(v, numLloydRelaxations, r, numSites * tectonicPlateIterationMultiplier,
    		   nonBorderPlateContinentalProbability, borderPlateContinentalProbability, sizeMultiplyer);
        
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
    
    public static BufferedImage createHeightMap(GraphImpl graph, Random rand, double sizeMultiplyer)
    {
		double startTime = System.currentTimeMillis();
		
        // Draw elevation map with tectonic plate boundaries. 
        BufferedImage heightMap = new BufferedImage(graph.getWidth(), graph.getHeight(), BufferedImage.TYPE_USHORT_GRAY);
        Graphics2D g = heightMap.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, graph.getWidth(), graph.getHeight());
        graph.paintElevationUsingTrianges(g);
         
        heightMap = ImageHelper.convolveGrayscale(heightMap, ImageHelper.createGaussianKernel((int)(IconDrawer.findMeanPolygonWidth(graph) / 2)), false);
        ImageHelper.write(heightMap, "heightMap.png");
       
        // Use a texture generated from mountain elevation to carve mountain shapes into the areas with high elevation.
        BufferedImage mountains = ImageHelper.read(Paths.get("assets/20180113084702_1450196252.png").toString());
        mountains = ImageHelper.scaleByWidth(mountains, (int)(mountains.getWidth() * sizeMultiplyer * 0.25f));
        BufferedImage mountainTexture = BackgroundGenerator.generateUsingWhiteNoiseConvolution(rand, mountains, graph.getHeight(), graph.getWidth(), false);
        ImageHelper.write(mountainTexture, "mountainTexture.png");
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
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				double elevation = out.getSample(x, y, 0);
				double scale;
				scale = Math.abs(elevation - GraphImpl.seaLevel*255.0) / 255.0;

				double tValue = 255f - textureRaster.getSample(x, y, 0);
				int newValue = (int)((elevation - scale * (tValue)));
				if (newValue < 0)
				{
					newValue = 0;
				}
				out.setSample(x, y, 0, newValue);
			}
		}

    }
    
    private static void subtractFractalTextureFromHeightMap(BufferedImage image, Random rand)
    {
    	float fractalScale = 0.025f;
        BufferedImage texture = FractalBGGenerator.generate(rand, 1.2f, image.getWidth(), image.getHeight(), fractalScale);

        Raster textureRaster = texture.getRaster();
		WritableRaster out = image.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				double elevation = out.getSample(x, y, 0);

				double tValue = textureRaster.getSample(x, y, 0);
				int newValue = (int)((elevation + (tValue - (0.5f - fractalScale/2f) * 255f)));
				newValue = Math.max(0, Math.min(newValue, 255));
				if (newValue < 0)
				{
					newValue = 0;
				}
				out.setSample(x, y, 0, newValue);
			}
		}

    }

    
    public static GraphImpl createSimpleGraph(double width, double height, int numSites, Random r, double sizeMultiplyer)
    {
        // Zero is most random. Higher values make the polygons more uniform shaped.
        final int numLloydRelaxations = 0;
 
        //make the initial underlying voronoi structure
        final Voronoi v = new Voronoi(numSites, width, height, r, null);

         //assemble the voronoi structure into a usable graph object representing a map
        final GraphImpl graph = new GraphImpl(v, numLloydRelaxations, r, sizeMultiplyer);
        
        return graph;
    }
    
    public static void main(String[] args) throws IOException 
    {
    	// 33198540789208L matching diverging plates.
    	// 33426595304007L a long snaky island.
    	// nice divergence with 12000 and 0 1 for probs
        final long seed = System.nanoTime();
        System.out.println("seed: " + seed);
        final Random r = new Random(seed);
    	createGraph(1024 * 2, 576 * 2, 1500, 1.0, 1.0, r, 1.0);
    	Logger.println("Done.");
    }

}
