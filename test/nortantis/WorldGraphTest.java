package nortantis;

import nortantis.geom.Point;
import nortantis.geom.PolarCoordinate;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.nodename.as3delaunay.Voronoi;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
					// A typical world size. In small worlds, plate seeds are only a few polygons apart, which leaves little room between a
					// continental plate and the edge.
					WorldGraph graph = createGraph(landShape, regionCount, seed, 8000);
					long borderCount = graph.centers.stream().filter(c -> c.isBorder).count();
					long borderContinentalLandCount = graph.centers.stream().filter(c -> c.isBorder && !c.isWater && c.tectonicPlate.type == PlateType.Continental).count();
					totalBorderContinentalLandFraction += borderContinentalLandCount / (double) borderCount;
				}
				double average = totalBorderContinentalLandFraction / seedCount;
				assertTrue(average < 0.02, "Average fraction of border centers that are continental land for " + landShape.name() + " with " + regionCount + " regions: " + average);
			}
		}
	}

	private static final LandShape[] riverTestLandShapes = { LandShape.Continents, LandShape.Scattered, LandShape.Landlocked };

	@Test
	public void drawnRiversEndInWaterAndNeverRunThroughLakes()
	{
		for (LandShape landShape : riverTestLandShapes)
		{
			for (long seed = 1; seed <= 3; seed++)
			{
				WorldGraph graph = createGraph(landShape, 6, seed);
				String description = landShape.name() + ", seed " + seed;
				List<Edge> drawnEdges = new ArrayList<>();
				for (Edge edge : graph.edges)
				{
					if (edge.river > GraphRiver.RIVERS_THIS_SIZE_OR_SMALLER_WILL_NOT_BE_DRAWN)
					{
						assertFalse(edge.isWater() || edge.isCoastOrLakeShore(), "River edge in or along water: " + description);
						drawnEdges.add(edge);
					}
				}
				assertFalse(drawnEdges.isEmpty(), "No rivers: " + description);

				// Every connected network of drawn river edges must reach water somewhere.
				Map<Corner, List<Edge>> edgesByCorner = new HashMap<>();
				for (Edge edge : drawnEdges)
				{
					edgesByCorner.computeIfAbsent(edge.v0, k -> new ArrayList<>()).add(edge);
					edgesByCorner.computeIfAbsent(edge.v1, k -> new ArrayList<>()).add(edge);
				}
				Set<Edge> visited = new HashSet<>();
				for (Edge start : drawnEdges)
				{
					if (!visited.add(start))
					{
						continue;
					}
					boolean reachesWater = false;
					Deque<Edge> queue = new ArrayDeque<>();
					queue.add(start);
					while (!queue.isEmpty())
					{
						Edge edge = queue.poll();
						for (Corner corner : new Corner[] { edge.v0, edge.v1 })
						{
							reachesWater |= corner.touches.stream().anyMatch(c -> c.isWater);
							for (Edge next : edgesByCorner.get(corner))
							{
								if (visited.add(next))
								{
									queue.add(next);
								}
							}
						}
					}
					assertTrue(reachesWater, "River that never reaches water: " + description);
				}
			}
		}
	}

	@Test
	public void newLakesAreLabeledAndNotTooBigAndDoNotTouchTheBorder()
	{
		int newLakeCount = 0;
		for (LandShape landShape : riverTestLandShapes)
		{
			for (long seed = 1; seed <= 3; seed++)
			{
				WorldGraph graph = createGraph(landShape, 6, seed);
				String description = landShape.name() + ", seed " + seed;
				Set<Center> visited = new HashSet<>();
				for (Center center : graph.centers)
				{
					// Only lakes put water above sea level.
					if (!center.isWater || center.elevation < WorldGraph.seaLevel || visited.contains(center))
					{
						continue;
					}
					Set<Center> lake = graph.breadthFirstSearch(c -> c.isWater, center);
					visited.addAll(lake);
					newLakeCount++;
					long belowSeaLevelCount = lake.stream().filter(c -> c.elevation < WorldGraph.seaLevel).count();
					assertTrue(lake.size() <= Math.max(WorldGraph.maxLakeSize, belowSeaLevelCount), "Lake of size " + lake.size() + " is too big: " + description);
					assertTrue(lake.stream().noneMatch(c -> c.isBorder), "Lake touches the border: " + description);
					assertTrue(lake.stream().allMatch(c -> c.isLake), "Lake not labeled as a lake: " + description);
				}
			}
		}
		assertTrue(newLakeCount > 0);
	}

	@Test
	public void carvingOnlyLowersAFewCorners()
	{
		int carvedCount = 0;
		for (long seed = 1; seed <= 3; seed++)
		{
			double[][] elevationsBeforeRivers = new double[1][];
			Random rand = new Random(seed);
			Voronoi voronoi = new Voronoi(3000, 4096, 3072, rand);
			WorldGraph graph = new WorldGraph(voronoi, MapSettings.defaultLloydRelaxationsScale, rand, 0.25, MapSettings.LineStyle.Jagged, MapSettings.defaultPointPrecision, true, false,
					LandShape.Landlocked, 6)
			{
				@Override
				protected void createRiversAndLakes()
				{
					elevationsBeforeRivers[0] = corners.stream().mapToDouble(c -> c.elevation).toArray();
					super.createRiversAndLakes();
				}
			};

			int changedCount = 0;
			for (Corner corner : graph.corners)
			{
				double before = elevationsBeforeRivers[0][corner.index];
				assertTrue(corner.elevation <= before, "Carving raised a corner, seed " + seed);
				if (corner.elevation < before)
				{
					changedCount++;
				}
			}
			assertTrue(changedCount < graph.corners.size() * 0.05, "Carving changed " + changedCount + " corners, seed " + seed);
			carvedCount += changedCount;
		}
		assertTrue(carvedCount > 0, "Nothing was carved");
	}

	@Test
	public void riversAndLakesAreDeterministic()
	{
		WorldGraph graph1 = createGraph(LandShape.Landlocked, 6, 4);
		WorldGraph graph2 = createGraph(LandShape.Landlocked, 6, 4);
		for (int i = 0; i < graph1.edges.size(); i++)
		{
			assertEquals(graph1.edges.get(i).river, graph2.edges.get(i).river);
		}
		for (int i = 0; i < graph1.centers.size(); i++)
		{
			assertEquals(graph1.centers.get(i).isWater, graph2.centers.get(i).isWater);
			assertEquals(graph1.centers.get(i).moisture, graph2.centers.get(i).moisture);
		}
	}

	@Test
	public void landNextToLakesIsWetterThanAverage()
	{
		double lakeNeighborMoisture = 0;
		int lakeNeighborCount = 0;
		double landMoisture = 0;
		int landCount = 0;
		for (long seed = 1; seed <= 3; seed++)
		{
			WorldGraph graph = createGraph(LandShape.Landlocked, 6, seed);
			for (Center center : graph.centers)
			{
				if (center.isWater)
				{
					continue;
				}
				landMoisture += center.moisture;
				landCount++;
				if (center.neighbors.stream().anyMatch(c -> c.isLake))
				{
					lakeNeighborMoisture += center.moisture;
					lakeNeighborCount++;
				}
			}
		}
		assertTrue(lakeNeighborCount > 0);
		assertTrue(lakeNeighborMoisture / lakeNeighborCount > landMoisture / landCount + 0.2,
				"Moisture next to lakes: " + lakeNeighborMoisture / lakeNeighborCount + ", all land: " + landMoisture / landCount);
	}

	private static WorldGraph createGraph(LandShape landShape, int regionCount, long seed)
	{
		return createGraph(landShape, regionCount, seed, 3000);
	}

	private static WorldGraph createGraph(LandShape landShape, int regionCount, long seed, int worldSize)
	{
		final double width = 1024;
		final double height = 768;
		return GraphCreator.createGraph(width, height, worldSize, new Random(seed), width / 4096.0, MapSettings.LineStyle.Jagged, MapSettings.defaultPointPrecision, true,
				MapSettings.defaultLloydRelaxationsScale, false, 0, false, false, landShape, regionCount);
	}

}
