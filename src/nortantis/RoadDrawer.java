package nortantis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import nortantis.editor.Road;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.util.OrderlessPair;
import nortantis.util.Range;

public class RoadDrawer
{
	/**
	 * Discourages roads from going through mountains.
	 */
	private final double mountainWeight = 5.0;
	/**
	 * Discourages roads from going through hills.
	 */
	private final double hillWeight = 1.5;
	/**
	 * Determines how much creating new roads favors following existing roads. Higher values means existing roads are less favored.
	 */
	private final double existingRoadWeight = 0.3;

	private final int numberOfRandomRoadsToPerCity = 3;

	private WorldGraph graph;
	private Random rand;
	private List<Road> roads;
	private double resolutionScale;
	private Color roadColor;
	private Stroke roadStyle;

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
		resolutionScale = settings.resolution;
		this.roadColor = settings.roadColor;
		this.roadStyle = settings.roadStyle;
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

				Set<OrderlessPair<Center>> roadsAdded = new HashSet<>();

				addRandomRoadsToNearbyNeighbors(connectedCities, roadsAdded, edgesAddedRoadsFor);
				makeAllCitiesReachable(connectedCities, roadsAdded, edgesAddedRoadsFor);
			}
		}
	}

	private void addRandomRoadsToNearbyNeighbors(Set<Center> connectedCities, Set<OrderlessPair<Center>> roadsAdded,
			Set<Edge> edgesAddedRoadsFor)
	{
		// Determine which cities will have roads between them
		for (Center city : connectedCities)
		{
			int roadsToAddCount = Math.abs(rand.nextInt() % numberOfRandomRoadsToPerCity) + 1;

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
					OrderlessPair<Center> pair = new OrderlessPair<Center>(closestCity, city);
					potentialNeighbors.remove(closestCity);
					if (!roadsAdded.contains(pair))
					{
						// Store which roads I have already added so that I don't re-add them later
						addRoadBetweenCenters(city, closestCity, roadsAdded, edgesAddedRoadsFor);
					}
				}

				if (potentialNeighbors.isEmpty())
				{
					break;
				}
			}
		}
	}

	/**
	 * Adds roads between cities to ensure all cities are reachable from all other cities through some path.
	 * 
	 * @param connectedCities
	 *            Cities that will all be reachable from each other after this method finishes.
	 * @param roadsAdded
	 *            Stores what roads have been added so far to avoid re-adding the same road twice.
	 * @param edgesAddedRoadsFor
	 *            Stores which edges we've added roads for so that roads going the same direction don't overlap each other.
	 */
	private void makeAllCitiesReachable(Set<Center> connectedCities, Set<OrderlessPair<Center>> roadsAdded, Set<Edge> edgesAddedRoadsFor)
	{
		while (true)
		{
			List<Set<Center>> disconnectedComponents = findDisconnectedComponents(connectedCities, roadsAdded);

			if (disconnectedComponents.size() == 1)
			{
				// We're done because all cities are reachable from all other cities.
				return;
			}

			if (disconnectedComponents.size() == 0)
			{
				// This should not happen because it means the there are no cities.
				assert false;
				return;
			}

			for (int i = 0; i < disconnectedComponents.size(); i++)
			{
				for (int j = i + 1; j < disconnectedComponents.size(); j++)
				{
					Set<Center> component1 = disconnectedComponents.get(i);
					Set<Center> component2 = disconnectedComponents.get(j);

					// Find closest cities between the two components.
					double minDistance = Double.MAX_VALUE;
					Center closestCity1 = null;
					Center closestCity2 = null;
					for (Center c1 : component1)
					{
						for (Center c2 : component2)
						{
							double distance = c1.loc.distanceTo(c2.loc);
							if (distance < minDistance)
							{
								minDistance = distance;
								closestCity1 = c1;
								closestCity2 = c2;
							}
						}
					}

					// Add a road between the closest cities.
					if (closestCity1 != null && closestCity2 != null)
					{
						addRoadBetweenCenters(closestCity1, closestCity2, roadsAdded, edgesAddedRoadsFor);
					}
				}

			}
		}
	}

	private void addRoadBetweenCenters(Center start, Center end, Set<OrderlessPair<Center>> roadsAdded, Set<Edge> edgesAddedRoadsFor)
	{
		// Mark the edges that will be roads between cities
		List<Edge> edges = graph.findShortestPath(start, end, (edge, center, distanceToEnd) ->
		{
			if (center.isWater)
			{
				return Double.POSITIVE_INFINITY;
			}

			// If there's already a road here, favor it so we don't make redundant roads that almost follow the same course.
			boolean alreadyHasRoad = edgesAddedRoadsFor.contains(edge);

			double mountainOrHillPenalty;
			if (center.isMountain)
			{
				mountainOrHillPenalty = mountainWeight;
			}
			else if (center.isHill)
			{
				mountainOrHillPenalty = hillWeight;
			}
			else
			{
				mountainOrHillPenalty = 1.0;
			}

			double distanceNormalized = Center.distanceBetween(edge.d0, edge.d1) * (1.0 / resolutionScale);

			return (distanceNormalized * mountainOrHillPenalty + distanceToEnd) * (alreadyHasRoad ? existingRoadWeight : 1.0);
		}, (center ->
		{
			// TODO remove this if I don't use it.
			// Stop the search early if we run into a center that already has a road passing through it and that road directly leads to the
			// end we want.
			// graph.breadthFirstSearchForGoal((prev, c, distance) ->
			// {
			// Edge e = graph.findConnectingEdge(prev, c);
			// if (e == null)
			// {
			// assert false;
			// return false;
			// }
			// return edgesAddedRoadsFor.contains(e);
			// }, (c) ->
			// {
			// return c.equals(end);
			// }, center);

			return false;
		}));

		if (edges.isEmpty())
		{
			return;
		}

		// Add the edges as roads, making sure to not add roads for edges already added, since overlapping dotted/dashed
		// lines don't look good.
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
		edgesAddedRoadsFor.addAll(soFar);

		// Add this road so that the next call to findDisconnectedComponents detects this road.
		roadsAdded.add(new OrderlessPair<>(start, end));
	}


	/**
	 * Finds the sets of disconnected components in the graph of connectedCities, where a node is a Center in connectedCities and an edge is
	 * an OrderlessPair stored in roadsAdded.
	 * 
	 * @param connectedCities
	 * @param roadsAdded
	 * @return A list of disconnected components from connectedCities. If all centers in connectedCities are connected through some path of
	 *         roads in roadsAdded, then the result will be a list of size 1, and that first element will be a set equal to connectedCities.
	 */
	private List<Set<Center>> findDisconnectedComponents(Set<Center> connectedCities, Set<OrderlessPair<Center>> roadsAdded)
	{
		List<Set<Center>> disconnectedComponents = new ArrayList<>();
		Set<Center> visited = new HashSet<>();

		for (Center city : connectedCities)
		{
			if (!visited.contains(city))
			{
				Set<Center> component = new HashSet<>();
				Stack<Center> stack = new Stack<>();
				stack.push(city);

				while (!stack.isEmpty())
				{
					Center current = stack.pop();
					if (!visited.contains(current))
					{
						visited.add(current);
						component.add(current);

						// Find neighbors connected by roads
						for (OrderlessPair<Center> road : roadsAdded)
						{
							if (road.getFirst().equals(current))
							{
								if (!visited.contains(road.getSecond()))
								{
									stack.push(road.getSecond());
								}
							}
							else if (road.getSecond().equals(current))
							{
								if (!visited.contains(road.getFirst()))
								{
									stack.push(road.getFirst());
								}
							}
						}
					}
				}
				disconnectedComponents.add(component);
			}
		}

		return disconnectedComponents;
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
		roads.add(new Road(pathResolutionInvariant));
	}

	/**
	 * Draws the roads the were either loaded from settings or created by createRoads().
	 * 
	 * @param map
	 *            The image to draw on.
	 */
	public void drawRoads(Image map)
	{
		Painter p = map.createPainter(DrawQuality.High);

		for (Road road : roads)
		{
			p.setColor(roadColor);
			p.setStroke(roadStyle, resolutionScale);
			List<Point> path = CurveCreator.createCurve(road.path);
			List<IntPoint> pathScaled = path.stream().map(point -> point.mult(resolutionScale).toIntPoint()).toList();
			p.drawPolyline(pathScaled);
		}

	}

}
