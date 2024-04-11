package nortantis.swing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import nortantis.FreeIconCollection;
import nortantis.MapText;
import nortantis.Region;
import nortantis.editor.CenterEdit;
import nortantis.editor.EdgeEdit;
import nortantis.editor.RegionEdit;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.util.Range;

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
	public List<EdgeEdit> edgeEdits;
	public FreeIconCollection freeIcons;

	/**
	 * Not stored. A flag the editor uses to tell TextDrawer to generate text and store it as edits.
	 */
	public boolean bakeGeneratedTextAsEdits;
	
	/**
	 * Not stored. A flag to ensure text bounds and areas are always present after drawing.
	 */
	public boolean hasCreatedTextBounds;

	public MapEdits()
	{
		text = new CopyOnWriteArrayList<>();
		centerEdits = new ConcurrentHashMap<>();
		regionEdits = new ConcurrentHashMap<>();
		edgeEdits = new ArrayList<>();
		freeIcons = new FreeIconCollection();
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

	public void initializeEdgeEdits(List<Edge> edges)
	{
		edgeEdits = new ArrayList<>(edges.size());
		for (Edge edge : edges)
		{
			edgeEdits.add(new EdgeEdit(edge.index, edge.river));
		}
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
	 * If the given point lands within the bounding box of a piece of text, this returns the first one found. Else null is returned.
	 */
	public MapText findTextPicked(Point point)
	{
		for (MapText mp : text)
		{
			if (mp.value.length() > 0)
			{
				if (mp.line1Area != null)
				{
					if (mp.line1Area.contains(point))
						return mp;
				}

				if (mp.line2Area != null)
				{
					if (mp.line2Area.contains(point))
						return mp;
				}
			}
		}
		return null;
	}

	public List<MapText> findTextSelectedByBrush(Point point, double brushDiameter)
	{
		List<MapText> result = new ArrayList<>();

		for (MapText mp : text)
		{
			if (mp.value.length() > 0)
			{
				if (mp.line1Area != null && mp.line1Area.overlapsCircle(point, brushDiameter / 2.0)
						|| mp.line2Area != null && mp.line2Area.overlapsCircle(point, brushDiameter / 2.0))
				{
					result.add(mp);
				}
			}
		}
		return result;
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

		copy.hasIconEdits = hasIconEdits;

		for (EdgeEdit eEdit : edgeEdits)
		{
			copy.edgeEdits.add(eEdit.deepCopy());
		}
		
		copy.freeIcons = new FreeIconCollection(freeIcons);

		copy.bakeGeneratedTextAsEdits = bakeGeneratedTextAsEdits;
		copy.hasCreatedTextBounds = hasCreatedTextBounds;

		return copy;
	}

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
		return bakeGeneratedTextAsEdits == other.bakeGeneratedTextAsEdits && Objects.equals(centerEdits, other.centerEdits)
				&& Objects.equals(edgeEdits, other.edgeEdits) && Objects.equals(freeIcons, other.freeIcons)
				&& hasCreatedTextBounds == other.hasCreatedTextBounds && hasIconEdits == other.hasIconEdits
				&& Objects.equals(regionEdits, other.regionEdits) && Objects.equals(text, other.text);
	}

}
