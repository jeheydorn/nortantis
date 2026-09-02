package nortantis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import nortantis.util.Assets;

public class DebugFlags
{
	/**
	 * Causes the replacement draw bounds for incremental updates to be drawn onto the map.
	 */
	private static boolean showIncrementalUpdateBounds = false;

	/**
	 * Prints how long incremental updates take.
	 */
	private static boolean printIncrementalUpdateTimes = false;

	/**
	 * Causes the indexes of edges to be printed to standard out when adding rivers in the Land and Water tool. This is useful when you're
	 * debugging a need to find the index of an edge for setting a conditional breakpoint.
	 */
	private static boolean printRiverEdgeIndexes = false;

	/**
	 * Causes the indexes of centers to be printed when hovering over them in the Land and Water tool. This is useful when you're debugging
	 * a need to find the index of a center for setting a conditional breakpoint.
	 */
	private static boolean printCenterIndexes = false;

	private static boolean printIconsBeingEdited = false;

	private static boolean writeBeforeAndAfterJsonWhenSavePromptShows = false;

	private static int[] indexesOfCentersToHighlight = new int[] {};

	private static int[] indexesOfEdgesToHighlight = new int[] {};

	private static int[] indexesOfCornersToHighlight = new int[] {};

	private static boolean drawRegionBoundaryPathJoins = false;

	private static boolean drawCorners = false;

	private static boolean drawVoronoi = false;

	private static boolean drawRoadDebugInfo = false;

	/**
	 * Tints the border pixels that show the map instead of the border background. The geometry that decides which those are is hard to
	 * eyeball on a finished map, and this makes it directly visible, including when checking whether Nortantis reads a new piece of border
	 * art's inner edge the way it was drawn.
	 */
	private static boolean tintBorderRevealMask = false;

	/**
	 * When true, the corners used as waypoints by the sub-map river re-routing (the "Choose" detail level) are highlighted on the rendered
	 * sub-map. The waypoint corner indexes are recorded into {@link #subMapRiverWaypointCornerIndexes} by {@link SubMapCreator} as it
	 * routes each river, and drawn by {@link MapCreator}. Because the sub-map's render graph is built with the same seed and parameters as
	 * the graph used during sub-map creation, corner indexes line up between the two.
	 */
	private static boolean highlightSubMapRiverWaypoints = false;

	/**
	 * New-graph corner indexes of the waypoints used by the most recent sub-map river re-routing, populated by {@link SubMapCreator} when
	 * {@link #highlightSubMapRiverWaypoints} is on. Thread-safe so the background draw thread can read it while sub-map creation writes it.
	 */
	private static final List<Integer> subMapRiverWaypointCornerIndexes = new CopyOnWriteArrayList<>();

	public static boolean showIncrementalUpdateBounds()
	{
		return !Assets.isRunningFromJar() && showIncrementalUpdateBounds;
	}

	public static boolean printIncrementalUpdateTimes()
	{
		return !Assets.isRunningFromJar() && printIncrementalUpdateTimes;
	}

	public static boolean printRiverEdgeIndexes()
	{
		return !Assets.isRunningFromJar() && printRiverEdgeIndexes;
	}

	public static boolean printCenterIndexes()
	{
		return !Assets.isRunningFromJar() && printCenterIndexes;
	}

	public static int[] getIndexesOfCentersToHighlight()
	{
		if (Assets.isRunningFromJar())
		{
			return new int[] {};
		}
		return indexesOfCentersToHighlight;
	}

	public static int[] getIndexesOfEdgesToHighlight()
	{
		if (Assets.isRunningFromJar())
		{
			return new int[] {};
		}
		return indexesOfEdgesToHighlight;
	}

	public static int[] getIndexesOfCornersToHighlight()
	{
		if (Assets.isRunningFromJar())
		{
			return new int[] {};
		}
		return indexesOfCornersToHighlight;
	}

	public static boolean shouldWriteBeforeAndAfterJsonWhenSavePromptShows()
	{
		return !Assets.isRunningFromJar() && writeBeforeAndAfterJsonWhenSavePromptShows;
	}

	public static boolean printIconsBeingEdited()
	{
		return !Assets.isRunningFromJar() && printIconsBeingEdited;
	}

	public static boolean drawRegionBoundaryPathJoins()
	{
		return !Assets.isRunningFromJar() && drawRegionBoundaryPathJoins;
	}

	public static boolean drawCorners()
	{
		return !Assets.isRunningFromJar() && drawCorners;
	}

	public static boolean drawVoronoi()
	{
		return !Assets.isRunningFromJar() && drawVoronoi;
	}

	public static boolean drawRoadDebugInfo()
	{
		return !Assets.isRunningFromJar() && drawRoadDebugInfo;
	}

	public static boolean tintBorderRevealMask()
	{
		return !Assets.isRunningFromJar() && tintBorderRevealMask;
	}

	public static boolean highlightSubMapRiverWaypoints()
	{
		return !Assets.isRunningFromJar() && highlightSubMapRiverWaypoints;
	}

	/**
	 * Clears the recorded sub-map river waypoint corner indexes. Called at the start of each sub-map river transfer so the highlights
	 * reflect only the most recent sub-map.
	 */
	public static void clearSubMapRiverWaypointCornerIndexes()
	{
		subMapRiverWaypointCornerIndexes.clear();
	}

	/**
	 * Records a new-graph corner index used as a river waypoint, so it can be highlighted on the rendered sub-map.
	 */
	public static void addSubMapRiverWaypointCornerIndex(int cornerIndex)
	{
		subMapRiverWaypointCornerIndexes.add(cornerIndex);
	}

	public static List<Integer> getSubMapRiverWaypointCornerIndexes()
	{
		if (Assets.isRunningFromJar())
		{
			return new CopyOnWriteArrayList<>();
		}
		return subMapRiverWaypointCornerIndexes;
	}
}
