package nortantis;

import nortantis.editor.FreeIcon;
import nortantis.editor.MapParts;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Compares two versions of the same code by running both in one JVM, alternating between them, rather than by editing the source and
 * running the benchmark twice.
 *
 * Running twice cannot measure a change of a few percent on this machine: identical code has produced anywhere from 22.8 to 30.7 ms per
 * icon across runs, because the machine's load drifts over minutes, while iterations inside a single run agree to within half a percent.
 * Alternating the two versions seconds apart in the same process exposes both to the same conditions, and the block order below is
 * mirrored (A B B A ...) so that a load trend in one direction cancels out of the paired differences.
 *
 * Run with: ./gradlew test --tests "nortantis.PairedIncrementalBenchmark" -DrunBenchmarks=true
 */
@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")
public class PairedIncrementalBenchmark
{
	/**
	 * How many icons each timed block redraws. Enough to take a few seconds, so that a block is long enough to be worth timing but short
	 * enough that the two versions stay close together in time.
	 */
	private static final int iconsPerBlock = 150;
	private static final int pairs = 5;

	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
	}

	@Test
	public void artPackPathCache() throws Exception
	{
		compare("ImageCache art pack path cache", on -> ImageCache.useArtPackPathCache = on);
	}

	@Test
	public void reusingTheDrawTask() throws Exception
	{
		compare("Reusing the already built IconDrawTask", on -> IconDrawer.reuseDrawTask = on);
	}

	@Test
	public void derivedIconListCaches() throws Exception
	{
		compare("ImageCache getIconsInGroup and getIconGroupsAsListsForType caches", on -> ImageCache.useDerivedIconListCaches = on);
	}

	@Test
	public void artPackListCache() throws Exception
	{
		compare("Assets.listArtPacks cache", on -> nortantis.util.Assets.useArtPackListCache = on);
	}

	/**
	 * Measures {@link RiverDrawer#stampRiverCurvesOntoGraphEdges(java.util.Set)} against a map with many rivers, since the incremental
	 * version's win is in not walking every river on the map, so it should show up more on a map that has a lot of them.
	 */
	@Test
	public void incrementalRiverStamping() throws Exception
	{
		String settingsFileName = "allTypesOfEdits.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		settings.resolution = 0.5;

		MapCreator mapCreator = new MapCreator();
		MapParts mapParts = new MapParts();
		Image fullMap = mapCreator.createMap(settings, null, mapParts);
		List<Set<Integer>> strokes = createStrokes(mapParts, 6, 12);

		System.out.println("\n=== Incremental river-edge stamping vs rebuilding from every river each stroke ===");
		System.out.println(strokes.size() + " strokes per block, " + pairs + " pairs, order mirrored per pair.");

		// Warm both versions up so that neither pays for being compiled first.
		for (boolean enabled : new boolean[] { true, false, true, false })
		{
			RiverDrawer.useIncrementalRiverStamping = enabled;
			runStrokeBlock(mapCreator, settings, mapParts, fullMap, strokes);
		}

		List<Double> enabledTimes = new ArrayList<>();
		List<Double> disabledTimes = new ArrayList<>();
		for (int pair = 0; pair < pairs; pair++)
		{
			boolean enabledFirst = pair % 2 == 0;
			for (boolean enabled : new boolean[] { enabledFirst, !enabledFirst })
			{
				RiverDrawer.useIncrementalRiverStamping = enabled;
				double milliseconds = runStrokeBlock(mapCreator, settings, mapParts, fullMap, strokes);
				(enabled ? enabledTimes : disabledTimes).add(milliseconds);
				System.out.println(String.format("  pair %d  %-8s %8.1f ms", pair + 1, enabled ? "with" : "without", milliseconds));
			}
		}
		RiverDrawer.useIncrementalRiverStamping = true;

		List<Double> differences = new ArrayList<>();
		for (int i = 0; i < pairs; i++)
		{
			differences.add(disabledTimes.get(i) - enabledTimes.get(i));
		}
		System.out.println("  without minus with, per pair (ms): " + format(differences));
		System.out.println(String.format("  median with:    %8.1f ms", median(enabledTimes)));
		System.out.println(String.format("  median without: %8.1f ms", median(disabledTimes)));
		double medianDifference = median(differences);
		System.out.println(String.format("  median difference: %.1f ms (%.1f%% of the version with it)", medianDifference,
				100.0 * medianDifference / median(enabledTimes)));
		System.out.println("  Every paired difference must share a sign for this to mean anything.");

		fullMap.close();
	}

	private double runStrokeBlock(MapCreator mapCreator, MapSettings settings, MapParts mapParts, Image fullMap, List<Set<Integer>> strokes)
	{
		Image mapCopy = fullMap.deepCopy();
		long start = System.nanoTime();
		for (Set<Integer> stroke : strokes)
		{
			mapCreator.incrementalUpdateForCentersAndEdges(settings, mapParts, mapCopy, stroke, new HashSet<>(), false);
		}
		double milliseconds = (System.nanoTime() - start) / 1000000.0;
		mapCopy.close();
		return milliseconds;
	}

	/**
	 * Builds several small clusters of neighboring centers, each standing in for one dab of a brush. Mirrors
	 * IncrementalCentersBenchmark#createStrokes.
	 */
	private List<Set<Integer>> createStrokes(MapParts mapParts, int strokeCount, int centersPerStroke)
	{
		List<Set<Integer>> strokes = new ArrayList<>();
		List<Center> allCenters = mapParts.graph.centers;
		for (int i = 0; i < strokeCount; i++)
		{
			Center start = allCenters.get((int) ((i + 1) * allCenters.size() / (double) (strokeCount + 1)));
			Set<Integer> stroke = new LinkedHashSet<>();
			stroke.add(start.index);
			List<Center> frontier = new ArrayList<>(List.of(start));
			while (stroke.size() < centersPerStroke && !frontier.isEmpty())
			{
				List<Center> next = new ArrayList<>();
				for (Center center : frontier)
				{
					for (Center neighbor : center.neighbors)
					{
						if (stroke.size() < centersPerStroke && stroke.add(neighbor.index))
						{
							next.add(neighbor);
						}
					}
				}
				frontier = next;
			}
			strokes.add(stroke);
		}
		return strokes;
	}

	private void compare(String description, Consumer<Boolean> setEnabled) throws Exception
	{
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString());
		settings.resolution = 0.75;

		MapCreator mapCreator = new MapCreator();
		MapParts mapParts = new MapParts();
		Image fullMap = mapCreator.createMap(settings, null, mapParts);

		List<FreeIcon> icons = new ArrayList<>();
		for (FreeIcon icon : settings.edits.freeIcons)
		{
			icons.add(icon);
			if (icons.size() >= iconsPerBlock)
			{
				break;
			}
		}

		System.out.println("\n=== " + description + " ===");
		System.out.println("Redrawing " + icons.size() + " icons per block, " + pairs + " pairs, order mirrored per pair.");

		// Warm both versions up so that neither pays for being compiled first.
		for (boolean enabled : new boolean[] { true, false, true, false })
		{
			setEnabled.accept(enabled);
			runBlock(mapCreator, settings, mapParts, fullMap, icons);
		}

		List<Double> enabledTimes = new ArrayList<>();
		List<Double> disabledTimes = new ArrayList<>();
		for (int pair = 0; pair < pairs; pair++)
		{
			// Mirror the order every other pair so a steady drift in machine load affects both versions equally.
			boolean enabledFirst = pair % 2 == 0;
			for (boolean enabled : new boolean[] { enabledFirst, !enabledFirst })
			{
				setEnabled.accept(enabled);
				double milliseconds = runBlock(mapCreator, settings, mapParts, fullMap, icons);
				(enabled ? enabledTimes : disabledTimes).add(milliseconds);
				System.out.println(String.format("  pair %d  %-8s %8.1f ms", pair + 1, enabled ? "with" : "without", milliseconds));
			}
		}
		setEnabled.accept(true);

		List<Double> differences = new ArrayList<>();
		for (int i = 0; i < pairs; i++)
		{
			differences.add(disabledTimes.get(i) - enabledTimes.get(i));
		}
		System.out.println("  without minus with, per pair (ms): " + format(differences));
		System.out.println(String.format("  median with:    %8.1f ms", median(enabledTimes)));
		System.out.println(String.format("  median without: %8.1f ms", median(disabledTimes)));
		double medianDifference = median(differences);
		System.out.println(String.format("  median difference: %.1f ms (%.1f%% of the version with it)", medianDifference,
				100.0 * medianDifference / median(enabledTimes)));
		System.out.println("  Every paired difference must share a sign for this to mean anything.");

		fullMap.close();
	}

	private double runBlock(MapCreator mapCreator, MapSettings settings, MapParts mapParts, Image fullMap, List<FreeIcon> icons)
	{
		Image mapCopy = fullMap.deepCopy();
		long start = System.nanoTime();
		for (FreeIcon icon : icons)
		{
			mapCreator.incrementalUpdateIcons(settings, mapParts, mapCopy, Arrays.asList(icon));
		}
		double milliseconds = (System.nanoTime() - start) / 1000000.0;
		mapCopy.close();
		return milliseconds;
	}

	private static String format(List<Double> values)
	{
		StringBuilder builder = new StringBuilder();
		for (double value : values)
		{
			builder.append(String.format("%.1f  ", value));
		}
		return builder.toString().trim();
	}

	private static double median(List<Double> values)
	{
		List<Double> sorted = new ArrayList<>(values);
		sorted.sort(Double::compare);
		return sorted.get(sorted.size() / 2);
	}
}
