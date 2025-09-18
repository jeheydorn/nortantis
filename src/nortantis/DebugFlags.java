package nortantis;

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
	 * Causes the indexes of edges to be be printed to standard out when adding rivers in the Land and Water tool. This is useful when
	 * you're debugging a need to find the index of an edge for setting a conditional breakpoint.
	 */
	private static boolean printRiverEdgeIndexes = false;

	/**
	 * Causes the indexes of centers to be printed when hovering over them in the Land and Water tool. This is useful when you're debugging
	 * a need to find the index of a center for setting a conditional breakpoint.
	 */
	private static boolean printCenterIndexes = false;

	private static boolean printIconsBeingEdited = false;

	private static boolean writeBeforeAndAfterJsonWhenSavePromptShows = false;

	private static int[] indexesOfCentersToHighlight = new int[]
	{};

	private static int[] indexesOfEdgesToHighlight = new int[]
	{};

	private static boolean drawRegionBoundaryPathJoins = false;

	private static boolean drawCorners = false;

	private static boolean drawVoronoi = false;
	
	private static boolean drawRoadDebugInfo = false;

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
			return new int[]
			{};
		}
		return indexesOfCentersToHighlight;
	}

	public static int[] getIndexesOfEdgesToHighlight()
	{
		if (Assets.isRunningFromJar())
		{
			return new int[]
			{};
		}
		return indexesOfEdgesToHighlight;
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
}
