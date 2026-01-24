package nortantis.test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

/**
 * Benchmark for map creation performance.
 *
 * Run with JFR profiling: ./gradlew benchmark
 *
 * The JFR recording will be saved to build/profile.jfr Open it in JDK Mission Control or IntelliJ to analyze hotspots.
 */
public class MapCreatorBenchmark
{
	private static final int WARMUP_ITERATIONS = 1;
	private static final int BENCHMARK_ITERATIONS = 3;

	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new SkiaFactory());
		Assets.disableAddedArtPacksForUnitTests();
	}

	@Test
	public void benchmarkMapCreation() throws Exception
	{
		System.out.println("\n=== Map Creation Benchmark (Skia GPU) ===\n");

		String settingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = 0.5;

		System.out.println("Settings: " + settingsPath);
		System.out.println("Resolution: " + settings.resolution);

		// Warmup
		System.out.println("\nWarmup (" + WARMUP_ITERATIONS + " iterations)...");
		for (int i = 0; i < WARMUP_ITERATIONS; i++)
		{
			MapCreator mapCreator = new MapCreator();
			Image map = mapCreator.createMap(settings, null, null);
			if (i == 0)
			{
				System.out.println("Map size: " + map.getWidth() + "x" + map.getHeight());
			}
			map.close();
		}

		// Benchmark
		System.out.println("\nRunning benchmark (" + BENCHMARK_ITERATIONS + " iterations)...\n");

		long[] times = new long[BENCHMARK_ITERATIONS];
		for (int i = 0; i < BENCHMARK_ITERATIONS; i++)
		{
			MapCreator mapCreator = new MapCreator();

			long start = System.nanoTime();
			Image map = mapCreator.createMap(settings, null, null);
			long elapsed = System.nanoTime() - start;

			times[i] = elapsed;
			System.out.println("  Iteration " + (i + 1) + ": " + formatTime(elapsed));

			map.close();
		}

		// Statistics
		long total = 0;
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long t : times)
		{
			total += t;
			min = Math.min(min, t);
			max = Math.max(max, t);
		}
		long avg = total / BENCHMARK_ITERATIONS;

		System.out.println("\n=== Results ===");
		System.out.println("  Average: " + formatTime(avg));
		System.out.println("  Min:     " + formatTime(min));
		System.out.println("  Max:     " + formatTime(max));
	}

	@Test
	public void benchmarkMapCreationHighRes() throws Exception
	{
		System.out.println("\n=== Map Creation Benchmark - High Resolution (Skia GPU) ===\n");

		String settingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = 1.0;

		System.out.println("Settings: " + settingsPath);
		System.out.println("Resolution: " + settings.resolution);

		// Single run for high-res (it's slow)
		System.out.println("\nRunning single iteration...\n");

		MapCreator mapCreator = new MapCreator();

		long start = System.nanoTime();
		Image map = mapCreator.createMap(settings, null, null);
		long elapsed = System.nanoTime() - start;

		System.out.println("Map size: " + map.getWidth() + "x" + map.getHeight());
		System.out.println("Time: " + formatTime(elapsed));

		map.close();
	}

	private String formatTime(long nanos)
	{
		if (nanos < 1_000_000)
		{
			return String.format("%.2f µs", nanos / 1000.0);
		}
		else if (nanos < 1_000_000_000)
		{
			return String.format("%.2f ms", nanos / 1_000_000.0);
		}
		else
		{
			return String.format("%.2f s", nanos / 1_000_000_000.0);
		}
	}
}
