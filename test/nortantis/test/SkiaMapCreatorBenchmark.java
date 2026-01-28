package nortantis.test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.WorldGraph;
import nortantis.geom.Point;
import nortantis.platform.PlatformFactory;
import nortantis.platform.skia.GPUExecutor;
import nortantis.platform.skia.GPUExecutor.RenderingMode;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Random;

/**
 * Benchmark for map creation performance using Skia (GPU rendering).
 *
 * Run with JFR profiling: ./gradlew benchmark
 *
 * The JFR recording will be saved to build/profile.jfr Open it in JDK Mission Control or IntelliJ to analyze hotspots.
 */
public class SkiaMapCreatorBenchmark
{
	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new SkiaFactory());
		Assets.disableAddedArtPacksForUnitTests();
	}

	@AfterEach
	public void cleanup()
	{
		// Reset to GPU mode after each test
		GPUExecutor.setRenderingMode(RenderingMode.DEFAULT);
	}

	@Test
	public void benchmarkMapCreationLowRes() throws Exception
	{
		MapTestUtil.runMapCreationBenchmark("Skia GPU", 0.5, 1, 3);
	}

	@Test
	public void benchmarkMapCreationHighRes() throws Exception
	{
		MapTestUtil.runMapCreationBenchmarkSingleIteration("Skia GPU", 1.5);
	}

	@Test
	public void benchmarkMapCreationHighResCPUOnly() throws Exception
	{
		GPUExecutor.setRenderingMode(RenderingMode.CPU_SHADERS);
		MapTestUtil.runMapCreationBenchmarkSingleIteration("Skia GPU", 0.75); // TODO change back to 1.5
	}

	@Test
	public void benchmarkIncrementalDrawing() throws Exception
	{
		MapTestUtil.runIncrementalDrawingBenchmark("Skia GPU", 0, 1);
	}

	@Test
	public void benchmarkIncrementalDrawingCPUOnly() throws Exception
	{
		GPUExecutor.setRenderingMode(RenderingMode.CPU_SHADERS);
		MapTestUtil.runIncrementalDrawingBenchmark("Skia CPU-only", 0, 1);
	}

	@Test
	public void benchmarkFindClosestCenter() throws Exception
	{
		System.out.println("\n=== findClosestCenter Benchmark (Skia) ===\n");

		String settingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = 1.0;

		System.out.println("Settings: " + settingsPath);
		System.out.println("Resolution: " + settings.resolution);

		// Create the graph
		System.out.println("\nCreating WorldGraph...");
		long graphStart = System.nanoTime();
		WorldGraph graph = MapCreator.createGraphForUnitTests(settings);
		long graphTime = System.nanoTime() - graphStart;
		System.out.println("Graph creation time: " + MapTestUtil.formatTime(graphTime));
		System.out.println("Graph dimensions: " + graph.getWidth() + "x" + graph.getHeight());
		System.out.println("Number of centers: " + graph.centers.size());

		// Generate random test points
		int numPoints = 100_000;
		Random rand = new Random(12345);
		Point[] testPoints = new Point[numPoints];
		for (int i = 0; i < numPoints; i++)
		{
			double x = rand.nextDouble() * graph.getWidth();
			double y = rand.nextDouble() * graph.getHeight();
			testPoints[i] = new Point(x, y);
		}
		System.out.println("Generated " + numPoints + " random test points");

		// Warmup - force lookup table construction and JIT compilation
		System.out.println("\nWarmup (1000 lookups)...");
		for (int i = 0; i < 1000; i++)
		{
			graph.findClosestCenter(testPoints[i]);
		}

		// Benchmark iterations
		int iterations = 5;
		long[] times = new long[iterations];

		System.out.println("\nRunning benchmark (" + iterations + " iterations of " + numPoints + " lookups each)...\n");

		for (int iter = 0; iter < iterations; iter++)
		{
			long start = System.nanoTime();
			for (int i = 0; i < numPoints; i++)
			{
				graph.findClosestCenter(testPoints[i]);
			}
			long elapsed = System.nanoTime() - start;
			times[iter] = elapsed;

			double perLookupNs = (double) elapsed / numPoints;
			System.out.println("  Iteration " + (iter + 1) + ": " + MapTestUtil.formatTime(elapsed) + " total, " + String.format("%.2f ns", perLookupNs) + " per lookup");
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
		long avg = total / iterations;
		double avgPerLookup = (double) avg / numPoints;

		System.out.println("\n=== Results ===");
		System.out.println("  Total lookups per iteration: " + numPoints);
		System.out.println("  Average total time: " + MapTestUtil.formatTime(avg));
		System.out.println("  Average per lookup: " + String.format("%.2f ns", avgPerLookup));
		System.out.println("  Min: " + MapTestUtil.formatTime(min));
		System.out.println("  Max: " + MapTestUtil.formatTime(max));
		System.out.println("  Throughput: " + String.format("%.2f", (numPoints * 1_000_000_000.0) / avg) + " lookups/sec");
	}
}
