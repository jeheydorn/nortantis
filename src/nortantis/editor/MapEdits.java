package nortantis.editor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import hoten.voronoi.Center;
import nortantis.MapText;
import nortantis.Region;
import util.Range;

/**
 * Stores edits made by a user to a map. These are stored as modifications from the generated content.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapEdits implements Serializable
{
	/**
	 * Text the user has edited, added, moved, or rotated. The key is the text id.
	 */
	public List<MapText> text;
	public List<CenterEdit> centerEdits;
	public List<RegionEdit> regionEdits;
		
	public MapEdits()
	{
		text = new ArrayList<>();
		centerEdits = new ArrayList<>();
		regionEdits = new ArrayList<>();
	}

	public boolean isEmpty()
	{
		return text.isEmpty() && centerEdits.isEmpty();
	}
	
	public void initializeCenterEdits(List<Center> centers)
	{
		if (centerEdits.isEmpty())
		{
			centerEdits = new ArrayList<>(centers.size());
			for (@SuppressWarnings("unused") Center c : centers)
			{
				centerEdits.add(new CenterEdit(c.isWater, c.region != null ? c.region.id : null));
			}
		}
		else
		{
			for (int i : new Range(centers.size()))
			{
				if (centers.get(i).region != null)
				{
					centerEdits.get(i).regionId = centers.get(i).region.id;
				}
				centerEdits.get(i).isWater = centers.get(i).isWater;
			}
		}
	}
	
	public void initializeRegionEdits(Collection<Region> regions)
	{
		for (Region region : regions)
		{
			RegionEdit edit = new RegionEdit(region.id, region.backgroundColor);
			regionEdits.add(edit);
		}
	}
}
