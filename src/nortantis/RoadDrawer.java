package nortantis;

import nortantis.editor.Road;
import nortantis.editor.RoadPathNode;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.util.OrderlessPair;
import nortantis.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class RoadDrawer
{
	/**
	 * Road nodes carry no per-segment metadata, so the strategy is a no-op pass-through. Used by {@link PathOperations} to keep the
	 * connect/reverse helpers generic across roads and rivers.
	 */
	public static final PathOperations.NodeMetadataOps<RoadPathNode> ROAD_OPS = new PathOperations.NodeMetadataOps<>()
	{
		@Override
		public RoadPathNode withClearedMetadata(RoadPathNode original)
		{
			return original;
		}

		@Override
		public RoadPathNode withMetadataFrom(RoadPathNode target, RoadPathNode donor)
		{
			return target;
		}

		@Override
		public RoadPathNode withStitchedToNextMetadata(RoadPathNode original)
		{
			return original;
		}
	};

	/**
	 * Discourages roads from going through mountains.
	 */
	private final double mountainWeight = 5.0;
	/**
	 * Discourages roads from going through hills.
	 */
	private final double hillWeight = 1.5;
	/**
	 * Discourages roads from going through sand dunes.
	 */
	private final double dunesWeight = 4.0;
	/**
	 * Determines how much creating new roads favors following existing roads. Higher values means existing roads are less favored.
	 */
	private final double existingRoadWeight = 0.3;

	private final int numberOfRandomRoadsToPerCity = 3;
	/**
	 * Distance between sampled points when checking whether a road overlaps a draw bounds rectangle. Coarser than the drawing resolution
	 * but fine enough to detect curves that bulge into the bounds.
	 */
	private static final double boundsCheckDistanceBetweenPoints = 20.0;

	private WorldGraph graph;
	private Random rand;
	private CopyOnWriteArrayList<Road> roads;
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
				roads = new CopyOnWriteArrayList<>();
				settings.edits.roads = roads;
			}
			else
			{
				roads = settings.edits.roads;
			}
		}
		else
		{
			roads = new CopyOnWriteArrayList<>();
		}
		resolutionScale = settings.resolution;
		this.roadColor = settings.roadColor;
		this.roadStyle = settings.roadStyle;
	}

	public void createRoads()
	{
		Set<Center> citiesProcessed = new HashSet<>();
		Set<Edge> edgesAddedRoadsFor = new HashSet<>();

		for (Center center : graph.centers)
		{
			if (center.isCity)
			{
				if (citiesProcessed.contains(center))
				{
					continue;
				}
				citiesProcessed.add(center);

				// First, partition the centers by which ones are capable of connecting by roads
				Set<Center> partition = graph.breadthFirstSearch((c) -> !c.isWater, center);

				Set<Center> connectedCities = partition.stream().filter((c) -> c.isCity).collect(Collectors.toSet());
				citiesProcessed.addAll(connectedCities);

				Set<OrderlessPair<Center>> roadsAdded = new HashSet<>();

				addRandomRoadsToNearbyNeighbors(connectedCities, roadsAdded, edgesAddedRoadsFor);
				makeAllCitiesReachable(connectedCities, roadsAdded, edgesAddedRoadsFor);
			}
		}
	}

	private void addRandomRoadsToNearbyNeighbors(Set<Center> connectedCities, Set<OrderlessPair<Center>> roadsAdded, Set<Edge> edgesAddedRoadsFor)
	{
		for (Center city : connectedCities)
		{
			int roadsToAddCount = Math.abs(rand.nextInt() % numberOfRandomRoadsToPerCity) + 1;

			Set<Center> potentialNeighbors = new HashSet<>(connectedCities);
			potentialNeighbors.remove(city);

			for (@SuppressWarnings("unused")
			int number : new Range(roadsToAddCount))
			{
				Optional<Center> promise = potentialNeighbors.stream().min((c1, c2) -> Double.compare(city.loc.distanceTo(c1.loc), city.loc.distanceTo(c2.loc)));
				if (promise.isPresent())
				{
					Center closestCity = promise.get();
					OrderlessPair<Center> pair = new OrderlessPair<Center>(closestCity, city);
					potentialNeighbors.remove(closestCity);
					if (!roadsAdded.contains(pair))
					{
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

	private void makeAllCitiesReachable(Set<Center> connectedCities, Set<OrderlessPair<Center>> roadsAdded, Set<Edge> edgesAddedRoadsFor)
	{
		while (true)
		{
			List<Set<Center>> disconnectedComponents = findDisconnectedComponents(connectedCities, roadsAdded);

			if (disconnectedComponents.size() == 1)
			{
				return;
			}

			if (disconnectedComponents.size() == 0)
			{
				assert false;
				return;
			}

			for (int i = 0; i < disconnectedComponents.size(); i++)
			{
				for (int j = i + 1; j < disconnectedComponents.size(); j++)
				{
					Set<Center> component1 = disconnectedComponents.get(i);
					Set<Center> component2 = disconnectedComponents.get(j);

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
		List<Edge> edges = graph.findShortestPath(start, end, (edge, center, distanceToEnd) ->
		{
			if (center.isWater)
			{
				return Double.POSITIVE_INFINITY;
			}

			boolean alreadyHasRoad = edgesAddedRoadsFor.contains(edge);

			double terrainPenalty;
			if (center.isMountain)
			{
				terrainPenalty = mountainWeight;
			}
			else if (center.isHill)
			{
				terrainPenalty = hillWeight;
			}
			else if (center.biome == IconDrawer.sandDunesBiome)
			{
				terrainPenalty = dunesWeight;
			}
			else
			{
				terrainPenalty = 1.0;
			}

			double distanceNormalized = Center.distanceBetween(edge.d0, edge.d1) * (1.0 / resolutionScale);

			return (distanceNormalized * terrainPenalty + distanceToEnd) * (alreadyHasRoad ? existingRoadWeight : 1.0);
		});

		if (edges.isEmpty())
		{
			return;
		}

		List<Edge> soFar = new ArrayList<Edge>();
		for (Edge edge : edges)
		{
			if (edgesAddedRoadsFor.contains(edge))
			{
				addEdgesToRoads(soFar, graph, roads, resolutionScale);
				soFar.clear();
			}
			else
			{
				soFar.add(edge);
				edgesAddedRoadsFor.add(edge);
			}
		}

		addEdgesToRoads(soFar, graph, roads, resolutionScale);
		edgesAddedRoadsFor.addAll(soFar);

		roadsAdded.add(new OrderlessPair<>(start, end));
	}

	public static List<Road> addRoadsFromEdgesInEditor(List<Edge> edges, WorldGraph graph, List<Road> roads, double resolutionScale)
	{
		Set<OrderlessPair<Point>> existingRoadConnections = PathOperations.collectAllConnections(roadNodesList(roads));

		List<Road> changed = new ArrayList<>();
		List<Edge> soFar = new ArrayList<Edge>();
		for (Edge edge : edges)
		{
			if (edge.d0 == null || edge.d1 == null)
			{
				continue;
			}

			OrderlessPair<Point> pair = new OrderlessPair<Point>(edge.d0.loc.mult(1.0 / resolutionScale), edge.d1.loc.mult(1.0 / resolutionScale));

			if (existingRoadConnections.contains(pair))
			{
				Road r = addEdgesToRoads(soFar, graph, roads, resolutionScale);
				if (r != null)
				{
					changed.add(r);
				}
				soFar.clear();
			}
			else
			{
				soFar.add(edge);
				existingRoadConnections.add(pair);
			}
		}

		Road r = addEdgesToRoads(soFar, graph, roads, resolutionScale);
		if (r != null)
		{
			changed.add(r);
		}
		return changed;
	}

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

	/**
	 * Either adds the given edges as a new road, or adds them to an existing road if the points from those edges connect to an existing
	 * road.
	 *
	 * @return Either the new road, or the one the new road was added to. {@code null} if there were no edges.
	 */
	private static Road addEdgesToRoads(List<Edge> edges, WorldGraph graph, List<Road> roads, double resolutionScale)
	{
		if (edges.isEmpty())
		{
			return null;
		}

		List<Point> path = graph.edgeListToDrawPointsDelaunay(edges);

		if (path == null || path.size() <= 1)
		{
			return null;
		}

		List<Point> pathRI = new ArrayList<>(path.stream().map(point -> point.mult(1.0 / resolutionScale)).toList());
		List<Road> changed = connectAndAddRoads(Collections.singletonList(pathRI), roads);
		return changed.isEmpty() ? null : changed.get(0);
	}

	/**
	 * For each location-only sub-path, builds a {@link Road} and either merges it into an existing road whose endpoint matches or adds it
	 * as a new road. A second merge attempt handles a new road bridging two existing roads.
	 *
	 * @return The changed roads — either newly added roads or existing roads that were extended.
	 */
	private static List<Road> connectAndAddRoads(List<List<Point>> subPaths, List<Road> roads)
	{
		List<Road> changed = new ArrayList<>();
		for (List<Point> subPath : subPaths)
		{
			Road newRoad = Road.fromLocations(subPath);
			Road joined = tryConnectingRoadToExistingRoad(newRoad, roads);
			if (joined != null)
			{
				Road joined2 = tryConnectingRoadToExistingRoad(joined, roads);
				if (joined2 != null)
				{
					roads.remove(joined);
					changed.add(joined2);
				}
				else
				{
					changed.add(joined);
				}
			}
			else
			{
				roads.add(newRoad);
				changed.add(newRoad);
			}
		}
		return changed;
	}

	/**
	 * Attempts to merge each road in {@code candidates} with any other road in {@code roads} whose endpoint now matches. Used after edits
	 * that may have changed a road's endpoints (control-point deletion, segment cut, erase) — a newly-exposed endpoint may coincide with
	 * another existing road's endpoint, in which case the two should be one continuous road. When a merge succeeds the candidate is removed
	 * from {@code roads} (its data has been folded into the matched road). A second join attempt is made on the extended result in case it
	 * can also connect at its other end.
	 *
	 * @return The roads whose nodes were extended by absorbing a candidate.
	 */
	public static List<Road> mergeAdjacentRoads(java.util.Collection<Road> candidates, List<Road> roads)
	{
		List<Road> extended = new ArrayList<>();
		for (Road candidate : candidates)
		{
			if (candidate == null || candidate.nodes.size() < 2 || !roads.contains(candidate))
			{
				continue;
			}
			Road joined = tryConnectingRoadToExistingRoad(candidate, roads);
			if (joined == null)
			{
				continue;
			}
			roads.remove(candidate);
			extended.add(joined);
			Road joined2 = tryConnectingRoadToExistingRoad(joined, roads);
			if (joined2 != null)
			{
				roads.remove(joined);
				extended.add(joined2);
			}
		}
		return extended;
	}

	/**
	 * Attempts to join {@code roadToAdd} to an existing road in {@code roads} by matching endpoints. If a match is found, the existing
	 * road's nodes are replaced with the merged node list (a single atomic volatile-field swap so concurrent readers don't see partial
	 * state), {@code roadToAdd} is NOT added to {@code roads}, and the extended existing road is returned. Returns {@code null} if no
	 * endpoint match is found.
	 */
	public static Road tryConnectingRoadToExistingRoad(Road roadToAdd, List<Road> roads)
	{
		if (roadToAdd == null || roadToAdd.nodes.size() < 2)
		{
			return null;
		}
		List<RoadPathNode> toAdd = roadToAdd.nodes;
		for (Road road : roads)
		{
			if (road == roadToAdd)
			{
				continue;
			}
			List<RoadPathNode> existing = road.nodes;
			if (existing.size() < 2)
			{
				continue;
			}
			List<RoadPathNode> merged = tryMergeEndpoints(existing, toAdd);
			if (merged != null)
			{
				road.nodes = new CopyOnWriteArrayList<>(merged);
				return road;
			}
		}
		return null;
	}

	private static List<RoadPathNode> tryMergeEndpoints(List<RoadPathNode> existing, List<RoadPathNode> toAdd)
	{
		PathOperations.ExistingPathAccessor<RoadPathNode> single = new PathOperations.ExistingPathAccessor<>()
		{
			@Override
			public int count()
			{
				return 1;
			}

			@Override
			public List<RoadPathNode> get()
			{
				return existing;
			}
		};
		PathOperations.Match<RoadPathNode> match = PathOperations.tryConnectToExistingPath(toAdd, single, ROAD_OPS);
		return match == null ? null : match.mergedNodes;
	}

	/**
	 * Splits {@code pathRI} at any segments that already exist in {@code roads}, adds the non-overlapping sub-roads to {@code roads}, and
	 * returns the changed roads.
	 */
	public static List<Road> addFreeHandRoadFromPoints(List<Point> pathRI, List<Road> roads)
	{
		if (pathRI == null || pathRI.size() < 2)
		{
			return Collections.emptyList();
		}

		Set<OrderlessPair<Point>> existingConnections = PathOperations.collectAllConnections(roadNodesList(roads));
		List<List<Point>> subPaths = nortantis.util.GeometryHelper.splitPathAtOverlaps(pathRI, existingConnections);
		return connectAndAddRoads(subPaths, roads);
	}

	/**
	 * Splits each road in {@code roads} at any node whose location appears as an endpoint of one of {@code segmentsToRemove}. A road that
	 * becomes multiple pieces stays in the list as its first piece, with the additional pieces appended as new {@link Road} objects.
	 *
	 * @return The roads that changed (existing roads whose nodes were replaced, plus any newly created pieces from splits).
	 */
	public static List<Road> removeSegmentsAndSplitRoads(List<Road> roads, List<List<Point>> segmentsToRemove)
	{
		Set<OrderlessPair<Point>> removedSegments = new HashSet<>();
		for (List<Point> seg : segmentsToRemove)
		{
			// Each list is a concatenation of consecutive overlapping segment pairs (e.g. [A,B,B,C,C,D]), so iterate by pairs
			// (i += 2) to remove every segment in the run, not just the first.
			for (int i = 0; i + 1 < seg.size(); i += 2)
			{
				removedSegments.add(new OrderlessPair<>(seg.get(i), seg.get(i + 1)));
			}
		}

		List<Road> changed = new ArrayList<>();
		List<Road> newRoads = new ArrayList<>();
		for (Road road : roads)
		{
			List<RoadPathNode> nodes = road.nodes;
			List<List<RoadPathNode>> splits = PathOperations.splitAtSegments(nodes, removedSegments);
			boolean unchanged = splits.size() == 1 && splits.get(0).size() == nodes.size();
			if (unchanged)
			{
				continue;
			}

			changed.add(road);
			if (splits.isEmpty())
			{
				road.nodes = new CopyOnWriteArrayList<>();
			}
			else
			{
				road.nodes = new CopyOnWriteArrayList<>(splits.get(0));
				for (int i = 1; i < splits.size(); i++)
				{
					newRoads.add(new Road(splits.get(i)));
				}
			}
		}

		roads.addAll(newRoads);
		changed.addAll(newRoads);
		return changed;
	}

	/** Road counterpart of {@link RiverDrawer#replaceWithFragments}. */
	public static void replaceWithFragments(List<Road> roads, Road road, List<List<RoadPathNode>> fragments, List<Road> changed)
	{
		List<List<RoadPathNode>> cleaned = new ArrayList<>(fragments.size());
		for (List<RoadPathNode> fragment : fragments)
		{
			List<RoadPathNode> normalized = PathOperations.normalizePath(fragment, ROAD_OPS);
			if (normalized.size() >= 2)
			{
				cleaned.add(normalized);
			}
		}
		if (cleaned.isEmpty())
		{
			// By identity, since roads compare equal by their nodes.
			roads.removeIf(r -> r == road);
			return;
		}
		road.nodes = new CopyOnWriteArrayList<>(cleaned.get(0));
		changed.add(road);
		for (int i = 1; i < cleaned.size(); i++)
		{
			Road newRoad = new Road(cleaned.get(i));
			roads.add(newRoad);
			changed.add(newRoad);
		}
	}

	public static void removeEmptyOrSinglePointRoads(List<Road> roadList)
	{
		roadList.removeIf(road -> road.nodes.size() < 2);
	}

	private static Iterable<List<RoadPathNode>> roadNodesList(List<Road> roads)
	{
		List<List<RoadPathNode>> result = new ArrayList<>(roads.size());
		for (Road road : roads)
		{
			result.add(road.nodes);
		}
		return result;
	}

	/**
	 * Draws the roads the were either loaded from settings or created by createRoads().
	 *
	 * @param map
	 *            The image to draw on.
	 */
	public void drawRoads(Image map, Rectangle drawBounds)
	{
		try (Painter p = map.createPainter(DrawQuality.High))
		{
			if (drawBounds != null)
			{
				p.translate(-drawBounds.x, -drawBounds.y);
			}

			Rectangle drawBoundsResolutionInvariant = drawBounds == null ? null
					: new Rectangle(drawBounds.x * (1.0 / resolutionScale), drawBounds.y * (1.0 / resolutionScale), drawBounds.width * (1.0 / resolutionScale),
							drawBounds.height * (1.0 / resolutionScale));
			for (Road road : roads)
			{
				List<RoadPathNode> nodes = road.nodes;
				if (nodes.size() < 2)
				{
					continue;
				}
				if (drawBoundsResolutionInvariant == null || roadOverlapsRectangle(nodes, drawBoundsResolutionInvariant))
				{
					p.setColor(roadColor);
					p.setStroke(roadStyle, resolutionScale);
					List<Point> locations = PathOperations.toLocationList(nodes);
					List<Point> path = CurveCreator.createCurve(locations);
					List<IntPoint> pathScaled = path.stream().map(point -> point.mult(resolutionScale).toIntPoint()).toList();
					p.drawPolyline(pathScaled);
				}
			}
		}
	}

	private boolean roadOverlapsRectangle(List<RoadPathNode> nodes, Rectangle bounds)
	{
		// Sample the actual curve at a coarser resolution than drawing uses so that curves which bulge into the
		// bounds are detected even when no control point lies inside it.
		List<Point> locations = PathOperations.toLocationList(nodes);
		List<Point> curve = CurveCreator.createCurve(locations, boundsCheckDistanceBetweenPoints);
		for (int i = 0; i < curve.size() - 1; i++)
		{
			Point p1 = curve.get(i);
			Point p2 = curve.get(i + 1);
			Rectangle segmentBounds = Rectangle.fromCorners(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
			if (bounds.overlaps(segmentBounds))
			{
				return true;
			}
		}
		return false;
	}

	public void drawRoadDebugInfo(Image map)
	{
		try (Painter p = map.createPainter())
		{
			p.setColor(Color.create(0, 150, 0));
			for (Road road : roads)
			{
				List<RoadPathNode> nodes = road.nodes;
				if (nodes.size() == 0)
				{
					throw new IllegalArgumentException();
				}
				if (nodes.size() == 1)
				{
					throw new IllegalArgumentException();
				}

				for (int i = 0; i < nodes.size(); i++)
				{
					Point point = nodes.get(i).getLoc().mult(resolutionScale);
					int diameter = (int) Math.max(1, 3 * resolutionScale);
					p.drawOval((int) point.x, (int) point.y, diameter, diameter);

					double yOffset = -9 * resolutionScale;
					p.drawString(i + "", point.x, point.y + yOffset);
				}
			}
		}
	}
}
