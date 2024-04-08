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

	public synchronized boolean isEmpty()
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

	public synchronized void addOrReplace(FreeIcon icon)
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

	public synchronized FreeIcon getNonTree(int centerIndex)
	{
		return anchoredNonTreeIcons.get(centerIndex);
	}

	public synchronized boolean hasAnchoredIcons(int centerIndex)
	{
		if (anchoredNonTreeIcons.get(centerIndex) != null)
		{
			return true;
		}

		return hasTrees(centerIndex);
	}

	public synchronized List<FreeIcon> getAnchoredIcons(int centerIndex)
	{
		List<FreeIcon> result = new ArrayList<FreeIcon>(getTrees(centerIndex));
		if (getNonTree(centerIndex) != null)
		{
			result.add(getNonTree(centerIndex));
		}
		return result;
	}

	public synchronized void clearTrees(int centerIndex)
	{
		if (anchoredTreeIcons.containsKey(centerIndex))
		{
			anchoredTreeIcons.get(centerIndex).clear();
		}
	}

	public synchronized boolean hasTrees(int centerIndex)
	{
		return !getTrees(centerIndex).isEmpty();
	}

	public synchronized List<FreeIcon> getTrees(int centerIndex)
	{
		if (!anchoredTreeIcons.containsKey(centerIndex))
		{
			return Collections.emptyList();
		}
		return anchoredTreeIcons.get(centerIndex);
	}
	
	public synchronized Iterable<Integer> iterateTreeAnchors()
	{
		return anchoredTreeIcons.keySet();
	}

	public synchronized void removeAll(Collection<FreeIcon> toRemove)
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

	public synchronized void doWithLock(Runnable task)
	{
		task.run();
	}

	public synchronized List<FreeIcon> diff(FreeIconCollection other)
	{
		if (other == null)
		{
			Set<FreeIcon> thisSet = new HashSet<>();
			iterator().forEachRemaining(thisSet::add);
			return new ArrayList<>(thisSet);
		}

		List<FreeIcon> result = new ArrayList<>();

		Set<FreeIcon> thisSet = new HashSet<>();
		iterator().forEachRemaining(thisSet::add);

		Set<FreeIcon> otherSet = new HashSet<>();
		// TODO See if I can remove this deadlock situation. Perhaps copy the icons out of the collections while holding only one lock at a time,
		// but then I need to make FreeIcon threadsafe. I might need to make it immutable anyway because currently the UI thread can change a 
		// FreeIcon while the background thread reads it.
		
		// This creates a hold and wait, which could cause a deadlock if another thread has 'other' locked and is trying to lock 'this'.
		// I don't think a deadlock can happen though because this method is the only method that does this, and it is only called by the UI
		// thread.
		other.doWithLock(() ->
		{
			other.iterator().forEachRemaining(otherSet::add);
			result.addAll(getElementsNotInIntersection(thisSet, otherSet));
		});
		
		return result;
	}

	private static <T> Set<T> getElementsNotInIntersection(Set<T> set1, Set<T> set2)
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

	public synchronized FreeIconCollection deepCopy()
	{
		FreeIconCollection copy = new FreeIconCollection();
		for (FreeIcon icon : this)
		{
			copy.addOrReplace(icon.deepCopy());
		}
		return copy;
	}

	// TODO This is not thread safe, which can be a problem if the main UI thread calls this while the background thread is changing something.
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
