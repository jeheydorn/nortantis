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
PlatformFactory.setInstance(new SkiaFactory());   // Can run on CPU or GPU. See SkiaImage.shouldUseGPU()
```

### Resource Management
`Image` and `Painter` implement `AutoCloseable`. Always use try-with-resources for Painters:
```java
try (Painter p = image.createPainter()) {
    // drawing operations
}
```
Use try-with-resources for Image whenever feasible.

## Testing

Tests run for both CPU (AWT) and GPU (Skia) backends:
- `MapCreatorTest` - Awt rendering tests. Currently disabled.
- `SkiaMapCreatorTest` - Test map creation with Skia. Good for testing GPU if enabled.
- `ImageHelperTest` - Test ImageHelper in Skia
- `SkiaPainterTest` - Test basic Skia rendering


Test data locations:
- Map settings: `unit test files/map settings/`
- Expected outputs: 
	`unit test files/expected maps/` for MapCreatorTest
	`unit test files/expected maps skia/` for SkiaMapCreatorTest
	`unit test files/expected image helper tests` for ImageHelperTest
	`unit test files/expected skia tests` for SkiaPainterTest
	

## Coding Conventions

- **Formatting:** Eclipse formatter config in `eclipse-formatter-config.xml`, enforced by Spotless
- **Naming:** PascalCase for classes, camelCase for methods/variables
- **Custom Functional Interfaces:** `Function<T, R>`, `Function0<R>`, `Function2<T1, T2, R>`
- **Tuple Classes:** `Tuple2`, `Tuple3`, `Tuple4`, `Pair`, `OrderlessPair`
- **Helper class for timing:** nortantis.util.Stopwatch

## Map Generation Pipeline

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

## Key Algorithms

- **Tectonic Plates:** Random plate generation with collision-based elevation
- **Voronoi Diagrams:** Fortune's Algorithm with Lloyd Relaxation
- **Rivers:** Flow from high to low elevation along Voronoi edges
- **Names:** N-gram generation from classic literature (`assets/books/`)

If you discover anything to be incorrect in these instructions, please let me know.