package nortantis;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import hoten.voronoi.Center;
import hoten.voronoi.Edge;
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
	
	public void drawRoads()
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
				
				// Determine which cities will have roads between them
				for (Center city : connectedCities)
				{
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
					
					List<Edge> path = graph.findShortestPath(center, city, calculateRoadWeight);
					
					// TODO - Mark the edges that will be roads between cities
					
					// TODO - store which roads I have already drawn so that I don't redraw them later
				}
			}
		}
	}
	
}




