# Nortantis

Fantasy map generator and editor that uses tectonic plate simulation to create islands and continents with terrain features (trees, rivers, mountains) rendered with an old-fashioned, hand-drawn appearance.

## Tech Stack

- **Language:** Java 21+
- **Build System:** Gradle (use `gradlew` wrapper)
- **UI Framework:** Swing with FlatLaf look-and-feel
- **Graphics:** AWT (CPU-based rendering)
- **Testing:** JUnit 5

## Shell and Paths

The shell is Windows bash (Git Bash / MSYS2). Use Windows-style paths like `C:/Users/...`, not WSL-style `/mnt/c/...` paths — the latter do not exist in this environment.

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
│   └── awt/                 # AWT rendering implementation
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
// The desktop app uses AWT rendering:
PlatformFactory.setInstance(new AwtFactory());
```

The platform abstraction layer allows different rendering backends. The Skia backend has been moved to the Android project.

### Resource Management
`Image` and `Painter` implement `AutoCloseable`. Always use try-with-resources for Painters:
```java
try (Painter p = image.createPainter()) {
    // drawing operations
}
```
Use try-with-resources for Image whenever feasible.

## Testing

Important tests:
- `MapCreatorTest` - AWT rendering tests. These tests are very slow, so only run them as needed.
- `ImageHelperTest` - Test ImageHelper operations

My unit tests can be overly strict sometimes because they compare pixel-by-pixel. If the output images look close enough that a person wouldn't be able to easily tell the difference, then that's probably good enough, as long as the test is consistent in the results it gives.

Test data locations:
- Map settings: `unit test files/map settings/`
- Expected outputs:
	`unit test files/expected maps/` for MapCreatorTest
	`unit test files/expected image helper tests` for ImageHelperTest

## Performance Benchmarking

Use the benchmark task to profile map creation performance:

```bash
./gradlew benchmark
```

This runs `AwtMapCreatorBenchmark` with JFR (Java Flight Recorder) profiling enabled. The JFR recording is saved to `build/profile.jfr`.

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

## Editing Files

Whenever possible, use the Read, Edit, and Write tools to read and modify files. Try to never use Python scripts, shell commands like `sed` or `awk`, or PowerShell to read or write file content.

If the Edit tool fails with "String to replace not found", the most likely cause is that the file uses Windows CRLF line endings and the tool is having trouble matching. To work around this without resorting to scripts:
- Try using a slightly different (larger) context string — include one or two extra lines above or below the target text. The Edit tool often succeeds once there is enough unique surrounding context.
- Alternatively, if the block to replace is large or the whole file needs rewriting, use the Write tool to overwrite the file entirely (after reading it first).

## Coding Conventions

- **Formatting:** Eclipse formatter config in `eclipse-formatter-config.xml`, enforced by Spotless
- **Naming:** PascalCase for classes, camelCase for methods/variables
- **Abbreviations:** Use full words in variable and method names. Single-letter names are fine when used consistently in the existing code (e.g. `p` for Point or Painter, `c` for Center, `e` for a map entry). Established acronyms are fine (e.g. `RI` for resolution-invariant). Do not use partial-word abbreviations that drop vowels or truncate words (e.g. write `samplePoints` not `samplePts`, `selectionBounds` not `selBounds`, `originalCenter` not `origCenter`). Do not use opaque prefixed names where the prefix is not self-evident (e.g. avoid `sIsWater`, `oe`, `sRegionId`).
- **Custom Functional Interfaces:** `Function<T, R>`, `Function0<R>`, `Function2<T1, T2, R>`
- **Tuple Classes:** `Tuple2`, `Tuple3`, `Tuple4`, `Pair`, `OrderlessPair`
- **Helper class for timing:** nortantis.util.Stopwatch
- **Rectangle and dimension classes:** Use `nortantis.geom.Rectangle`, `IntRectangle`, `RotatedRectangle`, `Dimension`, and `IntDimension` for anything bounding-box or size related. These classes have methods for intersection, union, containment checks, `fromCorners`, etc. Prefer these over recreating bounding-box logic inline.
- **Translations:** Whenever you add or modify a string key accessed via `Translation.get(...)`, update the English file (`messages.properties`) and all language files (`messages_de.properties`, `messages_es.properties`, `messages_fr.properties`, `messages_pt.properties`, `messages_ru.properties`, `messages_zh.properties`) in `src/nortantis/swing/translation/`.

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

## Android / Skia

The Skia rendering backend has been moved to the Android project (`NortantisTest`). This project produces a JAR (`./gradlew jar`) that the Android project depends on. See the Android project's CLAUDE.md for details.

If you discover anything to be incorrect in these instructions, please update them in CLAUDE.md.
