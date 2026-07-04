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

The platform abstraction layer allows different rendering backends.

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

### Do NOT regenerate the expected maps

The expected maps (and other expected-output images) are gitignored and exist so **I** can hand-inspect what a change altered and verify fixes — including yours. Do not delete or overwrite them to make failing pixel comparisons pass, and do not use them as your own correctness oracle by regenerating and re-comparing.

- Never bulk-delete or bulk-regenerate the expected-maps folder, and never copy `failed maps` over `expected maps` en masse.
- If your change legitimately alters rendering, **leave the existing expected maps in place and report which tests now differ** (with the diff locations / a short description), so I can review and regenerate them myself.
- The only exception: while iterating on a tests you are actively working on, you may delete/regenerate the expected maps for only those tests. Do not touch any others.
- A failing MapCreatorTest pixel comparison is a signal to investigate and report, not to re-baseline.

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

## Coding Conventions

- **Formatting:** Match the formatting of the surrounding code by hand (the project uses the Eclipse formatter config in `eclipse-formatter-config.xml`). Do NOT run `gradlew spotlessApply` to format your changes — the committed code has drifted from the current Spotless config, so a project-wide apply rewraps comments in many unrelated files and pollutes the diff.
- **Naming:** PascalCase for classes, camelCase for methods/variables
- **Abbreviations:** Use full words in variable and method names. Single-letter names are fine when used consistently in the existing code (e.g. `p` for Point or Painter, `c` for Center, `e` for a map entry). Established acronyms are fine (e.g. `RI` for resolution-invariant). Do not use partial-word abbreviations that drop vowels or truncate words (e.g. write `samplePoints` not `samplePts`, `selectionBounds` not `selBounds`, `originalCenter` not `origCenter`). Do not use opaque prefixed names where the prefix is not self-evident (e.g. avoid `sIsWater`, `oe`, `sRegionId`).
- **Custom Functional Interfaces:** `Function<T, R>`, `Function0<R>`, `Function2<T1, T2, R>`
- **Tuple Classes:** `Tuple2`, `Tuple3`, `Tuple4`, `Pair`, `OrderlessPair`
- **Helper class for timing:** nortantis.util.Stopwatch
- **Rectangle and dimension classes:** Use `nortantis.geom.Rectangle`, `IntRectangle`, `RotatedRectangle`, `Dimension`, and `IntDimension` for anything bounding-box or size related. These classes have methods for intersection, union, containment checks, `fromCorners`, etc. Prefer these over recreating bounding-box logic inline.
- **Shared constants and formulas:** When a numeric constant or formula is referenced in more than one place — especially one that's calibrated (a magic factor, a tuned threshold, a stroke/radius pair, etc.) — extract it into a named constant or a helper method on the first re-use, and have all references go through that name. Do not copy-paste the value or formula into multiple call sites. If the value is derived (e.g. a hit radius is centerline + half the stroke width), derive it from the same helper the original draw code uses, so future tweaks stay in sync automatically.
- **Translations:** Whenever you add or modify a string key accessed via `Translation.get(...)`, update the English file (`messages.properties`) and all language files (`messages_de.properties`, `messages_es.properties`, `messages_fr.properties`, `messages_pt.properties`, `messages_ru.properties`, `messages_zh.properties`) in `src/nortantis/swing/translation/`.
- **Javadoc `{@link}` references:** IntelliJ code analysis (which I use, and reports these as errors) resolves `{@link ...}` targets strictly. When you write a `{@link Type#member}` or `{@link Type}` in a doc comment: (1) the `Type` must be imported in that file or fully qualified — a simple-name link to a type that isn't imported fails to resolve (e.g. `{@link MapEdits#...}` from a `nortantis` class, since `MapEdits` lives in `nortantis.swing`); adding the import (or using the fully-qualified name in the link) fixes it. (2) You cannot `{@link}` a **private** or otherwise inaccessible member (e.g. `{@link MapCreator#incrementalUpdateBounds}` where that method is private) — use `{@code Type#member}` instead. Same-class private members are fine to `{@link #member}`. Prefer `{@code ...}` whenever a real link isn't worth an import.

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

### Editor draw pipeline is asynchronous and queued (`MapUpdater`)

In the editor, every redraw goes through `nortantis.editor.MapUpdater`, which is **asynchronous and queued** — a frequent source of subtle bugs, so understand it before touching draw-completion logic:

- Public `createAndShowMap*` methods (and `createAndShowMapFromChange` for undo/redo) don't draw immediately. They funnel through `createAndShowMap` → `createAndShowMapUsingIds` → `innerCreateAndShowMap`, which runs the actual draw on a **background thread** (`doInBackground`), then calls back on the EDT in `done()`.
- **If a draw is already running** (`isMapBeingDrawn`), the new request is **queued** as a `MapUpdate` (in `nonIncrementalUpdatesToDraw` / `incrementalUpdatesToDraw` / `lowPriorityUpdatesToDraw`) instead of drawn now. When the current draw finishes, `done()` pulls the next via `combineAndGetNextUpdateToDraw()`, which **coalesces** queued updates (e.g. a queued Full supersedes everything; same-type updates merge via `MapUpdate.add()`). So the draw that completes may not correspond 1:1 to a single user action.
- **Consequence:** a mutable field set right before calling a `createAndShowMap*` method is NOT a reliable way to tag "the resulting draw," because an earlier in-flight draw can finish first and consume it, or the request can be queued/coalesced. Likewise, **don't stash per-draw data in a `MapUpdater` field** to hand it to the completion callback — that is the same global-state shape and invites the same bugs. Instead, **carry per-draw state on the `MapUpdate`** (e.g. `isLowPriority`, `isUndoRedo`), thread it through `innerCreateAndShowMap` (whose params are captured by the background task and so are available, effectively final, in `done()` — like `updateType`), and **pass it as a parameter** of `onFinishedDrawingFull` / `onFinishedDrawingIncremental` (e.g. `citiesRemovedForWater`, `wasTriggeredByUndoRedo`). When adding such state, also merge it in `MapUpdate.add()` so coalesced draws keep it, and have each `createAndShow*` entry point pass the truthful value (don't hard-code a default that happens to be usually-right — e.g. `createAndShowLowPriorityChanges` is called both after forward edits and by the undoer).
- `onFinishedDrawingFull` / `onFinishedDrawingIncremental` are the EDT completion callbacks (overridden by `MainWindow`, `SubMapDialog`, `NewSettingsDialog`); `anotherDrawIsQueued` tells you whether more draws are pending. `incrementalChangeArea == null` distinguishes a full draw from an incremental one.


## Key Algorithms

- **Tectonic Plates:** Random plate generation with collision-based elevation
- **Voronoi Diagrams:** Fortune's Algorithm with Lloyd Relaxation
- **Rivers:** Flow from high to low elevation along Voronoi edges
- **Names:** N-gram generation from classic literature (`assets/books/`)

If you discover anything to be incorrect in these instructions, please update them in CLAUDE.md.
