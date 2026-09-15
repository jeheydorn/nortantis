package nortantis;

import nortantis.geom.Point;
import nortantis.geom.PolarCoordinate;
import nortantis.graph.voronoi.Center;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorldGraphTest
{
	@BeforeAll
	public static void setUpBeforeClass()
	{
		PlatformFactory.setInstance(new AwtFactory());
	}
	@Test
	public void calcUnilateralLevelOfConvergenceTest()
	{
		{
			Point p1 = new Point(1, -1);
			PolarCoordinate p1Velocity = new PolarCoordinate((3.0 / 4.0) * Math.PI, 0.6);
			Point p2 = new Point(-1, 1);
			PolarCoordinate p2Velocity = new PolarCoordinate(Math.PI / 3, 0.9);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);
			assertTrue(actual > 0);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
			assertTrue(actual < 0);
		}

		{
			Point p1 = new Point(1, -1);
			PolarCoordinate p1Velocity = new PolarCoordinate((3.0 / 4.0) * Math.PI, 0.6);
			Point p2 = new Point(-1, 1);
			PolarCoordinate p2Velocity = new PolarCoordinate(Math.PI / 3, 0.9);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);
			assertTrue(actual > 0);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
			assertTrue(actual < 0);
		}

		{
			Point p1 = new Point(-1, 0.5);
			PolarCoordinate p1Velocity = new PolarCoordinate((1.0 / 3.0) * Math.PI, 0.1);
			Point p2 = new Point(1, 0.5);
			PolarCoordinate p2Velocity = new PolarCoordinate((1.0 / 3.0) * Math.PI, 0.99);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);
			assertTrue(actual > 0);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
			assertTrue(actual < 0);
		}

		{
			Point p1 = new Point(-1, 0.5);
			PolarCoordinate p1Velocity = new PolarCoordinate((1.0 / 2.0) * Math.PI, 0.1);
			Point p2 = new Point(1, 0.5);
			PolarCoordinate p2Velocity = new PolarCoordinate((1.0 / 2.0) * Math.PI, 0.99);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);
			assertEquals(0, actual, 0.00001);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
			assertEquals(0, actual, 0.00001);
		}

		{
			Point p1 = new Point(-1, 0.5);
			PolarCoordinate p1Velocity = new PolarCoordinate((1.0 / 2.0) * Math.PI, 0.0);
			Point p2 = new Point(1, 0.5);
			PolarCoordinate p2Velocity = new PolarCoordinate(0, 0.0);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);
			assertEquals(0, actual, 0.00001);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
			assertEquals(0, actual, 0.00001);
		}

	}

	/**
	 * Checks that divergence levels are the same as convergence levels.
	 */
	@Test
	public void diverganceTest()
	{
		Point p1 = new Point(0, 0);
		PolarCoordinate p1Velocity = new PolarCoordinate(0.0, 1.0);
		Point p2 = new Point(1, 0);
		PolarCoordinate p2Velocity = new PolarCoordinate(Math.PI, 1.0);

		double convergence1 = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);

		double convergence2 = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);

		p1Velocity = new PolarCoordinate(Math.PI, 1.0);
		p2Velocity = new PolarCoordinate(0.0, 1.0);

		double divergence1 = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);

		double divergence2 = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);

		assertEquals(convergence1 * -1, divergence1, 0.000001);
		assertEquals(convergence2 * -1, divergence2, 0.000001);
	}

	/**
	 * Some land shapes use more continental plates than regions, so political region creation has to merge plates down to the region count.
	 * The merged land must keep a region.
	 */
	@Test
	public void everyLandShapeCreatesTheRequestedRegionsAndGivesAllLandARegion()
	{
		for (LandShape landShape : LandShape.values())
		{
			for (int regionCount : new int[] { 2, 7 })
			{
				for (long seed = 1; seed <= 3; seed++)
				{
					WorldGraph graph = createGraph(landShape, regionCount, seed);
					String description = landShape.name() + ", regionCount " + regionCount + ", seed " + seed;
					assertEquals(regionCount, graph.regions.size(), description);
					for (Center center : graph.centers)
					{
						if (!center.isWater)
						{
							assertNotNull(center.region, "Land center without a region: " + description);
						}
					}
				}
			}
		}
	}

	@Test
	public void supercontinentKeepsMostLandInOneLandmass()
	{
		double totalLargestLandmassFraction = 0;
		int seedCount = 5;
		for (long seed = 1; seed <= seedCount; seed++)
		{
			WorldGraph graph = createGraph(LandShape.Supercontinent, 8, seed);
			int landCount = 0;
			int largestLandmassSize = 0;
			Set<Center> visited = new HashSet<>();
			for (Center center : graph.centers)
			{
				if (center.isWater)
				{
					continue;
				}
				landCount++;
				if (!visited.contains(center))
				{
					Set<Center> landmass = graph.breadthFirstSearch(c -> !c.isWater, center);
					visited.addAll(landmass);
					largestLandmassSize = Math.max(largestLandmassSize, landmass.size());
				}
			}
			totalLargestLandmassFraction += largestLandmassSize / (double) landCount;
		}
		double averageLargestLandmassFraction = totalLargestLandmassFraction / seedCount;
		assertTrue(averageLargestLandmassFraction > 0.9, "Average fraction of land in the largest landmass: " + averageLargestLandmassFraction);
	}

	@Test
	public void landlockedIsMostlyLand()
	{
		for (long seed = 1; seed <= 5; seed++)
		{
			WorldGraph graph = createGraph(LandShape.Landlocked, 8, seed);
			long landCount = graph.centers.stream().filter(c -> !c.isWater).count();
			double landFraction = landCount / (double) graph.centers.size();
			assertTrue(landFraction > 0.75, "Land fraction for seed " + seed + ": " + landFraction);
		}
	}

	@Test
	public void coastlineHasBothLandAndOceanAlongTheMapBorder()
	{
		for (long seed = 1; seed <= 5; seed++)
		{
			WorldGraph graph = createGraph(LandShape.Coastline, 8, seed);
			boolean hasBorderLand = graph.centers.stream().anyMatch(c -> c.isBorder && !c.isWater);
			boolean hasBorderWater = graph.centers.stream().anyMatch(c -> c.isBorder && c.isWater);
			assertTrue(hasBorderLand && hasBorderWater, "Seed " + seed + " has border land: " + hasBorderLand + ", border water: " + hasBorderWater);
		}
	}

	/**
	 * Continents and Supercontinent add oceanic plates between the map edges and continental plates near them, so continental land should
	 * rarely reach the edges.
	 */
	@Test
	public void continentalLandRarelyTouchesTheMapEdgesForCentralLandShapes()
	{
		for (LandShape landShape : new LandShape[] { LandShape.Continents, LandShape.Supercontinent })
		{
			for (int regionCount : new int[] { 3, 8 })
			{
				int seedCount = 5;
				double totalBorderContinentalLandFraction = 0;
				for (long seed = 1; seed <= seedCount; seed++)
				{
					WorldGraph graph = createGraph(landShape, regionCount, seed);
					long borderCount = graph.centers.stream().filter(c -> c.isBorder).count();
					long borderContinentalLandCount = graph.centers.stream().filter(c -> c.isBorder && !c.isWater && c.tectonicPlate.type == PlateType.Continental).count();
					totalBorderContinentalLandFraction += borderContinentalLandCount / (double) borderCount;
				}
				double average = totalBorderContinentalLandFraction / seedCount;
				assertTrue(average < 0.02, "Average fraction of border centers that are continental land for " + landShape.name() + " with " + regionCount + " regions: " + average);
			}
		}
	}

	private static WorldGraph createGraph(LandShape landShape, int regionCount, long seed)
	{
		final double width = 1024;
		final double height = 768;
		return GraphCreator.createGraph(width, height, 3000, new Random(seed), width / 4096.0, MapSettings.LineStyle.Jagged, MapSettings.defaultPointPrecision, true,
				MapSettings.defaultLloydRelaxationsScale, false, 0, false, false, landShape, regionCount);
	}

}
