package nortantis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import hoten.voronoi.nodename.as3delaunay.Voronoi;
import util.Logger;

/**
 * TestDriver.java
 *
 * @author Connor
 */
public class GraphCreator 
{

    public static GraphImpl createGraph(double width, double height, int numSites, double borderPlateContinentalProbability,
    		double nonBorderPlateContinentalProbability, Random r, double sizeMultiplyer) throws IOException 
    {
		double startTime = System.currentTimeMillis();

        // Zero is most random. Higher values make the polygons more uniform shaped.
        final int numLloydRelaxations = 0;
        // Higher values will make larger plates.
        final int tectonicPlateIterationMultiplier = 30;
        
        //make the initial underlying voronoi structure
        final Voronoi v = new Voronoi(numSites, width, height, r, null);

         //assemble the voronoi structure into a usable graph object representing a map
        final GraphImpl graph = new GraphImpl(v, numLloydRelaxations, r, numSites * tectonicPlateIterationMultiplier,
    		   nonBorderPlateContinentalProbability, borderPlateContinentalProbability, sizeMultiplyer);
        
		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Time to generate graph (in seconds): " + elapsedTime
				/ 1000.0);

        // Draw elevation map with tectonic plate boundaries. 
        {
	        final BufferedImage elevationImg = new BufferedImage((int)width, (int)height, BufferedImage.TYPE_INT_RGB);
	        Graphics2D g = elevationImg.createGraphics();
	        g.setColor(Color.BLACK);
	        g.fillRect(0, 0, (int)width, (int)height);
	        graph.paintElevationUsingTrianges(g);
	        File elevationfile = new File("elevation.png");
	        ImageIO.write(elevationImg, "png", elevationfile);
       }
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
