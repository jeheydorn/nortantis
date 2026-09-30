package nortantis.swing;

import nortantis.FreeIconCollection;
import nortantis.GraphRiver;
import nortantis.MapText;
import nortantis.Region;
import nortantis.WorldGraph;
import nortantis.editor.CenterEdit;
import nortantis.editor.EdgeEdit;
import nortantis.editor.RegionEdit;
import nortantis.editor.River;
import nortantis.editor.RiverPathNode;
import nortantis.editor.Road;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.util.Range;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores edits made by a user to a map. This is initialized from the generated map the first time the map is drawn, and then afterwards the
 * edits are the source of truth for what the map should look like.
 *
 * Everything in this class that can change after the edits are first generated needs to be thread safe so that the editor can edit it wall
 * the map creator draws. And the text drawer needs to update MapText objects with areas and bounds.
 *
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapEdits implements Serializable
{
	/**
	 * Text the user has edited, added, moved, or rotated. The key is the text id.
	 */
	public CopyOnWriteArrayList<MapText> text;
	public ConcurrentHashMap<Integer, CenterEdit> centerEdits;
	public ConcurrentHashMap<Integer, RegionEdit> regionEdits;
	public boolean hasIconEdits;
	public Map<Integer, EdgeEdit> edgeEdits;
	public FreeIconCollection freeIcons;
	public CopyOnWriteArrayList<Road> roads;
	/** All rivers (both generated from the graph and user-drawn). */
	public CopyOnWriteArrayList<River> rivers;
	/**
	 * True once rivers have been initialized from the graph (either at first draw or by SubMapCreator). When false, the first full draw
	 * will call {@link #initializeRiversFromGraph} to populate {@link #rivers} from the graph's edge river levels plus any rivers already
	 * present.
	 */
	public boolean hasInitializedRivers;

	/**
	 * Not stored. A flag the editor uses to tell TextDrawer to generate text and store it as edits.
	 */
	public boolean bakeGeneratedTextAsEdits;

	/**
	 * Not stored. True when the line1Bounds/line2Bounds on the MapText entries in {@link #text} cannot be trusted to match the current
	 * rendering resolution — either because this MapEdits was just constructed, just deep-copied (e.g. an undo snapshot being restored), or
	 * has not yet been the subject of a full-bounds text draw. Set to false by {@link nortantis.TextDrawer} after a full-bounds pass; reset
	 * to true by {@link #deepCopy()}. {@link nortantis.TextDrawer#updateTextBoundsIfNeeded} consults this flag at the end of every
	 * incremental draw to decide whether to recompute bounds. Excluded from {@link #equals(Object)}.
	 *
	 * Polarity note: defaults to true (Java's default for boolean is false, so we set it true in the constructor and in deepCopy). That
	 * keeps "needs refresh" as the safe default — a freshly constructed MapEdits has null bounds and must be refreshed before its bounds
	 * are trusted.
	 */
	public boolean textBoundsNeedRefresh;

	/**
	 * Not stored or copied. Rivers and roads, by identity, that the editor is holding: their control points that can't stay where they are
	 * (see {@link nortantis.ControlPointPlacement}) are kept, and shown as ones to be removed, until the user lets go of them. Drawing
	 * removes such control points from every other line. Replaced rather than modified, so a draw on another thread can read it safely.
	 * Excluded from {@link #equals(Object)}.
	 */
	public transient volatile Set<Object> linesExemptFromControlPointRemoval = Collections.emptySet();

	public MapEdits()
	{
		text = new CopyOnWriteArrayList<>();
		centerEdits = new ConcurrentHashMap<>();
		regionEdits = new ConcurrentHashMap<>();
		edgeEdits = new TreeMap<>();
		freeIcons = new FreeIconCollection();
		roads = new CopyOnWriteArrayList<Road>();
		rivers = new CopyOnWriteArrayList<River>();
		hasInitializedRivers = false;
		textBoundsNeedRefresh = true;
	}

	public boolean isInitialized()
	{
		return !centerEdits.isEmpty();
	}

	public void initializeCenterEdits(List<Center> centers)
	{
		centerEdits = new ConcurrentHashMap<>(centers.size());
		for (int index : new Range(centers.size()))
		{
			Center c = centers.get(index);
			centerEdits.put(index, new CenterEdit(index, c.isWater, c.isLake, c.region != null ? c.region.id : null, null, null));
		}

		hasIconEdits = true;
	}

	/**
	 * Extracts rivers from the graph and appends them to {@link #rivers}. Any rivers already in the list (e.g. loaded from an old save
	 * file) are preserved. Sets {@link #hasInitializedRivers} to {@code true}.
	 *
	 * @param graph
	 *            The graph whose edge river levels are read.
	 * @param resolutionScale
	 *            Used to convert graph-pixel corner locations to RI coordinates.
	 */
	public void initializeRiversFromGraph(WorldGraph graph, double resolutionScale)
	{
		List<GraphRiver> graphRivers = graph.findRivers();
		for (GraphRiver graphRiver : graphRivers)
		{
			List<Corner> corners = graphRiver.getOrderedCorners();
			List<Edge> edges = graphRiver.getEdges();
			if (corners.size() < 2 || edges.isEmpty())
			{
				continue;
			}

			// Build nodes carrying the per-segment width, a noise seed derived from the edge, and
			// the edge index so later draw/lookup code can match this segment back to its Voronoi
			// edge without re-doing corner-distance matching.
			// We need (corners.size() - 1) segments, matching edges.size().
			List<RiverPathNode> nodes = new ArrayList<>(corners.size());
			for (int i = 0; i < corners.size(); i++)
			{
				Point loc = corners.get(i).loc.mult(1.0 / resolutionScale);
				if (i < edges.size())
				{
					Edge segmentEdge = edges.get(i);
					// Constrain generated river widths to the discrete set the editor's width slider can produce, so a generated river
					// looks like one the user could have drawn and the width slider snaps cleanly to it when selected.
					int widthLevel = GraphRiver.snapToAllowedRiverLevel(segmentEdge.river);
					nodes.add(new RiverPathNode(loc, widthLevel, segmentEdge.noisyEdgesSeed, segmentEdge.index));
				}
				else
				{
					nodes.add(new RiverPathNode(loc, 0, 0L, RiverPathNode.EDGE_INDEX_NONE));
				}
			}

			rivers.add(new River(nodes));
		}
		hasInitializedRivers = true;
	}

	public void initializeRegionEdits(Collection<Region> regions)
	{
		for (Region region : regions)
		{
			RegionEdit edit = new RegionEdit(region.id, region.backgroundColor);
			regionEdits.put(edit.regionId, edit);
		}
	}

	/**
	 * If the given point lands within the bounding box of a piece of text, this returns one with the lowest top. Else null is returned.
	 */
	public MapText findTextPicked(Point point)
	{
		List<MapText> textAtPoint = findAllTextAtPoint(point);
		if (textAtPoint.isEmpty())
		{
			return null;
		}

		return textAtPoint.stream()
				.max((t1, t2) -> Double.compare(t1.line1Bounds == null ? Double.POSITIVE_INFINITY : t1.line1Bounds.y, t2.line1Bounds == null ? Double.POSITIVE_INFINITY : t2.line1Bounds.y)).get();
	}

	public List<MapText> findAllTextAtPoint(Point point)
	{
		List<MapText> result = new ArrayList<>();

		for (MapText mp : text)
		{
			if (mp.value.length() > 0)
			{
				if (mp.line1Bounds != null && mp.line1Bounds.contains(point) || mp.line2Bounds != null && mp.line2Bounds.contains(point))
				{
					result.add(mp);
				}
			}
		}
		return result;
	}

	public List<MapText> findTextSelectedByBrush(Point point, double brushDiameter)
	{
		List<MapText> result = new ArrayList<>();

		for (MapText mp : text)
		{
			if (mp.value.length() > 0)
			{
				if (mp.line1Bounds != null && mp.line1Bounds.overlapsCircle(point, brushDiameter / 2.0) || mp.line2Bounds != null && mp.line2Bounds.overlapsCircle(point, brushDiameter / 2.0))
				{
					result.add(mp);
				}
			}
		}
		return result;
	}

	public void purgeEmptyText()
	{
		for (int i = text.size() - 1; i >= 0; i--)
		{
			if (text.get(i).value == null || text.get(i).value.isEmpty())
			{
				text.remove(i);
			}
		}
	}

	public MapEdits deepCopy()
	{
		MapEdits copy = new MapEdits();
		for (MapText mText : text)
		{
			copy.text.add(mText.deepCopy());
		}

		copy.centerEdits = new ConcurrentHashMap<Integer, CenterEdit>(centerEdits);

		for (Map.Entry<Integer, RegionEdit> entry : regionEdits.entrySet())
		{
			copy.regionEdits.put(entry.getKey(), entry.getValue().deepCopy());
		}

		// edgeEdits must be deep-copied so that undoing past every change on a pre-3.19 file
		// restores the source data for the EdgeEdit→River migration. Without this, the migration
		// reruns but finds nothing to convert and silently produces zero rivers.
		for (Map.Entry<Integer, EdgeEdit> entry : edgeEdits.entrySet())
		{
			copy.edgeEdits.put(entry.getKey(), entry.getValue().deepCopy());
		}

		copy.hasIconEdits = hasIconEdits;

		copy.freeIcons = new FreeIconCollection(freeIcons);

		copy.bakeGeneratedTextAsEdits = bakeGeneratedTextAsEdits;
		// Always reset to true on copy: the copy can't trust that its preserved line1Bounds/line2Bounds
		// references match the current displayQualityScale (the source may have been drawn at a different
		// resolution, or the source may itself be a snapshot with stale bounds). The next incremental
		// draw will see this flag and call updateTextBoundsIfNeeded, which recomputes every text's
		// bounds at the current resolution. The bounds references are still copied (see MapText.deepCopy)
		// so that the brief window between an undo restoration and the redraw doesn't leave the user
		// unable to click anything — they just may be slightly off until the redraw completes.
		copy.textBoundsNeedRefresh = true;

		List<Road> deepCopyOfRoads = roads.stream().map(road -> new Road(road)).toList();
		copy.roads = new CopyOnWriteArrayList<Road>();
		copy.roads.addAll(deepCopyOfRoads);

		List<River> deepCopyOfRivers = rivers.stream().map(river -> new River(river)).toList();
		copy.rivers = new CopyOnWriteArrayList<River>();
		copy.rivers.addAll(deepCopyOfRivers);

		copy.hasInitializedRivers = hasInitializedRivers;

		return copy;
	}

	/**
	 * Warning when re-creating this function: This must not include textBoundsNeedRefresh.
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		MapEdits other = (MapEdits) obj;
		return bakeGeneratedTextAsEdits == other.bakeGeneratedTextAsEdits && Objects.equals(centerEdits, other.centerEdits) && Objects.equals(freeIcons, other.freeIcons)
				&& hasIconEdits == other.hasIconEdits && Objects.equals(regionEdits, other.regionEdits) && Objects.equals(text, other.text) && Objects.equals(roads, other.roads)
				&& Objects.equals(rivers, other.rivers) && hasInitializedRivers == other.hasInitializedRivers;
	}

}
