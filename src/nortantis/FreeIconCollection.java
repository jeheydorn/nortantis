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
	private HashMapF<Integer, FreeIcon> anchoredNonTreeIcons;
	private HashMapF<Integer, List<FreeIcon>> anchoredTreeIcons;
	private List<FreeIcon> nonAnchoredIcons;
	
	FreeIconCollection()
	{
		anchoredNonTreeIcons = new HashMapF<>();
		anchoredTreeIcons = new HashMapF<>();
		nonAnchoredIcons = new ArrayList<>();
	}
	
	public void addOrReplace(FreeIcon icon)
	{
		if (icon.centerIndex != null)
		{
			if (icon.type == IconType.trees)
			{
				anchoredTreeIcons.getOrCreate(icon.centerIndex, () -> new ArrayList<FreeIcon>()).add(icon);
			}
			else
			{
				anchoredNonTreeIcons.put(icon.centerIndex, icon);
			}
		}
		else
		{
			nonAnchoredIcons.add(icon);
		}
	}
	
	public FreeIcon getNonTree(int centerIndex)
	{
		return anchoredNonTreeIcons.get(centerIndex);
	}
	
	public void clearTrees(int centerIndex)
	{
		if (anchoredTreeIcons.containsKey(centerIndex))
		{
			anchoredTreeIcons.get(centerIndex).clear();
		}
	}
	
	public boolean hasTrees(int centerIndex)
	{
		return !anchoredTreeIcons.getOrCreate(centerIndex, () -> new ArrayList<FreeIcon>()).isEmpty();
	}
}
