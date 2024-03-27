package nortantis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import nortantis.graph.voronoi.Center;
import nortantis.util.HashMapF;
import nortantis.editor.FreeIcon;

/**
 * Allows fast lookup of FreeIcons.
 */
public class FreeIconCollection implements Iterable<FreeIcon>
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
	
	public boolean hasAnchoredIcons(int centerIndex)
	{
		if (anchoredNonTreeIcons.get(centerIndex) != null)
		{
			return true;
		}
		
		return hasTrees(centerIndex);
	}
	
	public List<FreeIcon> getAnchoredIcons(int centerIndex)
	{
		// TODO If this doesn't perform well, convert it to an iterator.
		
		List<FreeIcon> result = new ArrayList<FreeIcon>(getTrees(centerIndex));
		if (getNonTree(centerIndex) != null)
		{
			result.add(getNonTree(centerIndex));
		}
		return result;
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
		return !getTrees(centerIndex).isEmpty();
	}
	
	private List<FreeIcon> getTrees(int centerIndex)
	{
		return anchoredTreeIcons.getOrCreate(centerIndex, () -> new ArrayList<FreeIcon>());
	}
	
	@Override
	public Iterator<FreeIcon> iterator()
	{
		return new Iterator<FreeIcon>()
		{
			Iterator<FreeIcon> anchoredNonTreeIconsIterator = anchoredNonTreeIcons.values().iterator();
			Iterator<List<FreeIcon>> anchoredTreeIconsIterator = anchoredTreeIcons.values().iterator();
			Iterator<FreeIcon> treesIterator = anchoredTreeIconsIterator.hasNext() ? anchoredTreeIconsIterator.next().iterator() : null;
			Iterator<FreeIcon> nonAnchoredIconsIterator = nonAnchoredIcons.iterator();
			
			@Override
			public FreeIcon next()
			{
				if (anchoredNonTreeIconsIterator.hasNext())
				{
					return anchoredNonTreeIconsIterator.next();
				}
				
				if (treesIterator != null)
				{
					if (treesIterator.hasNext())
					{
						return treesIterator.next();
					}
					
					while (treesIterator != null && !treesIterator.hasNext())
					{
						if (anchoredTreeIconsIterator.hasNext())
						{
							treesIterator = anchoredTreeIconsIterator.next().iterator();
						}
						else
						{
							treesIterator = null;
						}
					}
					
					if (treesIterator != null && treesIterator.hasNext())
					{
						return treesIterator.next();
					}
					
				}
				
				if (nonAnchoredIconsIterator.hasNext())
				{
					return nonAnchoredIconsIterator.next();
				}
				
				return null;
			}
			
			@Override
			public boolean hasNext()
			{
				if (anchoredNonTreeIconsIterator.hasNext())
				{
					return true;
				}
				
				if (treesIterator != null)
				{
					if (treesIterator.hasNext())
					{
						return true;
					}
					
					while (treesIterator != null && !treesIterator.hasNext())
					{
						if (anchoredTreeIconsIterator.hasNext())
						{
							treesIterator = anchoredTreeIconsIterator.next().iterator();
						}
						else
						{
							treesIterator = null;
						}
					}
					
					if (treesIterator != null && treesIterator.hasNext())
					{
						return true;
					}
					
				}
				
				if (nonAnchoredIconsIterator.hasNext())
				{
					return true;
				}
				
				return false;
			}
		};
	}
}
