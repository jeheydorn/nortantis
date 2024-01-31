package nortantis;

import java.nio.file.Paths;
import java.util.Random;

import nortantis.MapSettings.LineStyle;
import nortantis.geom.IntDimension;
import nortantis.graph.voronoi.nodename.as3delaunay.Voronoi;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
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
	public static WorldGraph createGraph(int width, int height, int numSites, double borderPlateContinentalProbability,
			double nonBorderPlateContinentalProbability, Random r, double resolutionScale, LineStyle lineStyle, double pointPrecision,
			boolean createElevationBiomesAndRegions, double lloydRelaxationsScale, boolean areRegionBoundariesVisible)
	{
		// double startTime = System.currentTimeMillis();

		IntDimension graphSize = getGraphDimensionsWithStandardWidth(new IntDimension(width, height));
		// make the initial underlying voronoi structure
		final Voronoi v = new Voronoi(numSites, graphSize.width, graphSize.height, r);

		// assemble the voronoi structure into a usable graph object representing a map
		final WorldGraph graph = new WorldGraph(v, lloydRelaxationsScale, r, nonBorderPlateContinentalProbability,
				borderPlateContinentalProbability, resolutionScale, lineStyle, pointPrecision, createElevationBiomesAndRegions,
				areRegionBoundariesVisible);
		graph.scale(width, height);
		graph.buildNoisyEdges(lineStyle, false);


		// Debug code to log elapsed time.
		// double elapsedTime = System.currentTimeMillis() - startTime;
		// Logger.println("Time to generate graph (in seconds): " + elapsedTime
		// / 1000.0);

		return graph;
	}

	public static Image createHeightMap(WorldGraph graph, Random rand)
	{
		double startTime = System.currentTimeMillis();

		// Draw elevation map with tectonic plate boundaries.
		Image heightMap = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Grayscale16Bit);
		Painter p = heightMap.createPainter();
		p.setColor(Color.black);
		p.fillRect(0, 0, graph.getWidth(), graph.getHeight());
		graph.paintElevationUsingTrianges(p);

		heightMap = ImageHelper.blur(heightMap, (int) IconDrawer.findMeanCenterWidth(graph) / 2, false);

		// Use a texture generated from mountain elevation to carve mountain shapes into the areas with high elevation.
		Image mountains = ImageHelper.read(Paths.get(AssetsPath.getInstallPath(), "internal/mountain texture.png").toString());
		if (mountains.getType() != ImageType.Grayscale16Bit)
		{
			mountains = ImageHelper.convertImageToType(mountains, ImageType.Grayscale16Bit);
		}
		mountains = ImageHelper.scaleByWidth(mountains,
				(int) (mountains.getWidth() * MapCreator.calcSizeMultiplier(graph.getWidth()) * 0.25f));
		Image mountainTexture = BackgroundGenerator.generateUsingWhiteNoiseConvolution(rand, mountains, graph.getHeight(), graph.getWidth(),
				false);
		// ImageHelper.write(mountainTexture, "mountainTexture.png");
		subtractTextureFromHeightMapUsingSeaLevel(heightMap, mountainTexture);
		mountainTexture = null;

		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Time to draw heightmap: " + elapsedTime / 1000.0);

		return heightMap;
	}

	private static void subtractTextureFromHeightMapUsingSeaLevel(Image image, Image texture)
	{
		float maxPixelValue = (float) image.getMaxPixelLevel();
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				float elevation = image.getGrayLevel(x, y);
				float scale;
				if (elevation > WorldGraph.seaLevel * maxPixelValue)
				{
					scale = Math.abs(elevation - WorldGraph.seaLevel * maxPixelValue) / maxPixelValue;
				}
				else
				{
					scale = 0f;
				}

				float tValue = maxPixelValue - texture.getGrayLevel(x, y);
				int newValue = (int) ((elevation - scale * (tValue)));
				if (newValue < 0)
				{
					newValue = 0;
				}
				image.setGrayLevel(x, y, newValue);
			}
		}

	}

	public static WorldGraph createSimpleGraph(int width, int height, int numSites, Random r, double resolutionScale,
			boolean isForFrayedBorder)
	{
		// Zero is most random. Higher values make the polygons more uniform shaped. Value should be between 0 and 1.
		final double lloydRelaxationsScale = 0.0;

		IntDimension graphSize = getGraphDimensionsWithStandardWidth(new IntDimension(width, height));
		// make the initial underlying voronoi structure
		final Voronoi v = new Voronoi(numSites, graphSize.width, graphSize.height, r);

		// assemble the voronoi structure into a usable graph object representing a map
		final WorldGraph graph = new WorldGraph(v, lloydRelaxationsScale, r, resolutionScale, MapSettings.defaultPointPrecision,
				isForFrayedBorder);
		graph.scale(width, height);
		graph.buildNoisyEdges(LineStyle.Jagged, isForFrayedBorder);


		return graph;
	}

	/**
	 * Used to convert dimensions for a graph from draw space to a standardized size for graph space when initially creating the graph. The
	 * reason I'm doing this it's because originally graphs were created at the size of the map we were drawing, but this created subtle
	 * bugs when the graph generated differently at different resolutions because of truncating floating point values, and limitations on
	 * floating point precision. My solution is to always generate the graph at the same size, no matter they draw resolution, then scale it
	 * to the resolution to draw at.
	 */
	private static IntDimension getGraphDimensionsWithStandardWidth(IntDimension drawResolution)
	{
		// It doesn't really matter what this value is. I'm using the value that used to be the width of a graph drawn at medium resolution,
		// since that's most likely to be backwards compatible with older maps.
		final int standardWidth = 4096;

		return new IntDimension(standardWidth, (int)(drawResolution.height * (standardWidth / drawResolution.width)));
	}


}
