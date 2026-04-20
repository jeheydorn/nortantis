# API Next Parameters - Analysis & Tasks

## Already Implemented

`size`, `width`, `height`, `quality`, `format`, `seed`, `worldSize`, `landShape`, `regionCount`, `drawText`, `drawBorder`, `drawRoads`

---

## Priority 1: Visual Style (High Impact)

These parameters dramatically change the map's look and feel. Users will want these most.

### 1.1 Colors

| Parameter | Type | Example | MapSettings Field | Description |
|-----------|------|---------|-------------------|-------------|
| `oceanColor` | hex | `#2a4a6b` | `oceanColor` | Ocean fill color |
| `landColor` | hex | `#c8b078` | `landColor` | Base land fill color |
| `textColor` | hex | `#000000` | `textColor` | Text label color |
| `riverColor` | hex | `#4a6fa5` | `riverColor` | River line color |
| `roadColor` | hex | `#000000` | `roadColor` | Road line color |
| `coastlineColor` | hex | `#000000` | `coastlineColor` | Coastline color |
| `borderColor` | hex | `#000000` | `borderColor` | Border frame color |

**API example:**
```
?oceanColor=2a4a6b&landColor=c8b078&textColor=333333
```

**Implementation:** Parse hex string → `Color.create(r, g, b)`. Support with/without `#` prefix.

### 1.2 Background Type

| Parameter | Type | Values | MapSettings Fields | Description |
|-----------|------|--------|-------------------|-------------|
| `background` | string | `generated`, `texture`, `solid` | `generateBackground`, `generateBackgroundFromTexture`, `solidColorBackground` | How the background is created |
| `backgroundTexture` | string | `old_paper_1`, `old_paper_2`, `grungy_paper`, `wavy_paper`, `declaration` | `backgroundTextureResource` | Which texture to use (when background=texture) |

Available textures (from assets):
- `old_paper_1` — old paper 1.png
- `old_paper_2` — old paper 2.png
- `grungy_paper` — grungy paper.png
- `wavy_paper` — wavy paper.png
- `declaration` — declaration of independence back.png

### 1.3 Ocean Effect

| Parameter | Type | Values | MapSettings Field | Description |
|-----------|------|--------|-------------------|-------------|
| `oceanWaves` | string | `none`, `ripples`, `concentric` | `oceanWavesType` | Ocean wave style |
| `concentricWaveCount` | int | 2-5 | `concentricWaveCount` | Number of concentric waves |

### 1.4 Line Style

| Parameter | Type | Values | MapSettings Field | Description |
|-----------|------|--------|-------------------|-------------|
| `lineStyle` | string | `jagged`, `splines`, `smooth_coast` | `lineStyle` | Coastline rendering style |

Maps to: `Jagged`, `Splines`, `SplinesWithSmoothedCoastlines`

---

## Priority 2: Geography Control (Medium Impact)

Fine-tuning how the world is shaped.

### 2.1 Land/Water Balance

| Parameter | Type | Range | Default | MapSettings Field | Description |
|-----------|------|-------|---------|-------------------|-------------|
| `centerLandProbability` | double | 0.0-1.0 | auto | `centerLandToWaterProbability` | Chance of land in center (higher = more land) |
| `edgeLandProbability` | double | 0.0-1.0 | auto | `edgeLandToWaterProbability` | Chance of land on edges (0 = island, 1 = land fills map) |
| `cityProbability` | double | 0.0-0.025 | 0.00625 | `cityProbability` | Density of cities |

### 2.2 Region Display

| Parameter | Type | Values | MapSettings Field | Description |
|-----------|------|--------|-------------------|-------------|
| `drawRegionColors` | bool | true/false | `drawRegionColors` | Color regions differently |
| `drawRegionBoundaries` | bool | true/false | `drawRegionBoundaries` | Draw borders between regions |

---

## Priority 3: Detail & Decoration (Lower Impact)

### 3.1 Icon Scales

| Parameter | Type | Range | Default | MapSettings Field | Description |
|-----------|------|-------|---------|-------------------|-------------|
| `mountainScale` | double | 0.5-3.0 | 1.2 | `mountainScale` | Mountain icon size |
| `hillScale` | double | 0.5-3.0 | 1.2 | `hillScale` | Hill icon size |
| `treeScale` | double | 0.1-1.0 | 0.4 | `treeHeightScale` | Tree icon size |
| `cityScale` | double | 0.5-3.0 | 1.2 | `cityScale` | City icon size |
| `duneScale` | double | 0.5-3.0 | 1.2 | `duneScale` | Sand dune icon size |

### 3.2 Border & Edge Effects

| Parameter | Type | Range/Values | MapSettings Field | Description |
|-----------|------|-------------|-------------------|-------------|
| `frayedBorder` | bool | true/false | `frayedBorder` | Frayed/torn paper edges |
| `drawGrunge` | bool | true/false | `drawGrunge` | Dark grunge stains around edges |
| `grungeWidth` | int | 100-1500 | random | Width of grunge effect |
| `borderWidth` | int | 25-300 | random | Border frame thickness |

### 3.3 Shading Levels

| Parameter | Type | Range | Default | MapSettings Field | Description |
|-----------|------|-------|---------|-------------------|-------------|
| `coastShadingLevel` | int | 0-100 | random(15-50) | `coastShadingLevel` | Darkness of coast shadows |
| `oceanWavesLevel` | int | 0-100 | random(15-50) | `oceanWavesLevel` | Intensity of ocean wave effect |
| `coastlineWidth` | double | 0.5-5.0 | 1.0 | `coastlineWidth` | Coastline thickness |

---

## Priority 4: Name Generation (Unique Feature)

### 4.1 Books (Name Sources)

| Parameter | Type | Values | MapSettings Field | Description |
|-----------|------|--------|-------------------|-------------|
| `books` | string (comma-separated) | see list | `books` | Books used for generating place names |

Available books:
- `a_princess_of_mars`
- `ancient_egypt`
- `around_the_world_in_80_days`
- `middle_ages` (Beacon Lights of History)
- `gullivers_travels`
- `jungle_tales_of_tarzan`
- `parish_priests_middle_ages`
- `ssa_boy_names`
- `ssa_girl_names`
- `faerie_queene`
- `alembic_plot`
- `art_of_war_middle_ages`
- `underground_city`
- `wizard_of_oz`

**API example:**
```
?books=ancient_egypt,wizard_of_oz
```

This gives users creative control over the naming style — Egyptian-sounding names, fantasy names, etc.

### 4.2 Text Seed

| Parameter | Type | Range | MapSettings Field | Description |
|-----------|------|-------|-------------------|-------------|
| `textSeed` | long | any | `textRandomSeed` | Separate seed for name generation (same map, different names) |

---

## Priority 5: Discovery Endpoints (No Generation)

New GET endpoints to help API users discover valid parameter values.

| Endpoint | Returns |
|----------|---------|
| `GET /api/options/sizes` | List of size presets with dimensions |
| `GET /api/options/qualities` | Quality presets with resolution values |
| `GET /api/options/books` | Available name generation books |
| `GET /api/options/textures` | Available background textures |
| `GET /api/options/borders` | Available border types |
| `GET /api/options/land-shapes` | Land shape enum values |
| `GET /api/options/ocean-waves` | Ocean wave types |

---

## Implementation Order

| Phase | Tasks | New Params Count | Effort |
|-------|-------|-----------------|--------|
| **Phase 1** | Colors + Background + Ocean + LineStyle | ~12 | Medium |
| **Phase 2** | Land/Water balance + Region display | ~5 | Small |
| **Phase 3** | Icon scales + Border effects + Shading | ~12 | Medium |
| **Phase 4** | Books + textSeed | ~2 | Small |
| **Phase 5** | Discovery endpoints | 0 (7 new endpoints) | Medium |

**Total new parameters: ~31**

---

## Notes

- Hex color parsing: accept both `#RRGGBB` and `RRGGBB`
- All new parameters remain optional — omitted = random/default
- For Phase 5 discovery endpoints, return JSON arrays so frontend can build dynamic UIs
- `books` parameter requires mapping from URL-friendly slug to actual book filename
