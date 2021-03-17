package nortantis;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import hoten.voronoi.Center;
import hoten.voronoi.Edge;
import nortantis.util.OrderlessPair;
import nortantis.util.Pair;
import nortantis.util.Range;

public class RoadDrawer
{
	/**
	 * Determines how much roads favor pass which are less steep. Higher values mean less steep but longer roads are favored. The domain is a positive number. 
	 * A value of 1.0 means the road search algorithm will see an infinitely steep road as twice as long as it is without considering steepness.
	 * a value of 0.0 means the road search algorithm will ignore the steepness of the road.
	 */
	final double roadElevationWeight = 1.0;
	
	private MapSettings settings;
	private IconDrawer iconDrawer;
	private WorldGraph graph;
	private Random rand;

	public RoadDrawer(Random rand, MapSettings settings, WorldGraph graph, IconDrawer iconDrawer)
	{
		this.settings = settings;
		this.iconDrawer = iconDrawer;
		this.graph =  graph;
		this.rand = rand;
	}
	
	public void markRoads()
	{
		Function<Edge, Double> calculateRoadWeight = (edge) -> 
		{
			if (edge.d0 == null || edge.d1 == null)
			{
				return Double.POSITIVE_INFINITY;
			}
			double distance = Center.distanceBetween(edge.d0, edge.d1);
			// Wait the distance by how steep the road is so that roads favor less steep paths.
			double lowerElevation = Math.min(edge.d0.elevation, edge.d1.elevation);
			double higherElevation = Math.max(edge.d0.elevation, edge.d1.elevation);
			double angle = Math.atan2(higherElevation, lowerElevation);
			double angleNormalized = angle/Math.PI;
			return distance + (distance * angleNormalized * roadElevationWeight);
		};
		
		// First, partition the centers by which ones aren't capable of connecting by roads
		for (Center center : graph.centers)
		{
			if (center.isCity)
			{
				Set<Center> partition = graph.breadthFirstSearch((c) -> !c.isMountain && !c.isWater, center);
				
				Set<Center> connectedCities = partition.stream().filter((c) -> c.isCity).collect(Collectors.toSet());
				
				Set<OrderlessPair<Center>> roadsAttemptedToAdd = new HashSet<>();
				
				// Determine which cities will have roads between them
				for (Center city : connectedCities)
				{
					OrderlessPair<Center> pair = new OrderlessPair<Center>(center, city);
					if (!roadsAttemptedToAdd.contains(pair))
					{
						// Store which roads I have already drawn so that I don't redraw them later
						roadsAttemptedToAdd.add(pair);
						
						int roadsToAddCount = (rand.nextInt() % 2) + 1;
						
						Set<Center> connectedNeighbors = new HashSet<>();
						Set<Center> potentialNeighbors = new HashSet<>(connectedCities);
						potentialNeighbors.remove(city);
						
						for (@SuppressWarnings("unused") int number : new Range(roadsToAddCount))
						{
							Optional<Center> promise = potentialNeighbors.stream().min((c1, c2) -> Double.compare(city.loc.distanceTo(c1.loc), city.loc.distanceTo(c2.loc)));
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
						
						// Mark the edges that will be roads between cities
						List<Edge> path = graph.findShortestPath(center, city, calculateRoadWeight);
						for (Edge edge : path)
						{
							edge.isRoad = true;
						}	
					}
				}
			}
		}
	}
	
	/**
	 * Draws roads based on which edges have been marked as roads. This is done separate from marking so that roads can be redrawn in the editor.
	 * @param map
	 * @param sizeMultiplier
	 */
	public void drawRoads(BufferedImage map, double sizeMultiplier)
	{
		Graphics2D g = map.createGraphics();
		g.setColor(settings.roadColor);
		// TODO - make the parameters below of the dashed line the maps that in
		Stroke dashed = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
		g.setStroke(dashed);
		
		for (Edge edge : graph.edges)
		{
			if (!edge.isRoad)
			{
				List<Edge> road = followRoad(edge, RoadDirection.d0);
				List<Edge> d1Road = followRoad(edge, RoadDirection.d1);
				// Remove edge so it's not drawn twice
				d1Road.remove(0);
				road.addAll(d1Road);
				
				// Convert the road to a polyline
				// TODO - Rather than use NoisyEdges, use CurveCreator to create a curve on the fly, and there is no need to store it.
				
				// Draw the road
				//g.drawPolyline();
			}
		}
	}
	
	private LinkedList<Edge> followRoad(Edge edge, RoadDirection direction)
	{
		if (edge == null)
		{
			// Edge of the map
			return new LinkedList<Edge>();
		}
		Center currentCenter = direction == RoadDirection.d0 ? edge.d0 : edge.d1;
		if (currentCenter == null)
		{
			// Edge of the map
			return new LinkedList<Edge>();
		}
			
		List<Edge> nextRoads = currentCenter.borders.stream().filter(e -> !edge.equals(e) && e.isRoad).collect(Collectors.toList());
		
		if (nextRoads.isEmpty())
		{
			// End of the road
			LinkedList<Edge> result = new LinkedList<Edge>();
			result.add(edge);
			return result;
		}
		
		// If there is only one option the next road, follow it. 
		// If there is more than one option, then follow the second one so that if two roads cross, they will be drawn as crossing each other.
		Edge next  = (nextRoads.size() == 1) ? nextRoads.get(0) : nextRoads.get(1);
		RoadDirection nextDirection = next.d0 == currentCenter ? RoadDirection.d1 : RoadDirection.d0;
		LinkedList<Edge> result = followRoad(next, nextDirection);
		result.push(edge);
		return result;
	}
	
	private enum RoadDirection
	{
		d0,
		d1
	}
	
}




