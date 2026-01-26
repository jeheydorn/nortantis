package nortantis.test;

import nortantis.platform.PlatformFactory;
import nortantis.platform.skia.GPUExecutor;
import nortantis.platform.skia.GPUExecutor.RenderingMode;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
	public void benchmarkIncrementalDrawing() throws Exception
	{
		MapTestUtil.runIncrementalDrawingBenchmark("Skia GPU", 1.0, 0, 1);
	}

	@Test
	public void benchmarkIncrementalDrawingCPUOnly() throws Exception
	{
		GPUExecutor.setRenderingMode(RenderingMode.CPU);
		MapTestUtil.runIncrementalDrawingBenchmark("Skia CPU-only", 1.0, 0, 1);
	}
}
