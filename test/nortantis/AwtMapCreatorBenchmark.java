package nortantis;

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
 * <p>The {@code profile*} tests render two fixtures ({@code highResTest.nort} @1.25 and {@code allTypesOfEdits.nort} @0.75) and print the
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
