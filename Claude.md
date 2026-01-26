# Nortantis

Fantasy map generator and editor that uses tectonic plate simulation to create islands and continents with terrain features (trees, rivers, mountains) rendered with an old-fashioned, hand-drawn appearance.

## Tech Stack

- **Language:** Java 21+
- **Build System:** Gradle (use `gradlew` wrapper)
- **UI Framework:** Swing with FlatLaf look-and-feel
- **Graphics:** Dual rendering backends:
  - AWT (CPU-based, default)
  - Skia via Skiko (GPU-accelerated)
- **Testing:** JUnit 5

## Build and Run

```bash
# Run the GUI application
./gradlew run

# Build JAR
./gradlew jar

# Run tests
./gradlew test

# Run benchmark with JFR profiling
./gradlew benchmark

# Format code
./gradlew spotlessApply
```

## Project Structure

```
src/nortantis/
├── MapCreator.java          # Core map generation orchestrator
├── MapSettings.java         # Serializable map configuration
├── WorldGraph.java          # Voronoi graph with elevation/biome data
├── GraphCreator.java        # Constructs WorldGraph from Voronoi diagrams
├── platform/                # Graphics abstraction layer
│   ├── PlatformFactory.java # Factory for graphics backends
│   ├── Image.java           # Abstract image
│   ├── Painter.java         # Abstract drawing interface
│   ├── awt/                  # CPU rendering (AWT)
│   └── skia/                 # GPU rendering (Skiko)
├── swing/                   # UI components
│   ├── MainWindow.java      # Application entry point
│   ├── MapEditingPanel.java # Map canvas/viewport
│   ├── ThemePanel.java      # Theme customization
│   └── ToolsPanel.java      # Editing tools
├── editor/                  # Map editing data structures
├── graph/voronoi/           # Voronoi diagram structures
├── geom/                    # Geometry utilities
└── util/                    # General utilities
```

## Key Entry Points

- **GUI Entry:** `nortantis.swing.MainWindow`
- **Map Generation:** `nortantis.MapCreator.createMap()`
- **Settings:** `nortantis.MapSettings` - serialized as `.nort` files

## Architecture Patterns

### Platform Abstraction (Strategy Pattern)
```java
// Switch between AWT and SKia rendering:
PlatformFactory.setInstance(new AwtFactory());    // CPU-only
PlatformFactory.setInstance(new SkiaFactory());   // Can run on CPU or GPU. See SkiaImage.shouldUseGPU(). Note that small images are processed on the CPU for performance, according to SkiaImage.GPU_THRESHOLD_PIXELS.
```

### Resource Management
`Image` and `Painter` implement `AutoCloseable`. Always use try-with-resources for Painters:
```java
try (Painter p = image.createPainter()) {
    // drawing operations
}
```
Use try-with-resources for Image whenever feasible.

## Runtime Configuration

GPU and shader behavior is controlled via `GPUExecutor.setRenderingMode()`:

```java
GPUExecutor.setRenderingMode(RenderingMode.DEFAULT);     // Auto-detect (try GPU, fall back to CPU shaders)
GPUExecutor.setRenderingMode(RenderingMode.GPU);         // Force GPU with shaders
GPUExecutor.setRenderingMode(RenderingMode.CPU_SHADERS); // Force CPU with Skia shaders
GPUExecutor.setRenderingMode(RenderingMode.CPU);         // Force traditional pixel-by-pixel
```

| Mode | Description |
|------|-------------|
| `DEFAULT` | Auto-detect: try GPU, fall back to CPU shaders if unavailable |
| `GPU` | GPU acceleration with shaders (fastest, requires GPU hardware) |
| `CPU_SHADERS` | CPU rendering with Skia shader rasterizer (no GPU required) |
| `CPU` | Traditional pixel-by-pixel CPU operations (no shaders) |

The rendering mode can be changed at any time via `setRenderingMode()`. Tests should reset to `DEFAULT` when done.

## Testing

Important tests:
- `MapCreatorTest` - Awt rendering tests. These tests are very slow, so only run them as needed, and only when testing AWT changes or changes that can affect both Skia and AWT.
- `SkiaMapCreatorTest` - Test map creation with Skia. Good for testing GPU if enabled.
- `ImageHelperTest` - Test ImageHelper in Skia
- `SkiaPainterTest` - Test basic Skia rendering

My unit tests can be overly strict sometimes because they compare pixe-by-pixel. If the output images look close enough that a person wouldn't be able to easily tell the difference, then that's probably good enough, as long as the test is consistent in the results it gives.

Test data locations:
- Map settings: `unit test files/map settings/`
- Expected outputs:
	`unit test files/expected maps/` for MapCreatorTest
	`unit test files/expected maps skia/` for SkiaMapCreatorTest
	`unit test files/expected image helper tests` for ImageHelperTest
	`unit test files/expected skia tests` for SkiaPainterTest

## Performance Benchmarking

Use the benchmark task to profile map creation performance:

```bash
./gradlew benchmark
```

This runs `MapCreatorBenchmark` with JFR (Java Flight Recorder) profiling enabled. The JFR recording is saved to `build/profile.jfr`.

**Analyzing results:**
- Open `build/profile.jfr` in JDK Mission Control (`jmc`) or IntelliJ IDEA
- Look at "Hot Methods" or "Method Profiling" to find CPU hotspots
- Use the Call Tree view to see time spent in each method

**When working on performance:**
1. Run `./gradlew benchmark` to establish a baseline
2. Make changes
3. Run benchmark again to measure improvement
4. Use JFR profile to identify remaining hotspots

The benchmark creates maps using settings from `unit test files/map settings/simpleSmallWorld.nort`.

## Coding Conventions

- **Formatting:** Eclipse formatter config in `eclipse-formatter-config.xml`, enforced by Spotless
- **Naming:** PascalCase for classes, camelCase for methods/variables
- **Custom Functional Interfaces:** `Function<T, R>`, `Function0<R>`, `Function2<T1, T2, R>`
- **Tuple Classes:** `Tuple2`, `Tuple3`, `Tuple4`, `Pair`, `OrderlessPair`
- **Helper class for timing:** nortantis.util.Stopwatch
- **Avoid hidden falbacks when possible:** Try to avoid add hidden fallbacks in code unless reasonable use cases are likely to need them, especially falling back from GPU to CPU image processing.

## Map Generation Pipeline

For "full" draws:

```
MapSettings (.nort file)
  ↓
MapCreator.createMap()
  ├─ GraphCreator.createGraph() → WorldGraph (Voronoi + elevation)
  ├─ drawTerrain() → Land, water, coasts
  ├─ drawRivers() → Rivers and lakes
  ├─ drawMountains() → Mountain textures
  ├─ drawIcons() → City/landmark icons
  ├─ drawText() → Place/region names
  └─ drawBorders() → Political boundaries
  ↓
Image (final rendered map)
```

"Incremental" draws update only part of the map, going through one of the MapCreator.incrementalUpdate\* methods. Incremental draws or what allows the editor to quickly update the map in near real time while the user is drawing or changing text.


## Key Algorithms

- **Tectonic Plates:** Random plate generation with collision-based elevation
- **Voronoi Diagrams:** Fortune's Algorithm with Lloyd Relaxation
- **Rivers:** Flow from high to low elevation along Voronoi edges
- **Names:** N-gram generation from classic literature (`assets/books/`)

## CPU Vs GPU rendering

When fixing bugs or performance issues caused by differences between CPU versus GPU rendering, try to fix the issue to make GPU rendering work correctly and efficiently rather than suggest switching to or falling back to CPU rendering. If that's not feasable, ask me before switching anything to CPU rendering.

If you discover anything to be incorrect in these instructions, please update them in Claude.md.