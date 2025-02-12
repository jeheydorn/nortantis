package nortantis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import nortantis.editor.Road;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.util.OrderlessPair;
import nortantis.util.Range;

public class RoadDrawer
{
	/**
	 * Determines how much roads favor pass which are less steep. Higher values mean less steep but longer roads are favored. The domain is
	 * a positive number. A value of 1.0 means the road search algorithm will see an infinitely steep road as twice as long as it is without
	 * considering steepness. a value of 0.0 means the road search algorithm will ignore the steepness of the road.
	 */
	private final double roadElevationWeight = 1.0;
	private final double defaultRoadWidth = 1.0;
	private final Stroke defaultStroke = new Stroke(StrokeType.Dots, (float) (MapCreator.calcSizeMultipilerFromResolutionScaleRounded(1.0) * defaultRoadWidth));

	private WorldGraph graph;
	private Random rand;
	private List<Road> roads;
	private Color colorForNewRoads;
	private double resolutionScale;

	public RoadDrawer(Random rand, MapSettings settings, WorldGraph graph)
	{
		this.graph = graph;
		this.rand = rand;
		if (settings.edits != null && settings.edits.roads != null)
		{
			this.roads = settings.edits.roads;
		}

		if (settings.edits != null)
		{
			if (!settings.edits.isInitialized())
			{
				roads = new ArrayList<>();
				settings.edits.roads = roads;
			}
			else
			{
				roads = settings.edits.roads;
			}
		}
		else
		{
			roads = new ArrayList<>();
		}
		colorForNewRoads = settings.roadColor;
		resolutionScale = settings.resolution;
	}

	public void createRoads()
	{
		Set<Center> citiesProcessed = new HashSet<>();
		Set<Edge> edgesAddedRoadsFor = new HashSet<>();

		// First, partition the centers by which ones aren't capable of connecting by roads
		for (Center center : graph.centers)
		{
			if (center.isCity)
			{
				if (citiesProcessed.contains(center))
				{
					continue;
				}
				citiesProcessed.add(center);

				Set<Center> partition = graph.breadthFirstSearch((c) -> !c.isWater, center);

				Set<Center> connectedCities = partition.stream().filter((c) -> c.isCity).collect(Collectors.toSet());
				citiesProcessed.addAll(connectedCities);

				Set<OrderlessPair<Center>> roadsAttemptedToAdd = new HashSet<>();

				// Determine which cities will have roads between them
				for (Center city : connectedCities)
				{
					OrderlessPair<Center> pair = new OrderlessPair<Center>(center, city);
					if (!roadsAttemptedToAdd.contains(pair))
					{
						// Store which roads I have already added so that I don't re-add them later
						roadsAttemptedToAdd.add(pair);

						int roadsToAddCount = (rand.nextInt() % 2) + 1;

						Set<Center> connectedNeighbors = new HashSet<>();
						Set<Center> potentialNeighbors = new HashSet<>(connectedCities);
						potentialNeighbors.remove(city);

						for (@SuppressWarnings("unused")
						int number : new Range(roadsToAddCount))
						{
							Optional<Center> promise = potentialNeighbors.stream()
									.min((c1, c2) -> Double.compare(city.loc.distanceTo(c1.loc), city.loc.distanceTo(c2.loc)));
							if (promise.isPresent())
							{
								Center closestCity = promise.get();
								connectedNeighbors.add(closestCity);
								potentialNeighbors.remove(closestCity);
							}

							if (potentialNeighbors.isEmpty())
							{
								break;
							}
						}

						for (Center destinationCity : connectedNeighbors)
						{
							// Mark the edges that will be roads between cities
							List<Edge> edges = graph.findShortestPath(city, destinationCity, this::calcRoadWeight, (c -> 
							{
								// Stop the search early if we run into a center that already has a road passing through it.
								// TODO There is a bug in this code that seems to be causing some entire roads between cities to not be added when the should. 
								// TODO I should only stop if the other road is going the same direction.
								for (Edge e : c.borders)
								{
									if (edgesAddedRoadsFor.contains(e))
									{
										return true;
									}
								}
								return false;
							}));
							
							if (edges.isEmpty())
							{
								continue;
							}
							
							// Add the edges as roads, making sure to not add roads for edges already added, since overlapping dotted/dashed lines don't look good.
							List<Edge> soFar = new ArrayList<Edge>();
							for (Edge edge : edges)
							{
								if (edgesAddedRoadsFor.contains(edge))
								{
									addEdgesToRoads(soFar);
									soFar.clear();
								}
								else
								{
									soFar.add(edge);
									edgesAddedRoadsFor.add(edge);
								}
							}
							addEdgesToRoads(soFar);
						}
					}
				}
			}
		}
	}
	
	private void addEdgesToRoads(List<Edge> edges)
	{
		if (edges.isEmpty())
		{
			return;
		}
		
		List<Point> path = graph.edgeListToDrawPointsDelaunay(edges);

		if (path == null || path.size() <= 1)
		{
			return;
		}
		
		List<Point> pathResolutionInvariant = path.stream().map(point -> point.mult(1.0 / resolutionScale)).toList();
		roads.add(new Road(pathResolutionInvariant, defaultStroke, colorForNewRoads));
	}

	private double calcRoadWeight(Edge edge)
	{
		if (edge.d0 == null || edge.d1 == null)
		{
			return Double.POSITIVE_INFINITY;
		}
		if (edge.d0.isWater || edge.d1.isWater)
		{
			return Double.POSITIVE_INFINITY;
		}
		
		double distance = Center.distanceBetween(edge.d0, edge.d1);
		// Wait the distance by how steep the road is so that roads favor less steep paths.
		double lowerElevation = Math.min(edge.d0.elevation, edge.d1.elevation);
		double higherElevation = Math.max(edge.d0.elevation, edge.d1.elevation);
		double angle = Math.atan2(higherElevation, lowerElevation);
		double angleNormalized = angle / Math.PI;
		return distance + (distance * angleNormalized * roadElevationWeight);
	}

	/**
	 * Draws the roads the were either loaded from settings or created by createRoads().
	 * 
	 * @param map
	 *            The image to draw on.
	 */
	public void drawRoads(Image map)
	{
		Painter p = map.createPainter();

		for (Road road : roads)
		{
			p.setColor(road.color);
			p.setStroke(road.style, resolutionScale);
			List<Point> path = road.path; // CurveCreator.createCurve(road.path); TODO put back. There's a bug in this.
			List<IntPoint> pathScaled = path.stream().map(point -> point.mult(resolutionScale).toIntPoint()).toList();
			p.drawPolyline(pathScaled);
		}

	}

}
