package nortantis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;

public class River implements Iterable<Edge>
{
	private List<Edge> edges;
	private int width;

	public River()
	{
		edges = new ArrayList<>();
		width = 0;
	}

	public void add(Edge edge)
	{
		width = Math.max(width, edge.river);
		edges.add(edge);
	}

	public void addAll(River other)
	{
		width = Math.max(width, other.width);
		edges.addAll(other.edges);
	}

	public void reverse()
	{
		Collections.reverse(edges);
	}

	public int size()
	{
		return edges.size();
	}

	public int getWidth()
	{
		return width;
	}

	public List<Edge> getEdges()
	{
		return Collections.unmodifiableList(edges);
	}

	@Override
	public Iterator<Edge> iterator()
	{
		return edges.iterator();
	}

	public Set<Corner> getCorners()
	{
		Set<Corner> result = new HashSet<>();
		for (Edge e : edges)
		{
			if (e.v0 != null)
			{
				result.add(e.v0);
			}

			if (e.v1 != null)
			{
				result.add(e.v1);
			}
		}
		return result;
	}

	public List<Edge> getSegmentForPlacingText()
	{
		final int maxEdgesToInclude = 10;
		final int maxDistanceFromMouth = 2;

		if (edges.size() <= maxEdgesToInclude)
		{
			return edges;
		}

		Edge mouth = findMouth();

		Edge first = edges.get(0);
		int distanceFromMouth = Math.min(edges.size() - maxEdgesToInclude, maxDistanceFromMouth);
		if (first == mouth)
		{
			return edges.subList(distanceFromMouth, distanceFromMouth + maxEdgesToInclude);
		}

		// last is the river's mouth
		return edges.subList(edges.size() - distanceFromMouth - maxEdgesToInclude, edges.size() - distanceFromMouth);
	}

	private Edge findMouth()
	{
		// Find the river's mouth, which is the end that is wider or is touching the ocean.
		Edge first = edges.get(0);
		Edge last = edges.get(edges.size() - 1);

		if (first.isRiverTouchingOcean())
		{
			return first;
		}

		if (last.isRiverTouchingOcean())
		{
			return last;
		}

		if (first.river > last.river)
		{
			return first;
		}

		return last;
	}
}
