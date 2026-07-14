package nortantis;

import nortantis.editor.MapParts;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Paths;

/**
 * Benchmark for map creation performance using AWT (CPU rendering).
 *
 * Benchmarks are skipped during normal test runs. Run with:
 * ./gradlew test --tests "nortantis.AwtMapCreatorBenchmark" -DrunBenchmarks=true
 * (or via the dedicated JFR-profiling task: ./gradlew benchmark)
 *
 * <p>
 * The {@code profile*} tests render two fixtures ({@code highResTest.nort} @1.25 and {@code allTypesOfEdits.nort} @0.75) and print the
 * per-iteration plus min and median {@code createMap} wall-clock times, so overall render cost can be compared across runs.
 */
@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")
public class AwtMapCreatorBenchmark
{
	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
		Assets.disableAddedArtPacksForUnitTests();
	}

	@Test
	public void benchmarkMapCreationLowRes() throws Exception
	{
		MapTestUtil.runMapCreationBenchmark("AWT CPU", 0.5, 1, 3);
	}

	@Test
	public void benchmarkMapCreationHighRes() throws Exception
	{
		MapTestUtil.runMapCreationBenchmarkSingleIteration("AWT CPU", 1.5);
	}

	@Test
	public void benchmarkIncrementalDrawing() throws Exception
	{
		MapTestUtil.runIncrementalDrawingBenchmark("AWT CPU", 0, 1);
	}

	/** Per-operation profile of highResTest.nort @ 1.25. */
	@Test
	public void profileHighResTest() throws Exception
	{
		profile("highResTest.nort", 1.25);
	}

	/** Per-operation profile of allTypesOfEdits.nort @ 0.75. */
	@Test
	public void profileAllTypesOfEdits() throws Exception
	{
		profile("allTypesOfEdits.nort", 0.75);
	}

	/**
	 * Isolated micro-benchmark of {@link nortantis.WorldGraph#findClosestCenter(Point)} - the per-pixel point-location used during
	 * rendering. Builds one graph, then times looking up every pixel of the map (matching real rendering usage, which exercises both the
	 * O(1) fast path and the rarer fallback near coasts/large cells). The query Point is reused to keep allocation out of the measurement.
	 */
	@Test
	public void profileFindClosestCenter() throws Exception
	{
		String settingsPath = Paths.get("unit test files", "map settings", "allTypesOfEdits.nort").toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = 1.0;

		MapParts mapParts = new MapParts();
		new MapCreator().createMap(settings, null, mapParts);
		WorldGraph graph = mapParts.graph;

		int width = (int) graph.getWidth();
		int height = (int) graph.getHeight();
		long numLookups = (long) width * height;
		System.out.println("\n=== findClosestCenter profile: allTypesOfEdits @1.0, map " + width + "x" + height + " (" + numLookups + " lookups/pass) ===\n");

		Point query = new Point(0, 0);

		for (int warmup = 0; warmup < 2; warmup++)
		{
			runLookupPass(graph, query, width, height);
		}

		long[] samples = new long[TIMED_ITERATIONS];
		for (int i = 0; i < TIMED_ITERATIONS; i++)
		{
			long start = System.nanoTime();
			long checksum = runLookupPass(graph, query, width, height);
			samples[i] = System.nanoTime() - start;
			// Consume the checksum so the JIT cannot eliminate the lookups.
			if (checksum == Long.MIN_VALUE)
			{
				System.out.println("(unreachable)");
			}
			System.out.println("  iteration " + (i + 1) + ": " + MapTestUtil.formatTime(samples[i]) + "  (" + (samples[i] / (double) numLookups) + " ns/lookup)");
		}

		long[] sorted = samples.clone();
		java.util.Arrays.sort(sorted);
		System.out.println("MIN    per-lookup: " + (sorted[0] / (double) numLookups) + " ns  (" + MapTestUtil.formatTime(sorted[0]) + " total)");
		System.out.println("MEDIAN per-lookup: " + (sorted[sorted.length / 2] / (double) numLookups) + " ns  (" + MapTestUtil.formatTime(sorted[sorted.length / 2]) + " total)");
	}

	private static long runLookupPass(WorldGraph graph, Point query, int width, int height)
	{
		long checksum = 0;
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				query.x = x;
				query.y = y;
				Center center = graph.findClosestCenter(query);
				checksum += center.index;
			}
		}
		return checksum;
	}

	/** Warmup renders (not timed) before the timed loop, so JIT is warm and one-time asset loading is excluded. */
	private static final int WARMUP_ITERATIONS = 2;
	/** Timed renders; we report the minimum (most stable, least affected by GC/scheduling jitter) plus the median and all samples. */
	private static final int TIMED_ITERATIONS = 5;

	/**
	 * Renders the given fixture {@link #WARMUP_ITERATIONS} times (discarded) then {@link #TIMED_ITERATIONS} times (timed), in a single warm
	 * JVM. Prints every sample plus the min and median wall-clock (min-of-N in one warm JVM removes most of the run-to-run variance a
	 * single measurement has). Run via the plain {@code test} task (NOT {@code benchmark}) to avoid JFR overhead.
	 */
	private void profile(String settingsFileName, double resolution) throws Exception
	{
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();

		System.out.println("\n=== AWT CPU Profile: " + settingsFileName + " @ resolution " + resolution + " ===\n");

		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = resolution;

		for (int i = 0; i < WARMUP_ITERATIONS; i++)
		{
			Image warmupMap = new MapCreator().createMap(settings, null, null);
			if (i == 0)
			{
				System.out.println("Map size: " + warmupMap.getWidth() + "x" + warmupMap.getHeight());
			}
			warmupMap.close();
		}

		long[] samples = new long[TIMED_ITERATIONS];
		for (int i = 0; i < TIMED_ITERATIONS; i++)
		{
			long start = System.nanoTime();
			Image map = new MapCreator().createMap(settings, null, null);
			samples[i] = System.nanoTime() - start;
			map.close();
			System.out.println("  timed iteration " + (i + 1) + ": " + MapTestUtil.formatTime(samples[i]));
		}

		long[] sorted = samples.clone();
		java.util.Arrays.sort(sorted);
		System.out.println("MIN  createMap time: " + MapTestUtil.formatTime(sorted[0]));
		System.out.println("MEDIAN createMap time: " + MapTestUtil.formatTime(sorted[sorted.length / 2]));
	}
}
