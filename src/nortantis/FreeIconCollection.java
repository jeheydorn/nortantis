package nortantis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import nortantis.editor.FreeIcon;
import nortantis.util.ConcurrentHashMapF;
import nortantis.util.Helper;

/**
 * Allows fast lookup of FreeIcons.
 */
public class FreeIconCollection implements Iterable<FreeIcon>
{
	/**
	 * Maps from Center index to lists of icons that are anchored to that Center.
	 */
	private ConcurrentHashMapF<Integer, FreeIcon> anchoredNonTreeIcons;
	private ConcurrentHashMapF<Integer, CopyOnWriteArrayList<FreeIcon>> anchoredTreeIcons;
	private CopyOnWriteArrayList<FreeIcon> nonAnchoredIcons;

	public FreeIconCollection()
	{
		anchoredNonTreeIcons = new ConcurrentHashMapF<>();
		anchoredTreeIcons = new ConcurrentHashMapF<>();
		nonAnchoredIcons = new CopyOnWriteArrayList<>();
	}

	public FreeIconCollection(FreeIconCollection other)
	{
		anchoredNonTreeIcons = new ConcurrentHashMapF<>(other.anchoredNonTreeIcons);
		anchoredTreeIcons = new ConcurrentHashMapF<>();
		for (Map.Entry<Integer, CopyOnWriteArrayList<FreeIcon>> entry : other.anchoredTreeIcons.entrySet())
		{
			anchoredTreeIcons.put(entry.getKey(), new CopyOnWriteArrayList<FreeIcon>(entry.getValue()));
		}
		nonAnchoredIcons = new CopyOnWriteArrayList<FreeIcon>(other.nonAnchoredIcons);
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

		for (Entry<Integer, CopyOnWriteArrayList<FreeIcon>> entry : anchoredTreeIcons.entrySet())
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
				anchoredTreeIcons.getOrCreateWithLock(icon.centerIndex, () -> new CopyOnWriteArrayList<FreeIcon>()).add(icon);
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

	public synchronized void replace(FreeIcon before, FreeIcon after)
	{
		if ((before.type != IconType.trees && after.type != IconType.trees)
				&& (before.centerIndex != null && before.centerIndex == after.centerIndex))
		{
			anchoredNonTreeIcons.put(after.centerIndex, after);
		}
		else
		{
			remove(before);
			addOrReplace(after);
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

	public synchronized Iterable<FreeIcon> iterateAnchoredNonTreeIcons()
	{
		return anchoredNonTreeIcons.values();
	}

	public synchronized Iterable<FreeIcon> iterateNonAnchoredIcons()
	{
		return nonAnchoredIcons;
	}

	public synchronized void removeAll(Collection<FreeIcon> toRemove)
	{
		for (FreeIcon icon : toRemove)
		{
			remove(icon);
		}
	}

	public synchronized void remove(FreeIcon icon)
	{
		if (icon.centerIndex == null)
		{
			nonAnchoredIcons.remove(icon);
		}
		else
		{
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
			Iterator<CopyOnWriteArrayList<FreeIcon>> anchoredTreeIconsIterator = anchoredTreeIcons.values().iterator();
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

	public synchronized <T> T doWithLockAndReturnResult(Supplier<T> task)
	{
		return task.get();
	}

	public List<FreeIcon> diff(FreeIconCollection other)
	{
		if (other == null)
		{
			return new ArrayList<>(asSet());
		}

		Set<FreeIcon> diff;
		// To avoid a potential deadlock, always compare this object with the one passed in in the same order no matter what direction this
		// method is called. That way the locks are always acquired and released in the same order, so we cannot have a circular hold and
		// wait.
		if (this.hashCode() > other.hashCode())
		{
			diff = innerDiff(other);
		}
		else
		{
			diff = other.innerDiff(this);
		}

		return new ArrayList<>(diff);
	}

	private synchronized Set<FreeIcon> innerDiff(FreeIconCollection other)
	{
		Set<FreeIcon> thisSet = asSet();
		return other.doWithLockAndReturnResult(() ->
		{
			Set<FreeIcon> otherSet = other.asSet();
			return Helper.getElementsNotInIntersection(thisSet, otherSet);
		});
	}

	private synchronized Set<FreeIcon> asSet()
	{
		Set<FreeIcon> thisSet = new HashSet<>();
		for (FreeIcon icon : this)
		{
			if (icon == null)
			{
				continue;
			}

			thisSet.add(icon);
		}
		return thisSet;
	}

	public synchronized void clear()
	{
		anchoredNonTreeIcons.clear();
		anchoredTreeIcons.clear();
		nonAnchoredIcons.clear();
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

		boolean areEqual = innerEquals(other);
		return areEqual;
	}

	private boolean innerEquals(FreeIconCollection other)
	{
		if (!Objects.equals(anchoredNonTreeIcons, other.anchoredNonTreeIcons))
		{
			return false;
		}

		if (!anchoredTreeIcons.keySet().equals(other.anchoredTreeIcons.keySet()))
		{
			return false;
		}

		for (Map.Entry<Integer, CopyOnWriteArrayList<FreeIcon>> entry : anchoredTreeIcons.entrySet())
		{
			if (!areListsEqualOrderInvariant(entry.getValue(), other.anchoredTreeIcons.get(entry.getKey())))
			{
				return false;
			}
		}

		return areListsEqualOrderInvariant(nonAnchoredIcons, other.nonAnchoredIcons);

	}

	private boolean areListsEqualOrderInvariant(List<FreeIcon> list1, List<FreeIcon> list2)
	{
		if (list1 == null)
		{
			return list2 == null;
		}
		if (list2 == null)
		{
			return list1 == null;
		}

		if (list1.size() != list2.size())
		{
			return false;
		}

		HashSet<FreeIcon> set1 = new HashSet<>(list1);
		HashSet<FreeIcon> set2 = new HashSet<>(list2);
		boolean areEqual = set1.equals(set2);
		return areEqual;
	}

}
