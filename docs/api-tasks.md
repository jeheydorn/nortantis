# Nortantis Map API - Implementation Tasks

## Current State

API server (`nortantis.api.MapApiServer`) fully implements all planned query parameters.

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health` | GET | Health check, returns version |
| `/api/maps/generate` | POST | Generate map (empty body=random, or JSON body for settings) |
| `/api/maps/generate-from-settings` | POST | Generate from full .nort JSON body |

### Supported Query Parameters

All parameters are optional. Unspecified values use random/default from `SettingsGenerator`.

| Parameter | Type | Values / Range | Default | Description |
|-----------|------|---------------|---------|-------------|
| `size` | string | `small`, `medium`, `large`, `wide_small`, `wide_medium`, `wide_large`, `golden`, `custom` | random | Output dimensions preset |
| `width` | int | 256-8192 | - | Custom width (requires `size=custom`) |
| `height` | int | 256-8192 | - | Custom height (requires `size=custom`) |
| `quality` | string | `draft`, `low`, `medium`, `high` | `low` (0.5) | Resolution scale (0.25 / 0.5 / 0.75 / 1.0) |
| `format` | string | `png`, `jpg` | `png` | Output image format |
| `seed` | long | any | random | Random seed (returned in `X-Map-Seed` header) |
| `worldSize` | int | 2000-32000 | random | World complexity |
| `landShape` | string | `continents`, `inland_sea`, `scattered` | random | Land mass shape |
| `regionCount` | int | 2-20 | auto | Number of political regions |
| `drawText` | bool | `true`, `false` | true | Show place names and titles |
| `drawBorder` | bool | `true`, `false` | random | Draw decorative border |
| `drawRoads` | bool | `true`, `false` | true | Show roads between cities |

### Size Presets

| Preset | Width | Height | Aspect Ratio | Use Case |
|--------|-------|--------|--------------|----------|
| `small` | 1024 | 1024 | 1:1 | Thumbnail / preview |
| `medium` | 2048 | 2048 | 1:1 | Web display |
| `large` | 4096 | 4096 | 1:1 | High quality download |
| `wide_small` | 1920 | 1080 | 16:9 | Web banner |
| `wide_medium` | 2560 | 1440 | 16:9 | 2K wallpaper |
| `wide_large` | 4096 | 2304 | 16:9 | 4K wallpaper |
| `golden` | 4096 | 2531 | Golden ratio | Classic print style |

---

## Completed Tasks

- [x] **Task 1: Image Size Presets** — `ApiSizePreset` enum + `size`, `width`, `height` params
- [x] **Task 2: Resolution Control** — `ApiQuality` enum + `quality` param, clamped to max 2.0
- [x] **Task 3: Output Format** — `format=png|jpg` with JPEG quality 0.95
- [x] **Task 4: World Parameters** — `seed`, `worldSize`, `landShape`, `regionCount`, `drawText`, `drawBorder`, `drawRoads`
- [x] **Task 5: Response Headers** — `X-Map-Seed` header for reproducibility
- [x] **Task 6: Validation** — 400 errors with clear messages for invalid parameters

---

## Test Results

All tests passed:

| Test | Command | Result |
|------|---------|--------|
| Health check | `GET /api/health` | `{"status":"ok","version":"3.18"}` |
| Small + draft | `?size=small&quality=draft` | 294x294 PNG, 160KB |
| Wide large + high + seed | `?size=wide_large&quality=high&seed=42&landShape=continents&regionCount=5` | 4160x2368 PNG |
| JPG format | `?size=small&quality=draft&format=jpg` | 396x396 JPEG, 70KB |
| Scattered + no text | `?landShape=scattered&drawText=false&drawBorder=false` | 1024x1024 PNG |
| Custom size | `?size=custom&width=1600&height=900&quality=low` | 834x484 PNG |
| Bad size (error) | `?size=huge` | 400 with error message |
| Missing width (error) | `?size=custom&height=500` | 400 with error message |
| Seed header | `X-Map-Seed` response header | Returns seed value (e.g. `42`) |

---

## Usage Examples

```bash
# Start the server
./gradlew runApi

# Quick preview
curl -X POST "http://localhost:8080/api/maps/generate?size=small&quality=draft" -o preview.png

# High quality reproducible continent map
curl -X POST "http://localhost:8080/api/maps/generate?size=wide_large&quality=high&seed=12345&landShape=continents&regionCount=8" -o map.png

# Scattered islands wallpaper as JPEG
curl -X POST "http://localhost:8080/api/maps/generate?size=wide_medium&quality=medium&landShape=scattered&format=jpg" -o wallpaper.jpg

# Custom dimensions, no text or roads
curl -X POST "http://localhost:8080/api/maps/generate?size=custom&width=3840&height=2160&quality=medium&drawText=false&drawRoads=false" -o clean_map.png

# Generate from .nort file, override to small size
curl -X POST "http://localhost:8080/api/maps/generate-from-settings?size=small&quality=low" -d @my_map.nort -o output.png
```

---

## Files

| File | Description |
|------|-------------|
| `src/nortantis/api/MapApiServer.java` | HTTP server with all endpoints and parameter handling |
| `src/nortantis/api/ApiSizePreset.java` | Size preset enum (small → golden) |
| `src/nortantis/api/ApiQuality.java` | Quality preset enum (draft → high) |
| `src/nortantis/MapSettings.java` | Added `parseJsonForApi()` public method |
| `build.gradle.kts` | Added `runApi` Gradle task |
