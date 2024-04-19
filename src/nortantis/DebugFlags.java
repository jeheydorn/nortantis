package nortantis;

import nortantis.util.AssetsPath;

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
	 * Causes the indexes of edges to be be printed to standard out when adding rivers in the Land and Water tool.
	 * This is useful when you're debugging a need to find the index of an edge for setting a conditional breakpoint.
	 */
	private static boolean printRiverEdgeIndexes = false;
	
	/**
	 * Causes the indexes of centers to be printed when hovering over them in the Land and Water tool.
	 * This is useful when you're debugging a need to find the index of a center for setting a conditional breakpoint.
	 */
	private static boolean printCenterIndexes = true;
	
	private static boolean writeBeforeAndAfterJsonWhenSavePromptShows = false;
	
	private static int[] indexesOfCentersToHighlight = new int[] {};
	
	
	public static boolean showIncrementalUpdateBounds()
	{
		return !AssetsPath.isInstalled && showIncrementalUpdateBounds;
	}
	
	public static boolean printIncrementalUpdateTimes()
	{
		return !AssetsPath.isInstalled && printIncrementalUpdateTimes;
	}

	public static boolean printRiverEdgeIndexes()
	{
		return !AssetsPath.isInstalled && printRiverEdgeIndexes; 
	}
	
	public static boolean printCenterIndexes()
	{
		return !AssetsPath.isInstalled && printCenterIndexes;
	}
	
	public static int[] getIndexesOfCentersToHighlight()
	{
		if (AssetsPath.isInstalled)
		{
			return new int[] {};
		}
		return indexesOfCentersToHighlight;
	}
	
	public static boolean shouldWriteBeforeAndAfterJsonWhenSavePromptShows()
	{
		return !AssetsPath.isInstalled && writeBeforeAndAfterJsonWhenSavePromptShows;
	}
	
}
