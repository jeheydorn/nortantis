package nortantis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import nortantis.editor.FreeIcon;
import nortantis.util.HashMapF;

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

	public FreeIconCollection()
	{
		anchoredNonTreeIcons = new HashMapF<>();
		anchoredTreeIcons = new HashMapF<>();
		nonAnchoredIcons = new ArrayList<>();
	}

	public boolean isEmpty()
	{
		if (!nonAnchoredIcons.isEmpty())
		{
			return false;
		}

		for (Entry<Integer, FreeIcon> entry : anchoredNonTreeIcons.entrySet())
		{
			if (entry.getValue() != null)
			{
				return false;
			}
		}

		for (Entry<Integer, List<FreeIcon>> entry : anchoredTreeIcons.entrySet())
		{
			if (entry.getValue() != null && entry.getValue().size() > 0)
			{
				return false;
			}
		}

		return true;
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
		if (!anchoredTreeIcons.containsKey(centerIndex))
		{
			return Collections.emptyList();
		}
		return anchoredTreeIcons.get(centerIndex);
	}

	public void removeAll(Collection<FreeIcon> toRemove)
	{
		for (FreeIcon icon : toRemove)
		{
			if (icon.centerIndex == null)
			{
				nonAnchoredIcons.remove(icon);
			}

			if (icon.type == IconType.trees)
			{
				if (anchoredTreeIcons.containsKey(icon.centerIndex))
				{
					List<FreeIcon> trees = anchoredTreeIcons.get(icon.centerIndex);
					trees.remove(icon);
					if (trees.isEmpty())
					{
						anchoredTreeIcons.remove(icon.centerIndex);
					}
				}
			}
			else
			{
				anchoredNonTreeIcons.remove(icon.centerIndex);
			}
		}
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

	public List<FreeIcon> diff(FreeIconCollection other)
	{
		// TODO Performance test this
		
		Set<FreeIcon> thisSet = new HashSet<>();
		iterator().forEachRemaining(thisSet::add);

		if (other == null)
		{
			return new ArrayList<>(thisSet);
		}
	
		Set<FreeIcon> otherSet = new HashSet<>();
		other.iterator().forEachRemaining(otherSet::add);

		return new ArrayList<>(getElementsNotInIntersection(thisSet, otherSet));
	}

	private <T> Set<T> getElementsNotInIntersection(Set<T> set1, Set<T> set2)
	{
		Set<T> result = new HashSet<>(set1);
		// Union of both sets
		result.addAll(set2);

		Set<T> intersection = new HashSet<>(set1);
		// Intersection of both sets
		intersection.retainAll(set2);
		
		// Remove elements in the intersection
		result.removeAll(intersection);
		return result;
	}

	public FreeIconCollection deepCopy()
	{
		FreeIconCollection copy = new FreeIconCollection();
		for (FreeIcon icon : this)
		{
			copy.addOrReplace(icon.deepCopy());
		}
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
		FreeIconCollection other = (FreeIconCollection) obj;
		return Objects.equals(anchoredNonTreeIcons, other.anchoredNonTreeIcons)
				&& Objects.equals(anchoredTreeIcons, other.anchoredTreeIcons) && Objects.equals(nonAnchoredIcons, other.nonAnchoredIcons);
	}
	
	
}
