package nortantis;

import nortantis.editor.MapParts;
import nortantis.geom.IntRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Times the incremental redraw that happens while drawing land with a brush, which is
 * {@link MapCreator#incrementalUpdateForCentersAndEdges}, with the coastline and ocean shading turned on and then off.
 *
 * The icon and text benchmarks in {@link AwtMapCreatorBenchmark} do not cover this path, and it is the one that governs how a brush stroke
 * feels.
 *
 * Run with: ./gradlew test --tests "nortantis.IncrementalCentersBenchmark" -DrunBenchmarks=true
 * Point it at a different map with -DincrementalBenchmarkSettings=some/path.nort
 */
@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")
public class IncrementalCentersBenchmark
{
	private static final int warmupIterations = 3;
	private static final int timedIterations = 12;

	/**
	 * Roughly how many centers one dab of a brush covers.
	 */
	private static final int centersPerStroke = 12;

	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
	}

	@Test
	public void compareWithAndWithoutShading() throws Exception
	{
		String settingsPath = System.getProperty("incrementalBenchmarkSettings",
				Paths.get("unit test files", "map settings", "allTypesOfEdits.nort").toString());
		System.out.println("\n=== Incremental centers and edges: shading on vs off ===");
		System.out.println("Settings: " + settingsPath);

		MapSettings settings = new MapSettings(settingsPath);
		System.out.println("Resolution: " + settings.resolution + ", coastShadingLevel " + settings.coastShadingLevel + ", oceanShadingLevel "
				+ settings.oceanShadingLevel + ", oceanWavesLevel " + settings.oceanWavesLevel + ", oceanWavesType " + settings.oceanWavesType);

		// Coast shading is left as configured in both runs so that the effects padding, which it sets, stays the same. Only the
		// ocean shading changes, which isolates the work it does from the cost of redrawing a larger area. Set
		// incrementalBenchmarkOceanShading to on or off to run only one of them, which is what profiling one of them needs.
		String which = System.getProperty("incrementalBenchmarkOceanShading", "both");
		if (!which.equals("off"))
		{
			measure(settings, "ocean shading ON,  coast shading " + settings.coastShadingLevel, settings.coastShadingLevel, settings.oceanShadingLevel);
		}
		if (!which.equals("on"))
		{
			measure(settings, "ocean shading OFF, coast shading " + settings.coastShadingLevel, settings.coastShadingLevel, 0);
		}
	}

	/**
	 * Sweeps the coast shading level, which is what sets the effects padding, to show how the cost of a stroke scales with how far the
	 * redrawn area is padded. Ocean shading is held at zero so that only one setting drives the padding.
	 */
	@Test
	public void measureCostAgainstShadingWidth() throws Exception
	{
		String settingsPath = System.getProperty("incrementalBenchmarkSettings",
				Paths.get("unit test files", "map settings", "allTypesOfEdits.nort").toString());
		System.out.println("\n=== Cost of a stroke against coast shading width ===");
		System.out.println("Settings: " + settingsPath);
		MapSettings settings = new MapSettings(settingsPath);

		for (int coastShadingLevel : new int[] { 0, 10, 20, 30, 49, 70, 100 })
		{
			measure(settings, "coastShadingLevel " + coastShadingLevel, coastShadingLevel, 0);
		}
	}

	private void measure(MapSettings originalSettings, String label, int coastShadingLevel, int oceanShadingLevel) throws Exception
	{
		MapSettings settings = originalSettings.deepCopy();
		settings.coastShadingLevel = coastShadingLevel;
		settings.oceanShadingLevel = oceanShadingLevel;

		MapCreator fullMapCreator = new MapCreator();
		MapParts mapParts = new MapParts();
		try (Image fullMap = fullMapCreator.createMap(settings, null, mapParts))
		{
			List<Set<Integer>> strokes = createStrokes(mapParts);

			for (int i = 0; i < warmupIterations; i++)
			{
				runOneStroke(settings, mapParts, fullMap, strokes.get(i % strokes.size()));
			}

			long[] samples = new long[timedIterations];
			IntRectangle lastBounds = null;
			long totalArea = 0;
			long collectionsBefore = totalGarbageCollections();
			long collectionMillisBefore = totalGarbageCollectionMillis();
			long allocatedBytesBefore = allocatedBytes();
			for (int i = 0; i < timedIterations; i++)
			{
				Set<Integer> stroke = strokes.get(i % strokes.size());
				long start = System.nanoTime();
				lastBounds = runOneStroke(settings, mapParts, fullMap, stroke);
				samples[i] = System.nanoTime() - start;
				totalArea += (long) lastBounds.width * lastBounds.height;
			}

			long[] sorted = samples.clone();
			java.util.Arrays.sort(sorted);
			long median = sorted[sorted.length / 2];
			long averageArea = totalArea / timedIterations;

			System.out.println("\n  " + label);
			System.out.println("    effects padding:    " + (long) MapCreator.calcEffectsPadding(settings) + " px on each side");
			System.out.println("    redrawn snippet:    " + lastBounds.width + "x" + lastBounds.height + " (average area " + averageArea
					+ " px, " + String.format("%.2f", averageArea / 1_000_000.0) + " megapixels)");
			System.out.println("    median per stroke:  " + String.format("%.1f", median / 1_000_000.0) + " ms");
			System.out.println("    min per stroke:     " + String.format("%.1f", sorted[0] / 1_000_000.0) + " ms");
			System.out.println("    per megapixel:      " + String.format("%.1f", (median / 1_000_000.0) / (averageArea / 1_000_000.0)) + " ms");
			StringBuilder allSamples = new StringBuilder();
			for (long sample : samples)
			{
				allSamples.append(String.format("%.0f ", sample / 1_000_000.0));
			}
			System.out.println("    every sample (ms):  " + allSamples.toString().trim());
			System.out.println("    garbage collection: " + (totalGarbageCollections() - collectionsBefore) + " collections, "
					+ (totalGarbageCollectionMillis() - collectionMillisBefore) + " ms total, "
					+ String.format("%.1f", (totalGarbageCollectionMillis() - collectionMillisBefore) / (double) timedIterations)
					+ " ms per stroke");
			System.out.println("    allocated:          "
					+ String.format("%.1f", (allocatedBytes() - allocatedBytesBefore) / 1024.0 / 1024.0 / timedIterations) + " MB per stroke");
		}
	}

	private IntRectangle runOneStroke(MapSettings settings, MapParts mapParts, Image fullMap, Set<Integer> centerIds)
	{
		MapCreator mapCreator = new MapCreator();
		return mapCreator.incrementalUpdateForCentersAndEdges(settings, mapParts, fullMap, centerIds, new HashSet<>(), false);
	}

	/**
	 * Builds several small clusters of neighboring centers, each standing in for one dab of a brush. Several are used in rotation so that
	 * the same part of the map is not redrawn every time.
	 */
	private List<Set<Integer>> createStrokes(MapParts mapParts)
	{
		List<Set<Integer>> strokes = new ArrayList<>();
		List<Center> allCenters = mapParts.graph.centers;
		int strokeCount = 6;
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

	private static long totalGarbageCollections()
	{
		long total = 0;
		for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())
		{
			total += Math.max(0, bean.getCollectionCount());
		}
		return total;
	}

	private static long totalGarbageCollectionMillis()
	{
		long total = 0;
		for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())
		{
			total += Math.max(0, bean.getCollectionTime());
		}
		return total;
	}

	/**
	 * Total bytes allocated by all threads, which shows how much garbage a stroke makes even when a collection does not happen to land
	 * inside the measurement.
	 */
	private static long allocatedBytes()
	{
		java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
		if (!(bean instanceof com.sun.management.ThreadMXBean))
		{
			return 0;
		}
		com.sun.management.ThreadMXBean sunBean = (com.sun.management.ThreadMXBean) bean;
		long total = 0;
		for (long id : bean.getAllThreadIds())
		{
			long allocated = sunBean.getThreadAllocatedBytes(id);
			if (allocated > 0)
			{
				total += allocated;
			}
		}
		return total;
	}
}
