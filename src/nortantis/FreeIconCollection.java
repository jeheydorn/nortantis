package nortantis;

import nortantis.editor.FreeIcon;
import nortantis.geom.GridCoordinate;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.util.ConcurrentHashMapF;
import nortantis.util.GeometryHelper;
import nortantis.util.Helper;
import nortantis.util.Range;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Allows fast lookup of FreeIcons.
 */
public class FreeIconCollection implements Iterable<FreeIcon>
{
	/**
	 * A spatial index of icons on the map, for fast spatial lookup.
	 * This uses resolution-invariant coordinates to avoid having to scale icon coordinates with each query and avoid having to re-create the grid when the display quality changes.
	 * Icons that are off the map but not all the way off, so they aren't deleted, are placed in the grid cell determined by clamping the coordinates of their location.
	 */
	private List<FreeIcon>[][] grid;
	private double cellWidth;
	private double cellHeight;
	private int gridCols;
	private int gridRows;
	private final int mapWidthRI;
	private final int mapHeightRI;
	private final int centerCount;

	/**
	 * @param mapWidthRI Resolution-invariant width of the map.
	 * @param mapHeightRI Resolution-invariant height of the map.
	 * @param worldSize Number of Centers in the map's graph.
	 */
	public FreeIconCollection(int mapWidthRI, int mapHeightRI, int worldSize)
	{
		assert mapWidthRI > 0;
		assert mapHeightRI > 0;
		assert worldSize > 0;
		this.mapWidthRI = mapWidthRI;
		this.mapHeightRI = mapHeightRI;
		this.centerCount = worldSize;
		buildGrid();
	}

	public FreeIconCollection(FreeIconCollection other)
	{
		this(other.mapWidthRI, other.mapHeightRI, other.centerCount);
	}

	/**
	 * Build the spacial lookup grid for icons. Must be called before any icons are added to the collection.
	 */
	@SuppressWarnings("unchecked")
	void buildGrid()
	{
		// Calculate cell size based on center density.
		double avgSpacing = Math.sqrt(((double) (mapWidthRI * mapHeightRI)) / centerCount);
		cellWidth = Math.max(1, (int) (avgSpacing));
		cellHeight = Math.max(1, (int) (avgSpacing));
		gridCols = (int) ((mapWidthRI / cellWidth) + 1);
		gridRows = (int) ((mapHeightRI / cellHeight) + 1);

		// Instantiate the cells
		grid = new ArrayList[gridRows][gridCols];
		for (int row = 0; row < gridRows; row++)
		{
			for (int col = 0; col < gridCols; col++)
			{
				grid[row][col] = new ArrayList<FreeIcon>();
			}
		}
	}

	private List<FreeIcon> getCell(FreeIcon icon)
	{
		GridCoordinate coordinates = getCoordinates(icon.locationResolutionInvariant);
		return grid[coordinates.row()][coordinates.col()];
	}

	private List<FreeIcon> getCell(GridCoordinate coordinates)
	{
		return grid[coordinates.row()][coordinates.col()];
	}

	private GridCoordinate getCoordinates(Point pointRI)
	{
		int row = GeometryHelper.clamp((int) (pointRI.y / cellHeight), 0, gridRows - 1);
		int col = GeometryHelper.clamp((int) (pointRI.x / cellWidth), 0, gridCols - 1);
		return new GridCoordinate(row, col);
	}

	public synchronized void add(FreeIcon icon)
	{
		getCell(icon).add(icon);
	}

	public synchronized void replace(FreeIcon before, FreeIcon after)
	{
		remove(before);
		add(after);
	}

	public synchronized FreeIcon getNonTree(int centerIndex)
	{
		return anchoredNonTreeIcons.get(centerIndex);
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

	public synchronized List<FreeIcon> getIconsOnCenterByType(WorldGraph graph, int centerIndex, IconType type)
	{
		// Pad the bounding box a little to account for getBoundingBox not guaranteeing coverage. The 2.0 division is just my guess at how much to pad.
		Rectangle boundsInGraphSpace = graph.getBoundingBox(Collections.singleton(graph.centers.get(centerIndex))).pad(graph.getMeanCenterWidth() / 2.0);
		return getIconsInBoundsFiltered(graph, boundsInGraphSpace, Collections.singleton(centerIndex), type);
	}

	/**
	 * Finds all icons in the given boundsInGraphSpace filtered by boundsInGraphSpace and typeToInclude.
	 * Note - the bounds check with boundsInGraphSpace is based on the icons' location, not its extent.
	 * @param graph The graph the centers are from
	 * @param boundsInGraphSpace Bounding box in graph space
	 * @param indexesOfCentersToInclude Only icons whose location is on a Center with an index in this collection will be returned. Passing in null means don't filter by Centers.
	 * @param typeToInclude Only icons of this type will be returned. Null is not supported.
	 * @return
	 */
	private synchronized List<FreeIcon> getIconsInBoundsFiltered(WorldGraph graph, Rectangle boundsInGraphSpace, Set<Integer> indexesOfCentersToInclude, IconType typeToInclude)
	{
		assert typeToInclude != null;
		Rectangle boundsRI = boundsInGraphSpace.scaleAboutOrigin(1.0 / graph.resolutionScale);
		GridCoordinate upperLeft = getCoordinates(boundsRI.upperLeftCorner());
		GridCoordinate lowerRight = getCoordinates(boundsRI.lowerRightCorner());
		List<FreeIcon> found = new ArrayList<>();

		for (int row : new Range(upperLeft.row(), lowerRight.row()))
		{
			for (int col : new Range(upperLeft.col(), lowerRight.col()))
			{
				for (FreeIcon icon : getCell(new GridCoordinate(row, col)))
				{
					if (!boundsRI.contains(icon.locationResolutionInvariant))
					{
						break;
					}

					if (indexesOfCentersToInclude != null && !indexesOfCentersToInclude.isEmpty())
					{
						Center closest = graph.findClosestCenter(icon.getScaledLocation(graph.resolutionScale), true, true);
						if (closest == null || !indexesOfCentersToInclude.contains(closest.index))
						{
							continue;
						}
					}
					if (!Objects.equals(icon.type, typeToInclude))
					{
						continue;
					}

					found.add(icon);
				}
			}
		}
		return found;
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
		getCell(icon).remove(icon);
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
		// To avoid a potential deadlock, always compare this object with the one passed in the same order no matter what direction this
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
