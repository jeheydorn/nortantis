package nortantis.editor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
	public Map<Integer, RegionEdit> regionEdits;
		
	public MapEdits()
	{
		text = new ArrayList<>();
		centerEdits = new ArrayList<>();
		regionEdits = new TreeMap<>();
	}

	public boolean isEmpty()
	{
		return text.isEmpty() && centerEdits.isEmpty() && regionEdits.isEmpty();
	}
	
	public void initializeCenterEdits(List<Center> centers)
	{
		if (centerEdits.isEmpty())
		{
			centerEdits = Arrays.asList(new CenterEdit[centers.size()]);
		}
		
		for (int i : new Range(centers.size()))
		{
			centerEdits.get(i).isWater = centers.get(i).water;
		}
	}
	
	public void initializeRegionEdits(List<Region> regions)
	{
		if (regionEdits.isEmpty())
		{
			regionEdits = new TreeMap<>();
		}
		
		for (int i : new Range(regions.size()))
		{
			regionEdits.get(i).regionId = regions.get(i).id;
			regionEdits.get(i).color = regions.get(i).backgroundColor;
		}

	}
}
