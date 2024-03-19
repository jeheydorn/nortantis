package nortantis;

import java.util.ArrayList;
import java.util.List;

import nortantis.graph.voronoi.Center;
import nortantis.util.HashMapF;
import nortantis.editor.FreeIcon;

/**
 * Allows fast lookup of FreeIcons.
 */
public class FreeIconCollection
{
	/**
	 * Maps from Center index to lists of icons that are anchored to that Center.
	 */
	HashMapF<Integer, List<FreeIcon>> anchoredIcons;
	List<FreeIcon> nonAnchoredIcons;
	
	FreeIconCollection()
	{
		anchoredIcons = new HashMapF<>();
		nonAnchoredIcons = new ArrayList<>();
	}
	
	public void add(FreeIcon icon)
	{
		if (icon.centerIndex != null)
		{
			anchoredIcons.getOrCreate(icon.centerIndex, () -> new ArrayList<FreeIcon>()).add(icon);
		}
		else
		{
			nonAnchoredIcons.add(icon);
		}
	}
	
	public List<FreeIcon> getForCenter(int centerIndex)
	{
		return anchoredIcons.getOrCreate(centerIndex, () -> new ArrayList<FreeIcon>());
	}
	
	public boolean hasTreesForCenter(int centerIndex)
	{
		return anchoredIcons.getOrCreate(centerIndex, () -> new ArrayList<FreeIcon>()).stream().anyMatch((icon) -> icon.type == IconType.trees);
	}
}
