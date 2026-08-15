package nortantis;

import nortantis.editor.FreeIcon;
import nortantis.geom.GridCoordinate;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.util.GeometryHelper;
import nortantis.util.Helper;
import nortantis.util.Range;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Allows fast lookup of FreeIcons.
 */
public class FreeIconCollection implements Iterable<FreeIcon>
{
	/**
	 * A spatial index of icons on the map, for fast spatial lookup. This uses resolution-invariant coordinates to avoid having to scale icon
	 * coordinates with each query and avoid having to re-create the grid when the display quality changes. Icons that are off the map but not
	 * all the way off, so they aren't deleted, are placed in the grid cell determined by clamping the coordinates of their location.
	 */
	private List<FreeIcon>[][] grid;
	private double cellWidth;
	private double cellHeight;
	private int gridCols;
	private int gridRows;
	private int mapWidthRI;
	private int mapHeightRI;
	private int centerCount;

	/**
	 * Maps from the index of a Center to the icons anchored to it.
	 * <p>
	 * This is not derivable from the spatial index above, because an icon's anchor is not the Center it sits on. Trees in particular are
	 * drawn scattered around their anchor's corners, so most of a Center's anchored trees are located on a neighboring Center.
	 */
	private Map<Integer, List<FreeIcon>> iconsByAnchor;

	/**
	 * Creates a collection whose spatial index has no cells, for callers that do not know the map's dimensions yet.
	 * {@link #rebuildGridForMap(int, int, int)} must be called before any icon is added.
	 */
	public FreeIconCollection()
	{
		this.mapWidthRI = 0;
		this.mapHeightRI = 0;
		this.centerCount = 0;
		buildGrid();
	}

	/**
	 * @param mapWidthRI
	 *            Resolution-invariant width of the map.
	 * @param mapHeightRI
	 *            Resolution-invariant height of the map.
	 * @param worldSize
	 *            Number of Centers in the map's graph.
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
		this.mapWidthRI = other.mapWidthRI;
		this.mapHeightRI = other.mapHeightRI;
		this.centerCount = other.centerCount;
		buildGrid();
		for (FreeIcon icon : other)
		{
			add(icon);
		}
	}

	/**
	 * Sizes the spatial index for a map with the given dimensions and number of Centers, keeping any icons already added. Does nothing if
	 * the index is already sized for those values.
	 */
	public synchronized void rebuildGridForMap(int mapWidthRI, int mapHeightRI, int worldSize)
	{
		if (this.mapWidthRI == mapWidthRI && this.mapHeightRI == mapHeightRI && this.centerCount == worldSize)
		{
			return;
		}

		assert mapWidthRI > 0;
		assert mapHeightRI > 0;
		assert worldSize > 0;

		List<FreeIcon> existing = toList();
		this.mapWidthRI = mapWidthRI;
		this.mapHeightRI = mapHeightRI;
		this.centerCount = worldSize;
		buildGrid();
		for (FreeIcon icon : existing)
		{
			add(icon);
		}
	}

	/**
	 * Builds the spacial lookup grid for icons, discarding any icons already in it.
	 */
	@SuppressWarnings("unchecked")
	private void buildGrid()
	{
		iconsByAnchor = new HashMap<>();

		if (mapWidthRI <= 0 || mapHeightRI <= 0 || centerCount <= 0)
		{
			// Not sized for a map yet, so build a grid with no cells at all. add rejects icons until rebuildGridForMap supplies real
			// dimensions, rather than letting them pile into one cell where every spatial query would degrade to a linear scan.
			cellWidth = 0;
			cellHeight = 0;
			gridCols = 0;
			gridRows = 0;
			grid = new ArrayList[0][0];
			return;
		}

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

	private GridCoordinate getCoordinates(Point pointRI)
	{
		int row = GeometryHelper.clamp((int) (pointRI.y / cellHeight), 0, gridRows - 1);
		int col = GeometryHelper.clamp((int) (pointRI.x / cellWidth), 0, gridCols - 1);
		return new GridCoordinate(row, col);
	}

	public synchronized void add(FreeIcon icon)
	{
		if (gridRows == 0 || gridCols == 0)
		{
			throw new IllegalStateException("This FreeIconCollection was created without a map's dimensions, so its spatial index has no"
					+ " cells to hold icons. Call rebuildGridForMap before adding icons.");
		}

		getCell(icon).add(icon);
		if (icon.centerIndex != null)
		{
			iconsByAnchor.computeIfAbsent(icon.centerIndex, unused -> new ArrayList<>()).add(icon);
		}
	}

	/**
	 * Adds the given icon, first removing the non-tree icon already anchored to the same Center, so that a Center never holds more than one
	 * anchored non-tree icon. Anchored trees and icons with no anchor, which includes every decoration, are simply added.
	 */
	public synchronized void addOrReplace(FreeIcon icon)
	{
		if (icon.centerIndex != null && icon.type != IconType.trees)
		{
			FreeIcon existing = getAnchoredNonTreeIcon(icon.centerIndex);
			if (existing != null)
			{
				remove(existing);
			}
		}
		add(icon);
	}

	public synchronized void replace(FreeIcon before, FreeIcon after)
	{
		remove(before);
		add(after);
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
		removeFrom(getCell(icon), icon);
		if (icon.centerIndex != null)
		{
			List<FreeIcon> anchored = iconsByAnchor.get(icon.centerIndex);
			if (anchored != null)
			{
				removeFrom(anchored, icon);
				if (anchored.isEmpty())
				{
					iconsByAnchor.remove(icon.centerIndex);
				}
			}
		}
	}

	/**
	 * Removes the given icon from the given list, preferring the instance passed in over another icon that merely has equal field values.
	 */
	private void removeFrom(List<FreeIcon> icons, FreeIcon icon)
	{
		for (int i = 0; i < icons.size(); i++)
		{
			if (icons.get(i) == icon)
			{
				icons.remove(i);
				return;
			}
		}
		// Fall back to comparing by value for callers that pass in a copy rather than the stored instance.
		icons.remove(icon);
	}

	public synchronized void clear()
	{
		buildGrid();
	}

	/**
	 * Returns the icon anchored to the given Center that is not a tree, or null if there is none.
	 * <p>
	 * Only anchored icons are considered, so an icon that merely sits on the Center without being anchored to it is never returned.
	 * Decorations are always created unanchored, which is what keeps them from being replaced when another icon is drawn on the Center they
	 * happen to sit on.
	 */
	public synchronized FreeIcon getAnchoredNonTreeIcon(int centerIndex)
	{
		for (FreeIcon icon : iconsByAnchor.getOrDefault(centerIndex, Collections.emptyList()))
		{
			if (icon.type != IconType.trees)
			{
				return icon;
			}
		}
		return null;
	}

	/**
	 * Returns the tree icons anchored to the given Center.
	 */
	public synchronized List<FreeIcon> getTrees(int centerIndex)
	{
		List<FreeIcon> result = new ArrayList<>();
		for (FreeIcon icon : iconsByAnchor.getOrDefault(centerIndex, Collections.emptyList()))
		{
			if (icon.type == IconType.trees)
			{
				result.add(icon);
			}
		}
		return result;
	}

	public synchronized boolean hasTrees(int centerIndex)
	{
		for (FreeIcon icon : iconsByAnchor.getOrDefault(centerIndex, Collections.emptyList()))
		{
			if (icon.type == IconType.trees)
			{
				return true;
			}
		}
		return false;
	}

	public synchronized void clearTrees(int centerIndex)
	{
		removeAll(getTrees(centerIndex));
	}

	/**
	 * Returns every icon of the given type whose location falls on the given Center, whether or not it is anchored to that Center.
	 */
	public synchronized List<FreeIcon> getIconsOnCenterByType(WorldGraph graph, int centerIndex, IconType type)
	{
		return getIconsOnCenterFiltered(graph, centerIndex, icon -> icon.type == type);
	}

	/**
	 * Groups the icons of the given type by the index of the Center they are anchored to.
	 *
	 * @param type
	 *            Only icons of this type are included. Null means include every type.
	 * @return A map from Center index to the icons anchored to it. Centers with no such icons are absent.
	 */
	public synchronized Map<Integer, List<FreeIcon>> groupAnchoredIconsByCenter(IconType type)
	{
		Map<Integer, List<FreeIcon>> result = new HashMap<>();
		for (Map.Entry<Integer, List<FreeIcon>> entry : iconsByAnchor.entrySet())
		{
			for (FreeIcon icon : entry.getValue())
			{
				if (type != null && icon.type != type)
				{
					continue;
				}
				result.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>()).add(icon);
			}
		}
		return result;
	}

	/**
	 * Returns the icons whose locations fall on the given Center and that pass the given filter.
	 */
	private synchronized List<FreeIcon> getIconsOnCenterFiltered(WorldGraph graph, int centerIndex, Predicate<FreeIcon> filter)
	{
		if (centerIndex < 0 || centerIndex >= graph.centers.size())
		{
			return Collections.emptyList();
		}

		// Pad the bounding box a little because getBoundingBox does not guarantee it covers everything the Center draws into.
		Rectangle boundsInGraphSpace = graph.getBoundingBox(Collections.singleton(graph.centers.get(centerIndex))).pad(graph.getMeanCenterWidth() / 2.0);
		return getIconsInBoundsFiltered(graph, boundsInGraphSpace, Collections.singleton(centerIndex), filter);
	}

	/**
	 * Finds the icons whose locations are within the given bounds, optionally narrowed to those sitting on particular Centers and to those
	 * passing a filter.
	 * <p>
	 * Note - the bounds check uses each icon's location, not the extent of the image it draws, so an icon whose image overlaps the bounds but
	 * whose location does not is excluded.
	 *
	 * @param graph
	 *            The graph the Centers are from.
	 * @param boundsInGraphSpace
	 *            Bounding box in graph space.
	 * @param indexesOfCentersToInclude
	 *            Only icons whose location is on a Center with an index in this collection are returned. Null means don't filter by Center.
	 * @param filter
	 *            Only icons passing this predicate are returned. Null means don't filter.
	 */
	private synchronized List<FreeIcon> getIconsInBoundsFiltered(WorldGraph graph, Rectangle boundsInGraphSpace, Set<Integer> indexesOfCentersToInclude,
			Predicate<FreeIcon> filter)
	{
		Rectangle boundsRI = boundsInGraphSpace.scaleAboutOrigin(1.0 / graph.resolutionScale);
		GridCoordinate upperLeft = getCoordinates(boundsRI.upperLeftCorner());
		GridCoordinate lowerRight = getCoordinates(boundsRI.lowerRightCorner());
		List<FreeIcon> found = new ArrayList<>();

		for (int row : new Range(upperLeft.row(), lowerRight.row() + 1))
		{
			for (int col : new Range(upperLeft.col(), lowerRight.col() + 1))
			{
				for (FreeIcon icon : grid[row][col])
				{
					if (filter != null && !filter.test(icon))
					{
						continue;
					}

					if (!boundsRI.contains(icon.locationResolutionInvariant))
					{
						continue;
					}

					if (indexesOfCentersToInclude != null)
					{
						Center closest = graph.findClosestCenter(icon.getScaledLocation(graph.resolutionScale), true);
						if (closest == null || !indexesOfCentersToInclude.contains(closest.index))
						{
							continue;
						}
					}

					found.add(icon);
				}
			}
		}
		return found;
	}

	/**
	 * Returns a snapshot of the icons that are anchored to a Center and are not trees.
	 */
	public synchronized List<FreeIcon> iterateAnchoredNonTreeIcons()
	{
		List<FreeIcon> result = new ArrayList<>();
		for (List<FreeIcon> anchored : iconsByAnchor.values())
		{
			for (FreeIcon icon : anchored)
			{
				if (icon.type != IconType.trees)
				{
					result.add(icon);
				}
			}
		}
		return result;
	}

	/**
	 * Returns a snapshot of the icons that are not anchored to a Center.
	 */
	public synchronized List<FreeIcon> iterateNonAnchoredIcons()
	{
		List<FreeIcon> result = new ArrayList<>();
		for (FreeIcon icon : toList())
		{
			if (icon.centerIndex == null)
			{
				result.add(icon);
			}
		}
		return result;
	}

	/**
	 * Returns a snapshot of every icon in this collection, in no particular order.
	 * <p>
	 * The result is a copy, so callers may add, remove, or replace icons while looping over it.
	 */
	public synchronized List<FreeIcon> toList()
	{
		List<FreeIcon> result = new ArrayList<>();
		for (int row = 0; row < gridRows; row++)
		{
			for (int col = 0; col < gridCols; col++)
			{
				result.addAll(grid[row][col]);
			}
		}
		return result;
	}

	@Override
	public Iterator<FreeIcon> iterator()
	{
		return toList().iterator();
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
		return areListsEqualOrderInvariant(toList(), other.toList());
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
