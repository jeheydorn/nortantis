package nortantis;

import nortantis.editor.FreeIcon;
import nortantis.editor.River;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.MapEdits;
import nortantis.swing.SubMapDialog;
import nortantis.util.OrderlessPair;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SubMapCreatorTest
{
	static final String failedMapsFolderName = "failed sub-maps";

	// Set this to true to make every test write its result map to the failed sub-maps folder, so the actual results can be viewed even when
	// the tests pass. Each test also has a local forceWrite flag near its top that does the same for that single test.
	private static final boolean forceWriteAllMaps = false;

	@BeforeAll
	public static void setUpBeforeClass() throws IOException
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", failedMapsFolderName).toString()));
	}

	/**
	 * Verifies that when a sub-map contains two rivers that form a confluence in the source map, those rivers are still connected via a
	 * shared corner in the sub-map's edge edits. This is a regression test for a bug in SubMapCreator where the last edge of a tributary
	 * was incorrectly removed by simplifyToPath, preventing the confluence from being transferred.
	 */
	@Test
	public void subMapRiversFormConfluence() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(1324, 999, 1307, 1307);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 1962328436L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertEquals(2, rivers.size(), "Sub-map should contain exactly 2 rivers (expected a main river and a tributary)");

		assertRiversAreAllConnected(rivers, subMapSettings, "subMapRiversFormConfluence");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRiversFormConfluence");
		}
	}

	/**
	 * Verifies that createSubMapSettings records sub-map provenance (original file name, selection box, detail, icon/river mode, and seed)
	 * so the user can recreate the sub-map, and that this info survives a save/load round-trip through the .nort file.
	 */
	@Test
	public void subMapInfoIsRecordedAndRoundTrips() throws Exception
	{
		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		Rectangle selectionBoundsRI = new Rectangle(1324, 999, 1307, 1307);
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);
		long seed = 1962328436L;
		String originalFileName = "riversForSubMaps.nort";

		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, false, originalFileName);

		assertSubMapInfoMatches(subMapSettings.subMapInfo, originalFileName, selectionBoundsRI, worldSize, seed, false, "in-memory");

		// deepCopy goes through Java serialization (Helper.deepCopy), so SubMapInfo must be Serializable. This mirrors what
		// MainWindow.updateLastSettingsLoadedOrSaved does on every load and would crash if SubMapInfo were not serializable.
		MapSettings copied = subMapSettings.deepCopy();
		assertSubMapInfoMatches(copied.subMapInfo, originalFileName, selectionBoundsRI, worldSize, seed, false, "after deepCopy");

		// The recorded info must survive a save/load round-trip through the .nort file.
		File tempFile = File.createTempFile("subMapInfoRoundTrip", MapSettings.fileExtensionWithDot);
		try
		{
			subMapSettings.writeToFile(tempFile.getAbsolutePath());
			MapSettings reloaded = new MapSettings(tempFile.getAbsolutePath());
			assertSubMapInfoMatches(reloaded.subMapInfo, originalFileName, selectionBoundsRI, worldSize, seed, false, "after reload");
		}
		finally
		{
			tempFile.delete();
		}
	}

	private static void assertSubMapInfoMatches(MapSettings.SubMapInfo info, String originalFileName, Rectangle selectionBoundsRI, int worldSize, long seed, boolean redistributeIconsAndRivers,
			String context)
	{
		assertNotNull(info, "Sub-map should carry provenance info (" + context + ")");
		assertEquals(originalFileName, info.originalFileName, context);
		assertEquals(selectionBoundsRI.x, info.selectionX, 0.0, context);
		assertEquals(selectionBoundsRI.y, info.selectionY, 0.0, context);
		assertEquals(selectionBoundsRI.width, info.selectionWidth, 0.0, context);
		assertEquals(selectionBoundsRI.height, info.selectionHeight, 0.0, context);
		assertEquals(worldSize, info.worldSize, context);
		assertEquals(seed, info.randomSeed, context);
		assertEquals(redistributeIconsAndRivers, info.redistributeIconsAndRivers, context);
	}

	/**
	 * Verifies that rivers in a sub-map contain no finger branches. A finger is a branch point — a corner with degree &gt; 2 in the
	 * combined river edge graph — that was not present in the original map. The original map for this test case has no such branches.
	 * <p>
	 * Note: {@link WorldGraph#findRivers()} separates branch arms into distinct {@link River} objects, so checking degree within a single
	 * river's edges would never detect fingers. Instead this test builds the degree map from the unique set of all edges across all rivers.
	 * </p>
	 */
	@Test
	public void subMapRiversHaveNoFingers() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, 1348, 4096);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 983909832L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertRiversHaveNoFingers(rivers, subMapSettings, "subMapRiversHaveNoFingers");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRiversHaveNoFingers");
		}
	}

	/**
	 * Verifies that rivers in a sub-map contain no fingers or loops for a sub-map with a complex river network.
	 */
	@Test
	public void subMapComplexRiversHaveNoFingersOrLoops() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(2515, 1471, 1581, 2625);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 1735631519L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertRiversHaveNoLoops(rivers, subMapSettings, "subMapComplexRiversHaveNoFingersOrLoops");
		assertRiversHaveNoFingers(rivers, subMapSettings, "subMapComplexRiversHaveNoFingersOrLoops");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapComplexRiversHaveNoFingersOrLoops");
		}
	}

	/**
	 * Verifies that rivers in a sub-map contain no loops. A loop exists when the river's edges form a cycle, detected by checking that the
	 * edge count exceeds corner count minus one (the invariant for a simple path).
	 */
	@Test
	public void subMapRiversHaveNoLoops() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, 1348, 4096);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 384811022L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertRiversHaveNoLoops(rivers, subMapSettings, "subMapRiversHaveNoLoops");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRiversHaveNoLoops");
		}
	}

	/**
	 * Regression test for a redistribute-mode sub-map whose re-routed river formed a loop that was not present in the source river. Rebuilt
	 * from the provenance of {@code submapWhereRiverFormLoop.nort}: source {@code riversForSubMaps.nort}, selection and seed below.
	 */
	@Test
	public void subMapRedistributedRiverFormsNoLoop() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		Rectangle selectionBoundsRI = new Rectangle(2527, 182, 964, 2216);
		int worldSize = 1000;
		long seed = 1804479318L;

		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertRiversHaveNoLoops(rivers, subMapSettings, "subMapRedistributedRiverFormsNoLoop");
		assertRiversHaveNoSelfCrossings(rivers, subMapSettings, "subMapRedistributedRiverFormsNoLoop");
		assertCombinedRiverNetworkHasNoLoop(rivers, subMapSettings, "subMapRedistributedRiverFormsNoLoop");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRedistributedRiverFormsNoLoop");
		}
	}

	/**
	 * Regression test for a redistribute-mode sub-map whose re-routed river formed a loop within a single polygon at the edge of the map.
	 * Rebuilt from the provenance of {@code submapWhereRiverFormLoopAtTheEdgeOfTheMap.nort}.
	 */
	@Test
	public void subMapRedistributedRiverFormsNoLoopAtMapEdge() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		Rectangle selectionBoundsRI = new Rectangle(2588, 1139, 884, 1056);
		int worldSize = 1000;
		long seed = 2025862360L;

		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertRiversHaveNoLoops(rivers, subMapSettings, "subMapRedistributedRiverFormsNoLoopAtMapEdge");
		assertRiversHaveNoSelfCrossings(rivers, subMapSettings, "subMapRedistributedRiverFormsNoLoopAtMapEdge");
		assertCombinedRiverNetworkHasNoLoop(rivers, subMapSettings, "subMapRedistributedRiverFormsNoLoopAtMapEdge");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRedistributedRiverFormsNoLoopAtMapEdge");
		}
	}

	/**
	 * Regression test for a redistribute-mode sub-map where a source river that passes through the edge of the selected region failed to
	 * reach the edge of the sub-map. Rebuilt from the provenance of {@code submapWhereRiverDoesNotReachEdgeOfMap.nort}.
	 */
	@Test
	public void subMapRedistributedRiverReachesMapEdge() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		Rectangle selectionBoundsRI = new Rectangle(2453, 212, 1056, 2124);
		int worldSize = 1000;
		long seed = 1502091017L;

		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		assertRedistributedRiverReachesMapEdge(originalSettings, selectionBoundsRI, subMapSettings, "subMapRedistributedRiverReachesMapEdge");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRedistributedRiverReachesMapEdge");
		}
	}

	/**
	 * Second regression test for a redistribute-mode sub-map where a source river that passes through the edge of the selected region
	 * failed to reach the edge of the sub-map. Rebuilt from the provenance of {@code submapWhereRiverDoesNotReachEdgeOfMap2.nort}.
	 */
	@Test
	public void subMapRedistributedRiverReachesMapEdge2() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		Rectangle selectionBoundsRI = new Rectangle(2496, 1127, 1062, 1099);
		int worldSize = 1000;
		long seed = 922176153L;

		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		assertRedistributedRiverReachesMapEdge(originalSettings, selectionBoundsRI, subMapSettings, "subMapRedistributedRiverReachesMapEdge2");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRedistributedRiverReachesMapEdge2");
		}
	}

	/**
	 * Verifies that a T-shaped river in a sub-map has 3 mouths where it meets the ocean. Sub-map rivers are stored as freehand
	 * {@link River} polylines (not tied to the new graph's edges), so a mouth is counted as a river-path endpoint whose location is
	 * adjacent to a coast or ocean corner in the sub-map graph. This is a regression test for a bug where one arm of the T was incorrectly
	 * dropped, leaving only 2 mouths.
	 */
	@Test
	public void subMapTShapedRiverHasThreeMouths() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(1158, 1115, 1559, 1092);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 595862260L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		WorldGraph newGraph = MapCreator.createGraphForUnitTests(subMapSettings);

		List<River> rivers = subMapSettings.edits.rivers;

		assertNoDuplicateRiverSegments(rivers, subMapSettings, "subMapTShapedRiverHasThreeMouths");

		long mouthCount = 0;
		for (River river : rivers)
		{
			if (riverEndpointTouchesCoastOrOcean(river.nodes.get(0).getLoc(), newGraph, subMapSettings.resolution))
				mouthCount++;
			if (riverEndpointTouchesCoastOrOcean(river.nodes.get(river.nodes.size() - 1).getLoc(), newGraph, subMapSettings.resolution))
				mouthCount++;
		}

		if (mouthCount != 3)
		{
			String failedMapPath = saveFailedMap(subMapSettings, "subMapTShapedRiverHasThreeMouths");
			fail("Expected the T-shaped river to have 3 mouths meeting the ocean, but found " + mouthCount + ".\nFailed map written to: " + failedMapPath);
		}
		else if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapTShapedRiverHasThreeMouths");
		}
	}

	/**
	 * Verifies that a T-shaped river in a sub-map forms its intersection: the three arms meet at a shared junction in the interior of the
	 * map rather than ending as disconnected rivers. Same source map and selection as {@link #subMapTShapedRiverHasThreeMouths}, but a
	 * different seed whose finer graph routes the arms so they come close in the middle without connecting. The arms reach the coast (3
	 * mouths), so a mouth-count check alone would pass; this asserts the rivers form a single connected network so the missing junction is
	 * caught.
	 */
	@Test
	public void subMapTShapedRiverFormsIntersection() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(1158, 1115, 1559, 1092);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 1791199644L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertRiversAreAllConnected(rivers, subMapSettings, "subMapTShapedRiverFormsIntersection");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapTShapedRiverFormsIntersection");
		}
	}

	/**
	 * Verifies that a T-shaped river in a sub-map has all its arms connected at the junction. This is a regression test for a bug where one
	 * arm of the T was disconnected from the others.
	 */
	@Test
	public void subMapRiversFormConfluence2() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(661, 18, 2013, 2335);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 1222331460L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		WorldGraph newGraph = MapCreator.createGraphForUnitTests(subMapSettings);

		List<River> rivers = subMapSettings.edits.rivers;

		assertRiversAreAllConnected(rivers, subMapSettings, "subMapRiversFormConfluence2");

		// The three arms of the T each terminate at the coast, so the sub-map must have three coastal mouths.
		// This guards against an arm regressing to stop inland (the bug attachMouths originally fixed): connectivity
		// alone would still pass if a dropped mouth left the arm joined to the trunk but not reaching the sea.
		long mouthCount = 0;
		for (River river : rivers)
		{
			if (riverEndpointTouchesCoastOrOcean(river.nodes.get(0).getLoc(), newGraph, subMapSettings.resolution))
				mouthCount++;
			if (riverEndpointTouchesCoastOrOcean(river.nodes.get(river.nodes.size() - 1).getLoc(), newGraph, subMapSettings.resolution))
				mouthCount++;
		}
		if (mouthCount != 3)
		{
			String failedMapPath = saveFailedMap(subMapSettings, "subMapRiversFormConfluence2");
			fail("Expected the confluence to have 3 coastal mouths, but found " + mouthCount + ".\nFailed map written to: " + failedMapPath);
		}

		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRiversFormConfluence2");
		}
	}

	/**
	 * Verifies that the single re-routed river in this sub-map follows the new graph's Voronoi edges throughout, with no freehand jumps. In
	 * redistribute mode the router falls back to connecting two waypoints with a direct freehand hop (a segment that follows no Voronoi
	 * edge, recorded as {@link nortantis.editor.RiverPathNode#EDGE_INDEX_NONE}) when greedy routing between them fails or wanders too far.
	 * Such a hop renders as a straight line cutting across polygons. For this sub-map the river should route cleanly along graph edges, so
	 * any freehand jump indicates the router had to patch around a routing failure.
	 */
	@Test
	public void subMapRiverHasNoFreehandJumps() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, 1183, 1839);

		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 174503823L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		assertEquals(1, rivers.size(), "Sub-map should contain exactly 1 river");

		assertRiversHaveNoFreehandJumps(rivers, subMapSettings, "subMapRiverHasNoFreehandJumps");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapRiverHasNoFreehandJumps");
		}
	}

	/**
	 * Verifies that a sub-map covering the entire source map at the original detail level (a 1× "Match source detail" sub-map) preserves
	 * every source river faithfully — none dropped. "Match source detail" copies rivers verbatim (no loop removal, no re-routing), so a
	 * full-map copy must reproduce all of them. {@code riversForSubMaps.nort} intentionally contains a complex river that revisits points;
	 * exact-copy reproduces it as-is, so this test deliberately does NOT assert loop-freedom — that would test source-data quality, not the
	 * copy. (It is a regression test for a bug where {@code removeLoops} silently deleted that river.)
	 */
	@Test
	public void subMapOfEntireMapAtOriginalDetailRivers() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Selection bounds covering the entire source map, in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, originalSettings.generatedWidth, originalSettings.generatedHeight);

		// For a full-map selection this returns the original world size, i.e. the original detail level ("Match source detail").
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);
		assertEquals(originalSettings.worldSize, worldSize, "A full-map selection should default to the source map's world size (original detail level)");

		int sourceRiverCount = originalSettings.edits.rivers.size();

		long seed = 1402779553L;
		// redistributeIcons=false: original detail level means "Match source detail", which copies rivers over exactly.
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, false);

		List<River> rivers = subMapSettings.edits.rivers;

		// A full-map exact-copy clips nothing, so every source river must survive 1:1 — none dropped or split.
		assertEquals(sourceRiverCount, rivers.size(), "Full-map exact-copy should preserve every source river (none dropped)");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapOfEntireMapAtOriginalDetailRivers");
		}
	}

	/**
	 * Verifies that a sub-map covering the entire source map at the original detail level (a 1× "Match source detail" sub-map) preserves
	 * every source river faithfully for {@code simpleSmallWorld.nort} — none dropped. "Match source detail" copies rivers verbatim, so a
	 * full-map copy must reproduce all of them.
	 */
	@Test
	public void subMapOfEntireMapAtOriginalDetailSimpleSmallWorld() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Selection bounds covering the entire source map, in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, originalSettings.generatedWidth, originalSettings.generatedHeight);

		// For a full-map selection this returns the original world size, i.e. the original detail level ("Match source detail").
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);
		assertEquals(originalSettings.worldSize, worldSize, "A full-map selection should default to the source map's world size (original detail level)");

		int sourceRiverCount = originalSettings.edits.rivers.size();

		long seed = 768241095L;
		// redistributeIcons=false: original detail level means "Match source detail", which copies rivers over exactly.
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, false);

		List<River> rivers = subMapSettings.edits.rivers;

		// A full-map exact-copy clips nothing, so every source river must survive 1:1 — none dropped or split.
		assertEquals(sourceRiverCount, rivers.size(), "Full-map exact-copy should preserve every source river (none dropped)");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapOfEntireMapAtOriginalDetailSimpleSmallWorld");
		}
	}

	/**
	 * Like {@link #subMapOfEntireMapAtOriginalDetailRivers}, but at a detail level a little higher than the source (1.5× the polygon
	 * count), which means rivers and icons are <em>redistributed</em> ({@code redistributeIcons == true}) — re-routed through the new,
	 * finer graph rather than copied verbatim. Even re-routed, a full-map redistribution must not drop any river: each source river is
	 * re-routed into one sub-map river, so the count is preserved. (Some segments may be freehand jumps where the finer topology forces
	 * them; that is allowed here, unlike {@link #subMapRiverHasNoFreehandJumps} which targets a specific clean-routing case.)
	 */
	@Test
	public void subMapOfEntireMapAtHigherDetailRivers() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Selection bounds covering the entire source map, in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, originalSettings.generatedWidth, originalSettings.generatedHeight);

		// A detail level a little higher than the source: 1.5x the source polygon count (createSubMapSettings clamps to the valid range).
		int higherWorldSize = (int) Math.round(originalSettings.worldSize * 1.5);

		int sourceRiverCount = originalSettings.edits.rivers.size();

		long seed = 1402779553L;
		// redistributeIcons=true: a higher-than-source detail level redistributes rivers and icons through the new graph.
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, higherWorldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		// A full-map redistribution re-routes each source river into one sub-map river, so none should be dropped.
		assertEquals(sourceRiverCount, rivers.size(), "Full-map higher-detail redistribution should re-route every source river (none dropped)");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapOfEntireMapAtHigherDetailRivers");
		}
	}

	/**
	 * Like {@link #subMapOfEntireMapAtOriginalDetailSimpleSmallWorld}, but at a detail level a little higher than the source (1.5× the
	 * polygon count), which redistributes rivers and icons ({@code redistributeIcons == true}) through the new, finer graph rather than
	 * copying them verbatim. A full-map redistribution re-routes each source river into one sub-map river, so the river count is preserved.
	 */
	@Test
	public void subMapOfEntireMapAtHigherDetailSimpleSmallWorld() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Selection bounds covering the entire source map, in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, originalSettings.generatedWidth, originalSettings.generatedHeight);

		// A detail level a little higher than the source: 1.5x the source polygon count (createSubMapSettings clamps to the valid range).
		int higherWorldSize = (int) Math.round(originalSettings.worldSize * 1.5);

		int sourceRiverCount = originalSettings.edits.rivers.size();

		long seed = 768241095L;
		// redistributeIcons=true: a higher-than-source detail level redistributes rivers and icons through the new graph.
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, higherWorldSize, originalSettings.resolution, seed, true);

		List<River> rivers = subMapSettings.edits.rivers;

		// A full-map redistribution re-routes each source river into one sub-map river, so none should be dropped.
		assertEquals(sourceRiverCount, rivers.size(), "Full-map higher-detail redistribution should re-route every source river (none dropped)");
		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapOfEntireMapAtHigherDetailSimpleSmallWorld");
		}
	}

	/**
	 * Verifies that when a region is copied at "match source detail" ({@code redistributeIcons == false}), each source river mouth still
	 * reaches the water on the sub-map's redrawn coastline. The sub-map is drawn on a fresh Voronoi grid, so its coast/lakeshore noisy
	 * edges differ slightly from the source; a river copied at its exact source position can therefore end short of the redrawn water,
	 * which looks broken. {@link SubMapCreator} fixes this by extending such mouths with a short connector onto the redrawn water.
	 * <p>
	 * The check maps each source river endpoint that is a water mouth in the source and lies inside the selection to its position on the
	 * sub-map, finds the nearest sub-map river endpoint, and asserts that endpoint reaches the redrawn water. Reach is tested with
	 * {@link SubMapCreator#doesPointReachWater}, which follows the drawn (noisy-edge) coastline pixel-accurately, so a mouth that ends a
	 * sub-polygon short of the water counts as not reaching it — a gap the coarser corner-level check cannot see. For this selection and
	 * seed, two of the three mouths fall short without the connector (so this test fails if the connector is removed).
	 * </p>
	 */
	@Test
	public void subMapExactCopyRiverMouthsReachRedrawnCoast() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversForSubMaps.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-region selection in RI coordinates (zoomed in, so the coastline mismatch is magnified).
		Rectangle selectionBoundsRI = new Rectangle(1158, 1115, 1559, 1092);
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 5L;
		// redistributeIcons=false: "match source detail" copies rivers exactly, then extends mouths to the redrawn coast.
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, false);
		WorldGraph newGraph = MapCreator.createGraphForUnitTests(subMapSettings);

		// Sub-map river endpoints, to match each source mouth to its copied-over end.
		List<Point> subMapEndpoints = new java.util.ArrayList<>();
		for (River river : subMapSettings.edits.rivers)
		{
			subMapEndpoints.add(river.nodes.get(0).getLoc());
			subMapEndpoints.add(river.nodes.get(river.nodes.size() - 1).getLoc());
		}

		int sourceMouthsInSelection = 0;
		int reachedCoast = 0;
		for (River sourceRiver : originalSettings.edits.rivers)
		{
			for (int end = 0; end < 2; end++)
			{
				Point sourcePoint = (end == 0 ? sourceRiver.nodes.get(0) : sourceRiver.nodes.get(sourceRiver.nodes.size() - 1)).getLoc();
				boolean isSourceMouth = selectionBoundsRI.contains(sourcePoint.x, sourcePoint.y) && riverEndpointTouchesCoastOrOcean(sourcePoint, originalGraph, originalSettings.resolution);
				if (!isSourceMouth)
				{
					continue;
				}
				sourceMouthsInSelection++;
				// Where this mouth lands on the sub-map, and the nearest sub-map river endpoint to it.
				Point subMapPoint = new Point((sourcePoint.x - selectionBoundsRI.x) / selectionBoundsRI.width * subMapSettings.generatedWidth,
						(sourcePoint.y - selectionBoundsRI.y) / selectionBoundsRI.height * subMapSettings.generatedHeight);
				Point nearestEnd = null;
				double bestDistance = Double.MAX_VALUE;
				for (Point endpoint : subMapEndpoints)
				{
					if (endpoint.distanceTo(subMapPoint) < bestDistance)
					{
						bestDistance = endpoint.distanceTo(subMapPoint);
						nearestEnd = endpoint;
					}
				}
				// Pixel-accurate check against the drawn (noisy-edge) coastline, so a mouth that ends a sub-polygon short of the redrawn
				// water is counted as NOT reaching it. The coarser corner-level riverEndpointTouchesCoastOrOcean cannot see that gap.
				if (nearestEnd != null && SubMapCreator.doesPointReachWater(nearestEnd.mult(subMapSettings.resolution), newGraph))
				{
					reachedCoast++;
				}
			}
		}

		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapExactCopyRiverMouthsReachRedrawnCoast");
		}
		assertTrue(sourceMouthsInSelection > 0, "Expected the selection to contain at least one source river mouth");
		assertEquals(sourceMouthsInSelection, reachedCoast,
				"Every source river mouth should reach the sub-map's redrawn coast, but " + (sourceMouthsInSelection - reachedCoast) + " of " + sourceMouthsInSelection + " did not");
	}

	/**
	 * Regression test for a bug where, at "match source detail" ({@code redistributeIcons == false}), rivers from a <em>generated</em>
	 * source map fail to reach the ocean in the sub-map even though the connector code (added for the match-source-detail case) is supposed
	 * to extend such mouths to the redrawn coast. The map {@code riversInSubmapsMeetWater.nort} was reduced by hand to exactly two rivers,
	 * both of which end at the ocean in the source. The bug reproduces with generated rivers but not with hand-drawn ones, so this captures
	 * the generated case.
	 * <p>
	 * The check mirrors {@link #subMapExactCopyRiverMouthsReachRedrawnCoast}: each source river endpoint that is a water mouth in the
	 * source and lies inside the selection is mapped to the sub-map, and the nearest sub-map river endpoint must reach the redrawn water
	 * per {@link SubMapCreator#doesPointReachWater}.
	 * </p>
	 */
	@Test
	public void subMapGeneratedRiverMouthsReachOcean() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "riversInSubmapsMeetWater.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);

		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		Rectangle selectionBoundsRI = new Rectangle(1043, 53, 808, 708);
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);

		long seed = 323066151L;
		// redistributeIcons=false: "match source detail" copies rivers exactly, then extends mouths to the redrawn coast.
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, false);
		WorldGraph newGraph = MapCreator.createGraphForUnitTests(subMapSettings);

		// Sub-map river endpoints, to match each source mouth to its copied-over end.
		List<Point> subMapEndpoints = new ArrayList<>();
		for (River river : subMapSettings.edits.rivers)
		{
			subMapEndpoints.add(river.nodes.get(0).getLoc());
			subMapEndpoints.add(river.nodes.get(river.nodes.size() - 1).getLoc());
		}

		int sourceMouthsInSelection = 0;
		int reachedCoast = 0;
		for (River sourceRiver : originalSettings.edits.rivers)
		{
			for (int end = 0; end < 2; end++)
			{
				Point sourcePoint = (end == 0 ? sourceRiver.nodes.get(0) : sourceRiver.nodes.get(sourceRiver.nodes.size() - 1)).getLoc();
				boolean isSourceMouth = selectionBoundsRI.contains(sourcePoint.x, sourcePoint.y) && riverEndpointTouchesCoastOrOcean(sourcePoint, originalGraph, originalSettings.resolution);
				if (!isSourceMouth)
				{
					continue;
				}
				sourceMouthsInSelection++;
				Point subMapPoint = new Point((sourcePoint.x - selectionBoundsRI.x) / selectionBoundsRI.width * subMapSettings.generatedWidth,
						(sourcePoint.y - selectionBoundsRI.y) / selectionBoundsRI.height * subMapSettings.generatedHeight);
				Point nearestEnd = null;
				double bestDistance = Double.MAX_VALUE;
				for (Point endpoint : subMapEndpoints)
				{
					if (endpoint.distanceTo(subMapPoint) < bestDistance)
					{
						bestDistance = endpoint.distanceTo(subMapPoint);
						nearestEnd = endpoint;
					}
				}
				if (nearestEnd != null && SubMapCreator.doesPointReachWater(nearestEnd.mult(subMapSettings.resolution), newGraph))
				{
					reachedCoast++;
				}
			}
		}

		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapGeneratedRiverMouthsReachOcean");
		}
		assertTrue(sourceMouthsInSelection > 0, "Expected the selection to contain at least one source river mouth");
		assertEquals(sourceMouthsInSelection, reachedCoast,
				"Every source river mouth should reach the sub-map's redrawn ocean, but " + (sourceMouthsInSelection - reachedCoast) + " of " + sourceMouthsInSelection + " did not");
	}

	/**
	 * Verifies that when a small region is extracted from {@code simpleSmallWorld.nort} — too small to match the source detail level, so it
	 * is redistributed ({@code redistributeIconsAndRivers == true}) — the source icons inside the selection are carried into the sub-map.
	 * Specifically the selection contains an "octopus" decoration (group "creatures") and a "town with castle" city (group "flat"), and the
	 * resulting {@link MapSettings} should contain exactly one of each. The selection is far below the minimum polygon count for "Match
	 * source detail", which is why redistribution is forced.
	 * <p>
	 * This is a regression test for a bug where these icons vanished only in the editor (not in earlier single-resolution tests). The
	 * editor builds the source graph at the display quality scale but {@code getSettingsFromGUI} sets {@code originalSettings.resolution}
	 * to the (different) export resolution, while {@code SubMapDialog} passes the display scale as {@code originalResolution}.
	 * {@code SubMapCreator} built its source {@code IconDrawer} from {@code originalSettings}, so city/decoration draw bounds were scaled
	 * by the export resolution but converted back to RI with the display resolution — landing outside the selection, so
	 * {@code doesIconOverlapSelection} rejected them all. This test reproduces that by building the graph at one resolution and then
	 * setting {@code originalSettings.resolution} to a different value before calling {@code createSubMapSettings} (using the editor values
	 * that triggered the report: display 0.75, export 0.25). The icons are checked both in the in-memory result and after a JSON
	 * round-trip.
	 * </p>
	 */
	@Test
	public void subMapPreservesDecorationAndCityIcons() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);

		// In the editor, the source graph is built at the display quality scale, and SubMapDialog passes that scale as originalResolution.
		double displayResolution = 0.75;
		originalSettings.resolution = displayResolution;
		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// getSettingsFromGUI overwrites originalSettings.resolution with the export resolution, which can differ from the display scale.
		// Set
		// it here so originalSettings.resolution != originalResolution, exactly as it is in the editor when the bug appears.
		originalSettings.resolution = 0.25;

		// Sub-map selection bounds in RI (resolution-invariant) coordinates.
		Rectangle selectionBoundsRI = new Rectangle(152, 1549, 627, 389);

		// This selection is far too small to reach the minimum polygon count, so "Match source detail" is unavailable and the sub-map must
		// be redistributed. computeDefaultWorldSize clamps the 1x polygon count up to that minimum, which we verify mirrors the dialog's
		// decision to force redistribution.
		double oneXWorldSize = originalSettings.worldSize * (selectionBoundsRI.width * selectionBoundsRI.height) / (originalSettings.generatedWidth * (double) originalSettings.generatedHeight);
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);
		boolean redistributeIconsAndRivers = oneXWorldSize < worldSize;
		assertTrue(redistributeIconsAndRivers, "Selection should be too small for 'Match source detail', forcing redistribution");

		long seed = 643229385L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, displayResolution, seed, redistributeIconsAndRivers);

		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapPreservesDecorationAndCityIcons");
		}

		// The in-memory result of createSubMapSettings should contain both icons.
		assertSubMapHasExpectedIcons(subMapSettings, "in-memory sub-map settings");

		// Round-trip the settings through JSON serialization (writeToFile) and deserialization (new MapSettings) using MapSettings's own
		// APIs, then re-check, to confirm the icons survive a save/reload as well as living in memory.
		File tempNort = File.createTempFile("subMapPreservesDecorationAndCityIcons", ".nort");
		try
		{
			subMapSettings.writeToFile(tempNort.getAbsolutePath());
			MapSettings reloadedSettings = new MapSettings(tempNort.getAbsolutePath());
			assertSubMapHasExpectedIcons(reloadedSettings, "sub-map settings reloaded from JSON");
		}
		finally
		{
			tempNort.delete();
		}
	}

	/**
	 * Asserts that {@code settings} contains exactly one "octopus" decoration (group "creatures") and exactly one "town with castle" city
	 * (group "flat") among its free icons. {@code context} is included in failure messages to identify which stage failed (in-memory vs.
	 * reloaded from JSON).
	 */
	private static void assertSubMapHasExpectedIcons(MapSettings settings, String context)
	{
		int octopusCount = 0;
		int townWithCastleCount = 0;
		for (FreeIcon icon : settings.edits.freeIcons)
		{
			if (icon.type == IconType.decorations && "creatures".equals(icon.groupId) && "octopus".equals(icon.iconName))
			{
				octopusCount++;
			}
			else if (icon.type == IconType.cities && "flat".equals(icon.groupId) && "town with castle".equals(icon.iconName))
			{
				townWithCastleCount++;
			}
		}

		assertEquals(1, octopusCount, "Expected exactly 1 'octopus' decoration from group 'creatures' (" + context + ")");
		assertEquals(1, townWithCastleCount, "Expected exactly 1 'town with castle' city from group 'flat' (" + context + ")");
	}

	/**
	 * Verifies that coastline cities in a redistributed sub-map are actually drawn (not just present in the edits). The selection contains
	 * a "town with castle" and a "town on two hills" (both group "flat") near the coast; both must appear in the rendered map.
	 * <p>
	 * Regression test for a bug where these cities vanished from the sub-map. A redistributed sub-map stores its mountains/hills/trees as
	 * {@code CenterIcon}s, which {@link IconDrawer#addOrUpdateIconsFromEdits} converts to free icons on the first draw. That conversion
	 * returned a non-null bounds, and on a full draw (where {@code replaceBounds} is null) the bounds were used as a draw filter, so
	 * free-icon cities lying outside the converted-terrain bounding box were skipped — coastal cities (away from the inland mountains) fell
	 * outside it. The icons remained in {@code edits.freeIcons} (so a membership check passed falsely); they were only missing from the
	 * draw tasks. This test asserts the cities are in the {@link IconDrawer}'s draw tasks after a full draw, which catches the skip.
	 * </p>
	 */
	@Test
	public void subMapCoastlineCitiesAreNotDroppedByWaterCheck() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		String originalSettingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings originalSettings = new MapSettings(originalSettingsPath);
		originalSettings.resolution = 0.5;
		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// Sub-map selection bounds in RI (resolution-invariant) coordinates. Contains a "town with castle" at ~(657, 1920) and a
		// "town on two hills" at ~(770, 1981), both near the coastline.
		Rectangle selectionBoundsRI = new Rectangle(410, 1710, 787, 621);

		// Selection too small for "Match source detail", so icons and rivers are redistributed.
		double oneXWorldSize = originalSettings.worldSize * (selectionBoundsRI.width * selectionBoundsRI.height) / (originalSettings.generatedWidth * (double) originalSettings.generatedHeight);
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);
		boolean redistributeIconsAndRivers = oneXWorldSize < worldSize;
		assertTrue(redistributeIconsAndRivers, "Selection should be too small for 'Match source detail', forcing redistribution");

		long seed = 1160170610L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, redistributeIconsAndRivers);

		// Draw the (fresh) sub-map. Passing mapParts lets us inspect the icon draw tasks the draw actually produced.
		nortantis.editor.MapParts mapParts = new nortantis.editor.MapParts();
		new MapCreator().createMap(subMapSettings, null, mapParts);

		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapCoastlineCitiesAreNotDroppedByWaterCheck");
		}

		List<IconDrawTask> drawTasks = mapParts.iconDrawer.getTasksInDrawBoundsSortedAndScaled(null);

		// The two in-bounds cities, at their sub-map RI positions (verified from the transferred free icons).
		assertCityIsDrawn(drawTasks, subMapSettings, "town with castle", new Point(1287.34, 1092.15));
		assertCityIsDrawn(drawTasks, subMapSettings, "town on two hills", new Point(1874.58, 1409.64));
	}

	/**
	 * Asserts that some icon draw task covers {@code cityLocationRI} (the city's resolution-invariant position), i.e. the city icon was
	 * actually queued to draw rather than skipped. Skipped icons stay in {@code edits.freeIcons} but produce no draw task, so this checks
	 * the draw tasks, not edits membership. The cities sit away from the redistributed terrain icons, so a task covering this point is the
	 * city.
	 */
	private static void assertCityIsDrawn(List<IconDrawTask> drawTasks, MapSettings subMapSettings, String cityName, Point cityLocationRI)
	{
		double pixelX = cityLocationRI.x * subMapSettings.resolution;
		double pixelY = cityLocationRI.y * subMapSettings.resolution;
		boolean drawn = false;
		for (IconDrawTask task : drawTasks)
		{
			if (task.createBounds().contains(pixelX, pixelY))
			{
				drawn = true;
				break;
			}
		}
		assertTrue(drawn, "City '" + cityName + "' near sub-map RI " + cityLocationRI + " should be drawn, but no icon draw task covers it (it was skipped)");
	}

	/**
	 * Returns true if the new-graph corner closest to the given river-path endpoint (in RI coordinates) is adjacent to the ocean — i.e. it
	 * is a coast or ocean corner, or it touches a water center. Sub-map rivers are freehand polylines, so a river that reaches the sea ends
	 * at a shoreline corner; an interior confluence/junction endpoint sits inland and does not match.
	 */
	private boolean riverEndpointTouchesCoastOrOcean(Point endpointRI, WorldGraph newGraph, double resolution)
	{
		Point pixel = new Point(endpointRI.x * resolution, endpointRI.y * resolution);
		nortantis.graph.voronoi.Corner corner = newGraph.findClosestCorner(pixel);
		if (corner == null)
			return false;
		if (corner.isCoast || corner.isOcean || corner.isWater)
			return true;
		return corner.touches.stream().anyMatch(center -> center.isWater);
	}

	private String saveFailedMap(MapSettings subMapSettings, String testName) throws Exception
	{
		File failedMapsDir = Paths.get("unit test files", failedMapsFolderName).toFile();
		failedMapsDir.mkdirs();
		String failedMapPath = Paths.get("unit test files", failedMapsFolderName, testName + ".png").toString();
		Image map = new MapCreator().createMap(subMapSettings, null, null);
		ImageHelper.getInstance().write(map, failedMapPath);
		return failedMapPath;
	}

	/**
	 * Asserts that all rivers form a single connected component, i.e. every river shares at least one path point with some other river.
	 * Uses union-find on path-point sets to detect disconnected river arms.
	 */
	private void assertRiversAreAllConnected(List<River> rivers, MapSettings subMapSettings, String testName) throws Exception
	{
		if (rivers.size() <= 1)
			return;

		int[] parent = new int[rivers.size()];
		for (int i = 0; i < parent.length; i++)
			parent[i] = i;

		for (int i = 0; i < rivers.size(); i++)
		{
			Set<Point> pointsI = new HashSet<>(nortantis.PathOperations.toLocationList(rivers.get(i).nodes));
			for (int j = i + 1; j < rivers.size(); j++)
			{
				Set<Point> pointsJ = new HashSet<>(nortantis.PathOperations.toLocationList(rivers.get(j).nodes));
				Set<Point> intersection = new HashSet<>(pointsI);
				intersection.retainAll(pointsJ);
				if (!intersection.isEmpty())
				{
					int rootI = i, rootJ = j;
					while (parent[rootI] != rootI)
						rootI = parent[rootI];
					while (parent[rootJ] != rootJ)
						rootJ = parent[rootJ];
					if (rootI != rootJ)
						parent[rootI] = rootJ;
				}
			}
		}

		Set<Integer> roots = new HashSet<>();
		for (int i = 0; i < parent.length; i++)
		{
			int root = i;
			while (parent[root] != root)
				root = parent[root];
			roots.add(root);
		}

		if (roots.size() > 1)
		{
			String failedMapPath = saveFailedMap(subMapSettings, testName);
			fail("Rivers are not all connected: found " + roots.size() + " disconnected components among " + rivers.size() + " rivers.\nFailed map written to: " + failedMapPath);
		}
	}

	/**
	 * Asserts that no river path contains a repeated point (which would indicate a loop in the routed path).
	 */
	private void assertRiversHaveNoLoops(List<River> rivers, MapSettings subMapSettings, String testName) throws Exception
	{
		for (int ri = 0; ri < rivers.size(); ri++)
		{
			River river = rivers.get(ri);
			Set<Point> seen = new HashSet<>();
			for (nortantis.editor.RiverPathNode node : river.nodes)
			{
				Point p = node.getLoc();
				if (!seen.add(p))
				{
					String failedMapPath = saveFailedMap(subMapSettings, testName);
					fail("River " + ri + " has a loop: path point " + p + " appears more than once.\nFailed map written to: " + failedMapPath);
				}
			}
		}
	}

	/**
	 * Asserts that the combined river network (all rivers together, sharing path points where they meet) contains no cycle. Sub-map rivers
	 * are stored as separate {@link River} polylines, and {@link WorldGraph#findRivers} splits a network at junctions, so a loop can span two
	 * {@link River} objects — two rivers that share both endpoints via different paths form a lens that no single-river check detects. This
	 * builds one undirected graph from every river's segments and uses union-find: a segment whose endpoints are already connected (by a
	 * distinct earlier segment) closes a cycle. A legitimate confluence (rivers sharing a single junction point) forms a tree, not a cycle,
	 * so it is not flagged; a duplicated segment (the same endpoint pair twice) is a separate defect covered by
	 * {@link #assertNoDuplicateRiverSegments} and is skipped here rather than counted as a cycle.
	 */
	private void assertCombinedRiverNetworkHasNoLoop(List<River> rivers, MapSettings subMapSettings, String testName) throws Exception
	{
		Map<Point, Integer> pointIds = new HashMap<>();
		List<Integer> parent = new ArrayList<>();
		Set<OrderlessPair<Point>> seenSegments = new HashSet<>();
		for (River river : rivers)
		{
			List<Point> path = nortantis.PathOperations.toLocationList(river.nodes);
			for (int i = 0; i + 1 < path.size(); i++)
			{
				Point a = path.get(i);
				Point b = path.get(i + 1);
				if (a.equals(b))
					continue;
				if (!seenSegments.add(new OrderlessPair<>(a, b)))
					continue; // Duplicate segment: a separate defect, not a cycle.
				int rootA = unionFindRoot(parent, pointId(pointIds, parent, a));
				int rootB = unionFindRoot(parent, pointId(pointIds, parent, b));
				if (rootA == rootB)
				{
					String failedMapPath = saveFailedMap(subMapSettings, testName);
					fail("Rivers form a loop: segment between " + a + " and " + b + " closes a cycle in the combined river network.\nFailed map written to: " + failedMapPath);
				}
				parent.set(rootA, rootB);
			}
		}
	}

	private static int pointId(Map<Point, Integer> pointIds, List<Integer> parent, Point p)
	{
		Integer id = pointIds.get(p);
		if (id == null)
		{
			id = parent.size();
			pointIds.put(p, id);
			parent.add(id);
		}
		return id;
	}

	private static int unionFindRoot(List<Integer> parent, int i)
	{
		while (parent.get(i) != i)
		{
			parent.set(i, parent.get(parent.get(i)));
			i = parent.get(i);
		}
		return i;
	}

	/**
	 * Asserts that no river's polyline crosses itself: no two non-adjacent segments of the same river geometrically intersect. This catches
	 * a loop drawn as a self-crossing even when it uses no repeated path point (two distinct corners whose segments cross).
	 */
	private void assertRiversHaveNoSelfCrossings(List<River> rivers, MapSettings subMapSettings, String testName) throws Exception
	{
		for (int ri = 0; ri < rivers.size(); ri++)
		{
			List<Point> path = nortantis.PathOperations.toLocationList(rivers.get(ri).nodes);
			for (int i = 0; i + 1 < path.size(); i++)
			{
				for (int j = i + 2; j + 1 < path.size(); j++)
				{
					// Skip segments that share an endpoint (adjacent segments, or the loop-closing pair for a legitimately closed path).
					if (i == 0 && j + 1 == path.size() - 1 && path.get(0).equals(path.get(path.size() - 1)))
						continue;
					if (segmentsProperlyIntersect(path.get(i), path.get(i + 1), path.get(j), path.get(j + 1)))
					{
						String failedMapPath = saveFailedMap(subMapSettings, testName);
						fail("River " + ri + " crosses itself: segment " + i + " (" + path.get(i) + "->" + path.get(i + 1) + ") intersects segment " + j + " ("
								+ path.get(j) + "->" + path.get(j + 1) + ").\nFailed map written to: " + failedMapPath);
					}
				}
			}
		}
	}

	/**
	 * Returns true if segments a0-a1 and b0-b1 cross at an interior point (proper intersection), ignoring mere shared endpoints and
	 * collinear touching.
	 */
	private static boolean segmentsProperlyIntersect(Point a0, Point a1, Point b0, Point b1)
	{
		double d1 = cross(b0, b1, a0);
		double d2 = cross(b0, b1, a1);
		double d3 = cross(a0, a1, b0);
		double d4 = cross(a0, a1, b1);
		return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
	}

	private static double cross(Point origin, Point end, Point p)
	{
		return (end.x - origin.x) * (p.y - origin.y) - (end.y - origin.y) * (p.x - origin.x);
	}

	/**
	 * Asserts that every place a source river crosses the selection boundary produces a sub-map river endpoint that actually reaches the
	 * corresponding edge of the sub-map. A source river segment that has one node inside the selection and one outside crosses the boundary;
	 * that crossing maps (via the same RI→sub-map transform SubMapCreator uses) onto an edge of the sub-map, and the re-routed river should
	 * terminate on that edge. The bug this guards against is a re-routed river that stops one or more polygons short of the map edge.
	 */
	private void assertRedistributedRiverReachesMapEdge(MapSettings originalSettings, Rectangle selectionBoundsRI, MapSettings subMapSettings, String testName)
			throws Exception
	{
		WorldGraph newGraph = MapCreator.createGraphForUnitTests(subMapSettings);
		double meanWidthRI = newGraph.getMeanCenterWidthBetweenNeighbors() / subMapSettings.resolution;

		// Collect every point where a source river crosses the selection boundary, transformed into sub-map RI space (where it lies on a map
		// edge). Deduplicate near-identical crossings so multiple source segments crossing at the same spot count once.
		List<Point> expectedEdgePoints = new ArrayList<>();
		for (River sourceRiver : originalSettings.edits.rivers)
		{
			List<Point> path = nortantis.PathOperations.toLocationList(sourceRiver.nodes);
			for (int i = 0; i + 1 < path.size(); i++)
			{
				Point a = path.get(i);
				Point b = path.get(i + 1);
				boolean aIn = selectionBoundsRI.contains(a.x, a.y);
				boolean bIn = selectionBoundsRI.contains(b.x, b.y);
				if (aIn == bIn)
					continue;
				Point outside = aIn ? b : a;
				// The crossing maps onto an edge of the sub-map. Use the outside node clamped to the selection to locate the edge it exits.
				double clampedX = Math.max(selectionBoundsRI.x, Math.min(selectionBoundsRI.x + selectionBoundsRI.width, outside.x));
				double clampedY = Math.max(selectionBoundsRI.y, Math.min(selectionBoundsRI.y + selectionBoundsRI.height, outside.y));
				Point edgePoint = new Point((clampedX - selectionBoundsRI.x) / selectionBoundsRI.width * subMapSettings.generatedWidth,
						(clampedY - selectionBoundsRI.y) / selectionBoundsRI.height * subMapSettings.generatedHeight);
				boolean duplicate = false;
				for (Point existing : expectedEdgePoints)
				{
					if (existing.distanceTo(edgePoint) < meanWidthRI)
					{
						duplicate = true;
						break;
					}
				}
				if (!duplicate)
					expectedEdgePoints.add(edgePoint);
			}
		}

		assertTrue(expectedEdgePoints.size() > 0, "Expected the selection to contain at least one source river crossing its boundary");

		// Sub-map river endpoints.
		List<Point> subMapEndpoints = new ArrayList<>();
		for (River river : subMapSettings.edits.rivers)
		{
			subMapEndpoints.add(river.nodes.get(0).getLoc());
			subMapEndpoints.add(river.nodes.get(river.nodes.size() - 1).getLoc());
		}

		// A river that reaches the map edge ends on a border corner, which sits exactly on the edge (distance 0). One that stops short ends at
		// an interior corner a fraction of a polygon in. A tenth of a mean polygon width cleanly separates the two (observed short-falls are
		// 0.16 polygon widths or more).
		double edgeTolerance = meanWidthRI * 0.1;
		for (Point expected : expectedEdgePoints)
		{
			Point nearestEnd = null;
			double bestDistance = Double.MAX_VALUE;
			for (Point endpoint : subMapEndpoints)
			{
				double distance = endpoint.distanceTo(expected);
				if (distance < bestDistance)
				{
					bestDistance = distance;
					nearestEnd = endpoint;
				}
			}
			// The nearest sub-map river endpoint must reach the map edge the crossing maps onto.
			double distanceToEdge = nearestEnd == null ? Double.MAX_VALUE
					: Math.min(Math.min(nearestEnd.x, subMapSettings.generatedWidth - nearestEnd.x), Math.min(nearestEnd.y, subMapSettings.generatedHeight - nearestEnd.y));
			if (nearestEnd == null || distanceToEdge > edgeTolerance)
			{
				String failedMapPath = saveFailedMap(subMapSettings, testName);
				fail("A source river crossing the selection boundary near sub-map edge point " + expected + " did not reach the sub-map edge: nearest river endpoint " + nearestEnd
						+ " is " + distanceToEdge + " from the nearest edge (tolerance " + edgeTolerance + ").\nFailed map written to: " + failedMapPath);
			}
		}
	}

	/**
	 * Asserts that no river segment is a freehand jump — i.e. every segment follows a Voronoi edge of the new graph
	 * ({@code edgeIndexToNext != EDGE_INDEX_NONE}). A freehand jump is the router's fallback when it cannot route two waypoints along graph
	 * edges, and renders as a straight line cutting across polygons.
	 */
	private void assertRiversHaveNoFreehandJumps(List<River> rivers, MapSettings subMapSettings, String testName) throws Exception
	{
		for (int ri = 0; ri < rivers.size(); ri++)
		{
			List<nortantis.editor.RiverPathNode> nodes = rivers.get(ri).nodes;
			// The last node has no outgoing segment, so only check segments leaving nodes 0 .. size-2.
			for (int i = 0; i + 1 < nodes.size(); i++)
			{
				if (nodes.get(i).getEdgeIndexToNext() == nortantis.editor.RiverPathNode.EDGE_INDEX_NONE)
				{
					String failedMapPath = saveFailedMap(subMapSettings, testName);
					fail("River " + ri + " has a freehand jump at segment " + i + " (from " + nodes.get(i).getLoc() + " to " + nodes.get(i + 1).getLoc()
							+ "): the segment follows no Voronoi edge.\nFailed map written to: " + failedMapPath);
				}
			}
		}
	}

	/**
	 * Asserts that no two river segments across all rivers share the same pair of endpoints. A sub-map should never contain two segments
	 * with the same two control points: where river arms meet at a confluence they should share a single segment, not duplicate it. A
	 * duplicate segment is drawn as two overlapping Catmull-Rom curves, which renders as a small loop even though the node path has no
	 * topological loop. This catches the duplicated segment at the base of a T-junction.
	 */
	private void assertNoDuplicateRiverSegments(List<River> rivers, MapSettings subMapSettings, String testName) throws Exception
	{
		Map<OrderlessPair<Point>, Integer> segmentToRiver = new HashMap<>();
		for (int ri = 0; ri < rivers.size(); ri++)
		{
			List<Point> path = nortantis.PathOperations.toLocationList(rivers.get(ri).nodes);
			for (int i = 0; i + 1 < path.size(); i++)
			{
				Point a = path.get(i);
				Point b = path.get(i + 1);
				if (a.equals(b))
					continue; // A zero-length segment is a separate degeneracy, not a duplicate.
				Integer previousRiver = segmentToRiver.putIfAbsent(new OrderlessPair<>(a, b), ri);
				if (previousRiver != null)
				{
					String failedMapPath = saveFailedMap(subMapSettings, testName);
					fail("Duplicate river segment between " + a + " and " + b + " (first in river " + previousRiver + ", again in river " + ri
							+ "). Arms meeting at a confluence should share one segment, not duplicate it.\nFailed map written to: " + failedMapPath);
				}
			}
		}
	}

	/**
	 * Asserts that each river's path has no branching. Since each {@link River} is a linear polyline this is trivially true, but this check
	 * catches degenerate cases such as rivers with fewer than 2 points (which would be invisible and indicate a routing failure).
	 */
	private void assertRiversHaveNoFingers(List<River> rivers, MapSettings subMapSettings, String testName) throws Exception
	{
		for (int ri = 0; ri < rivers.size(); ri++)
		{
			River river = rivers.get(ri);
			if (river.nodes.size() < 2)
			{
				String failedMapPath = saveFailedMap(subMapSettings, testName);
				fail("River " + ri + " has fewer than 2 path points (" + river.nodes.size() + "), indicating a failed routing.\nFailed map written to: " + failedMapPath);
			}
		}
	}

	/**
	 * Verifies that when a region is extracted at higher detail (more polygons than the source section), each city label stays attached to
	 * its city icon instead of drifting away as the icon shrinks. {@link SubMapCreator} repositions city labels relative to their icon,
	 * scaling the offset by the icon's size ratio (plus a small clearance so the larger text does not overlap the icon — see transferText),
	 * so the label keeps the same distance from its icon measured in polygon-widths, the unit icons scale with.
	 * <p>
	 * The check pairs each in-selection source city label with its nearest in-selection source city icon (the same distance-based
	 * association SubMapCreator uses), keeps only labels clearly belonging to a city (within 2 polygon-widths), and asserts the transferred
	 * label sits the same distance (in polygon-widths) from its nearest sub-map city icon, within a tolerance that allows the small
	 * clearance. Without the fix the offset scales by the full position magnification, so the distance balloons by the square root of the
	 * detail factor and this test fails.
	 * </p>
	 */
	@Test
	public void subMapCityLabelsStayWithIconsAtHigherDetail() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		MapSettings originalSettings = new MapSettings(Paths.get("unit test files", "map settings", "allTypesOfEdits.nort").toString());
		originalSettings.resolution = 0.5;
		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		Rectangle selectionBoundsRI = new Rectangle(0, 0, 2048, 2048);
		int matchWorldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);
		// Higher detail than the source region (more, smaller polygons) — the case where labels drift from their icons.
		int worldSize = Math.min(SettingsGenerator.maxWorldSize, matchWorldSize * 8);
		assertTrue(worldSize > matchWorldSize, "Test setup requires higher-than-source detail to exercise the drift");

		long seed = 12345L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, true);
		WorldGraph subMapGraph = MapCreator.createGraphForUnitTests(subMapSettings);

		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapCityLabelsStayWithIconsAtHigherDetail");
		}

		double sourceMeanWidthRI = originalGraph.getMeanCenterWidthBetweenNeighbors() / originalSettings.resolution;
		double subMeanWidthRI = subMapGraph.getMeanCenterWidthBetweenNeighbors() / subMapSettings.resolution;

		int tested = 0;
		for (MapText sourceLabel : originalSettings.edits.text)
		{
			if (sourceLabel.type != TextType.City || !selectionBoundsRI.containsOrOverlaps(sourceLabel.location))
			{
				continue;
			}
			FreeIcon sourceIcon = nearestCityIcon(sourceLabel.location, originalSettings.edits, selectionBoundsRI);
			if (sourceIcon == null)
			{
				continue;
			}
			double sourceDistanceInWidths = sourceLabel.location.distanceTo(sourceIcon.locationResolutionInvariant) / sourceMeanWidthRI;
			// Skip ambiguous/manually-detached labels; test only labels clearly hugging a city icon.
			if (sourceDistanceInWidths > 2.0)
			{
				continue;
			}
			MapText subLabel = findUniqueTextByValue(subMapSettings.edits.text, sourceLabel.value);
			if (subLabel == null)
			{
				continue;
			}
			FreeIcon subIcon = nearestCityIcon(subLabel.location, subMapSettings.edits, null);
			assertNotNull(subIcon, "Sub-map should contain a city icon for '" + sourceLabel.value + "'");
			double subDistanceInWidths = subLabel.location.distanceTo(subIcon.locationResolutionInvariant) / subMeanWidthRI;
			// The label-to-icon distance, measured in polygon-widths (the unit icons scale with), is preserved aside from a small constant
			// clearance that lifts the label off the icon. Without the fix the offset scales by the full position magnification, so the
			// distance balloons by the square root of the detail factor and this assertion fails.
			assertEquals(sourceDistanceInWidths, subDistanceInWidths, 0.3,
					"City label '" + sourceLabel.value + "' should stay attached to its icon (distance in polygon-widths preserved) at higher detail");
			tested++;
		}
		assertTrue(tested >= 5, "Expected several testable city labels in the selection, but only found " + tested);
	}

	/**
	 * Returns the {@link IconType#cities} icon in {@code edits} nearest to {@code locationRI}, optionally restricted to
	 * {@code selectionOrNull}, or null if none.
	 */
	private static FreeIcon nearestCityIcon(Point locationRI, MapEdits edits, Rectangle selectionOrNull)
	{
		FreeIcon nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (FreeIcon icon : edits.freeIcons)
		{
			if (icon.type != IconType.cities)
			{
				continue;
			}
			if (selectionOrNull != null && !selectionOrNull.containsOrOverlaps(icon.locationResolutionInvariant))
			{
				continue;
			}
			double distance = locationRI.distanceTo(icon.locationResolutionInvariant);
			if (distance < nearestDistance)
			{
				nearestDistance = distance;
				nearest = icon;
			}
		}
		return nearest;
	}

	/**
	 * Returns the single MapText with the given value, or null if there is not exactly one (avoids ambiguous matches when names repeat).
	 */
	private static MapText findUniqueTextByValue(List<MapText> texts, String value)
	{
		MapText found = null;
		for (MapText text : texts)
		{
			if (value.equals(text.value))
			{
				if (found != null)
				{
					return null;
				}
				found = text;
			}
		}
		return found;
	}

	/**
	 * Verifies that when a sub-map is made of an entire map that is densely packed with cities, the cities that land on water on the
	 * sub-map's freshly generated grid are dropped from the draw and reported by {@link MapCreator#getCitiesRemovedForTouchingWater()} (so
	 * the sub-map dialog can warn the user). The sub-map regenerates the whole map on a new Voronoi grid, so its coastline differs slightly
	 * from the source; with so many cities, a large number end up over the redrawn water.
	 */
	@Test
	public void subMapOfManyCitiesMapReportsManyCitiesOnWater() throws Exception
	{
		// Set to true to force this test to write its result map to the failed sub-maps folder, even when it passes.
		boolean forceWrite = false;

		MapSettings originalSettings = new MapSettings(Paths.get("unit test files", "map settings", "manyCitiesForSubMapWaterWarning.nort").toString());
		originalSettings.resolution = 0.5;
		WorldGraph originalGraph = MapCreator.createGraphForUnitTests(originalSettings);

		// A sub-map of the entire map.
		Rectangle selectionBoundsRI = new Rectangle(0, 0, originalSettings.generatedWidth, originalSettings.generatedHeight);
		int worldSize = SubMapDialog.computeDefaultWorldSize(originalSettings, selectionBoundsRI);
		double oneXWorldSize = originalSettings.worldSize * (selectionBoundsRI.width * selectionBoundsRI.height) / (originalSettings.generatedWidth * (double) originalSettings.generatedHeight);
		boolean redistributeIconsAndRivers = oneXWorldSize < worldSize;

		long seed = 12345L;
		MapSettings subMapSettings = SubMapCreator.createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, worldSize, originalSettings.resolution, seed, redistributeIconsAndRivers);

		MapCreator mapCreator = new MapCreator();
		mapCreator.createMap(subMapSettings, null, new nortantis.editor.MapParts());
		int count = mapCreator.getCitiesRemovedForTouchingWater().size();

		if (forceWrite || forceWriteAllMaps)
		{
			saveFailedMap(subMapSettings, "subMapOfManyCitiesMapReportsManyCitiesOnWater");
		}

		// This selection and seed drop ~121 cities onto the redrawn water. Assert a high lower bound rather than the exact value so the test
		// proves "many cities are reported" and catches a regression to near-zero, without being brittle to a few borderline coastal cities
		// shifting across the water line.
		assertTrue(count >= 50, "Expected many cities (>= 50) to land on water in a full-map sub-map of a city-dense map, but got " + count);
	}

}
