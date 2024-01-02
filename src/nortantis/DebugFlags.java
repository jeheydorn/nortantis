package nortantis;

import nortantis.util.AssetsPath;

public class DebugFlags
{
	/**
	 * Makes testing I layout changes a lot faster because you don't have to wait for a map to load if they have already been loaded and
	 * saved before, but enabling this can cause issues with the undoer, so tests with undo/redo should be done with this flag disabled.
	 */
	private static boolean enableUIBeforeMapsWithEditsLoad = true;
	
	/**
	 * Causes the replacement draw bounds for incremental updates to be drawn onto the map.
	 */
	private static boolean showIncrementalUpdateBounds = false;
	
	/**
	 * Causes the indexes of edges to be be printed to standard out when adding rivers in the Land and Water tool.
	 * This is useful when you're debugging a need to find the index of an edge for setting a conditional breakpoint.
	 */
	private static boolean printRiverEdgeIndexes = false;
	
	/**
	 * Causes the indexes of centers to be printed when hovering over them in the Land and Water tool.
	 * This is useful when you're debugging a need to find the index of a center for setting a conditional breakpoint.
	 */
	private static boolean printCenterIndexes = false;
	

	public static boolean enableUIBeforeMapsWithEditsLoad()
	{
		return !AssetsPath.isInstalled && enableUIBeforeMapsWithEditsLoad;
	}
	
	public static boolean showIncrementalUpdateBounds()
	{
		return !AssetsPath.isInstalled && showIncrementalUpdateBounds;
	}
	
	public static boolean printRiverEdgeIndexes()
	{
		return !AssetsPath.isInstalled && printRiverEdgeIndexes; 
	}
	
	public static boolean printCenterIndexes()
	{
		return !AssetsPath.isInstalled && printCenterIndexes;
	}
}
