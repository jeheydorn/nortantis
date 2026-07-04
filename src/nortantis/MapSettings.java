package nortantis;

import nortantis.editor.*;
import nortantis.geom.Point;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.swing.MapEdits;
import nortantis.swing.translation.Translation;
import nortantis.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * For parsing and storing map settings.
 *
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapSettings implements Serializable
{
	/**
	 * When updating this, also update installers/version.txt.
	 *
	 * This must not contain trailing zeros (use "3.2", not "3.20"), since versions are compared numerically and a trailing zero would
	 * change the value.
	 */
	public static final String currentVersion = "3.2";
	public static final String fileExtension = "nort";
	public static final String fileExtensionWithDot = "." + fileExtension;
	public static final double defaultPointPrecision = 2.0;
	public static final double defaultLloydRelaxationsScale = 0.1;
	public static final double defaultResolution = 1.0;
	public static final double defaultHeightmapResolution = 1.0;
	private final double defaultTreeHeightScaleForOldMaps = 0.5;
	private final double defaultRoadWidth = 1.0;
	private final Stroke defaultRoadStyle = new Stroke(StrokeType.Dots, (float) (MapCreator.calcSizeMultiplierFromResolutionScaleRounded(1.0) * defaultRoadWidth));
	private final Color defaultRoadColor = Color.black;
	public static final Color defaultIconFillColor = Color.gray;
	/**
	 * The value of defaultIconFillColor before version 3.2. Retained because maps saved before 3.2 omitted an icon's (or icon type's) fill
	 * color when it equaled the default of the time, so on load a visible icon left at the old default must be resolved back to this value
	 * to preserve its color rather than picking up the new default.
	 */
	public static final Color defaultIconFillColorBeforeV3_2 = Color.create(155, 105, 49, (int) (255 * 0.7));
	public static final HSBColor defaultIconFilterColor = new HSBColor(0, 0, 0, 0);

	public String version;
	public long randomSeed;
	/**
	 * A scalar multiplied by the map height and width to get the final resolution.
	 * I set this to the default here just to be safe, even though I believe all code paths override it.
	 */
	public double resolution = defaultResolution;
	public int coastShadingLevel;
	@Deprecated
	public int oceanEffectsLevel;
	public int oceanWavesLevel;
	public int oceanShadingLevel;
	public int concentricWaveCount;
	public boolean jitterToConcentricWaves;
	public boolean brokenLinesForConcentricWaves;
	public boolean fadeConcentricWaves;
	public OceanWaves oceanWavesType;
	public boolean drawOceanEffectsInLakes;
	public int worldSize;
	public Color riverColor;
	public Color roadColor;
	public Color coastShadingColor;
	@Deprecated
	public Color oceanEffectsColor;
	public Color oceanWavesColor;
	public Color oceanShadingColor;
	public Color coastlineColor;
	public double coastlineWidth;
	public double centerLandToWaterProbability;
	public double edgeLandToWaterProbability;
	public LandShape landShape;
	public int regionCount;
	public boolean frayedBorder;
	public int frayedBorderSize;
	public Color frayedBorderColor;
	public int frayedBorderBlurLevel;
	public long frayedBorderSeed;
	public int grungeWidth;
	public boolean drawGrunge;
	/**
	 * This setting actually means fractal generated as opposed to generated from texture. It is mutually exclusive with
	 * generateBackgroundFromTexture
	 */
	public boolean generateBackground;
	public boolean generateBackgroundFromTexture;
	public boolean solidColorBackground;
	public boolean colorizeOcean; // For backgrounds generated from a texture.
	public boolean colorizeLand; // For backgrounds generated from a texture.
	public TextureSource backgroundTextureSource;
	/**
	 * The path to the background texture image if a specific file was selected.
	 */
	public String backgroundTextureImage;
	/**
	 * The path to the background texture image if one was selected from an art pack.
	 */
	public NamedResource backgroundTextureResource;
	public long backgroundRandomSeed;
	public Color oceanColor;
	public Color landColor;
	public Color regionBaseColor;
	public int generatedWidth;
	public int generatedHeight;
	public int hueRange;
	public int saturationRange;
	public int brightnessRange;
	public boolean drawText;
	public long textRandomSeed;
	public Set<String> books;
	public Font titleFont;
	public Font regionFont;
	public Font mountainRangeFont;
	public Font otherMountainsFont;
	public Font citiesFont;
	public Font riverFont;
	public Color boldBackgroundColor;
	public Color textColor;
	public MapEdits edits;
	public boolean drawBoldBackground;
	public boolean drawRegionBoundaries;
	public Stroke regionBoundaryStyle;
	public Color regionBoundaryColor;
	/**
	 * Note - this should be considered false if drawRegionBoundaries is false.
	 */
	public boolean drawRegionColors;
	public long regionsRandomSeed;
	public boolean drawBorder;
	@Deprecated
	public String borderType;
	public NamedResource borderResource;
	public int borderWidth;
	public BorderPosition borderPosition;
	public BorderColorOption borderColorOption;
	public Color borderColor;
	public boolean drawRoads = true;
	public double cityProbability;
	public LineStyle lineStyle;
	/**
	 * No longer an editable field. Maintained for backwards compatibility when loading older maps, and for telling new maps which city
	 * images to use. But the editor now allows selecting city images of any type.
	 */
	public String cityIconTypeName;
	// Not exposed for editing. Only for backwards compatibility so I can change it without braking older settings
	// files that have edits.
	public double pointPrecision = defaultPointPrecision;
	public double lloydRelaxationsScale = defaultLloydRelaxationsScale;
	public String imageExportPath;
	public String heightmapExportPath;
	/**
	 * I set this to the default here just to be safe, even though I believe all code paths override it.
	 */
	public double heightmapResolution = defaultHeightmapResolution;
	public String customImagesPath;
	/**
	 * When generating a new map, this is the art pack to use. When editing a map, this is the art pack displayed in the UI.
	 */
	public String artPack;
	public double treeHeightScale;
	// Default scale values below are for old maps from properties files. For current defaults, see SettingsGenerator.
	public double mountainScale = 1.0;
	public double hillScale = 1.0;
	public double duneScale = 1.0;
	public double cityScale = 1.0;
	public static final ExportAction defaultDefaultExportAction = ExportAction.SaveToFile;
	public ExportAction defaultMapExportAction = defaultDefaultExportAction;
	public ExportAction defaultHeightmapExportAction = defaultDefaultExportAction;
	public Stroke roadStyle;

	public boolean drawOverlayImage;
	public String overlayImagePath;
	private final int overlayImageDefaultTransparency = 50;
	/**
	 * An integer percentage between 0 and 100 inclusive.
	 */
	public int overlayImageTransparency = overlayImageDefaultTransparency;
	/**
	 * Stores the overlay image location as an offset from the default place it is drawn, which is in the center of the map.
	 */
	public Point overlayOffsetResolutionInvariant = new Point(0, 0);
	private final double overlayImageDefaultScale = 1.0;
	public double overlayScale = overlayImageDefaultScale;

	public int rightRotationCount;
	public boolean flipHorizontally;
	public boolean flipVertically;

	private ConcurrentHashMap<IconType, Color> iconFillColorsByType;
	private ConcurrentHashMap<IconType, HSBColor> iconFilterColorsByType;
	// Implemented as a map instead of a set for concurrency.
	private ConcurrentHashMap<IconType, Boolean> maximizeOpacityByType;
	private ConcurrentHashMap<IconType, Boolean> fillWithColorByType;

	public boolean drawGridOverlay;
	public GridOverlayShape gridOverlayShape = GridOverlayShape.Horizontal_hexes;
	public int gridOverlayRowOrColCount = 16;
	public Color gridOverlayColor = Color.create(0, 0, 0, (int) (0.3 * 255.0));
	public GridOverlayOffset gridOverlayXOffset = GridOverlayOffset.zero;
	public GridOverlayOffset gridOverlayYOffset = GridOverlayOffset.zero;
	public int gridOverlayLineWidth = 3;
	public GridOverlayLayer gridOverlayLayer = GridOverlayLayer.Under_icons;
	public boolean drawVoronoiGridOverlayOnlyOnLand = true;

	/**
	 * Provenance for sub-maps: records the inputs used to create this sub-map (original map file name, selection box, detail, icon/river
	 * mode, and seed) so the user can recreate it later. Null for maps that are not sub-maps. Its presence is what defines a settings
	 * object as a sub-map.
	 */
	public SubMapInfo subMapInfo;

	public MapSettings()
	{
		iconFillColorsByType = new ConcurrentHashMap<>();
		iconFilterColorsByType = new ConcurrentHashMap<>();
		maximizeOpacityByType = new ConcurrentHashMap<>();
		fillWithColorByType = new ConcurrentHashMap<>();
		edits = new MapEdits();
	}

	/**
	 * Loads map settings file. The file can either be the newer JSON format, or the older *.properties format, which is supported only for
	 * converting old files to the new format.
	 *
	 * @param filePath
	 *            file path and file name
	 */
	public MapSettings(String filePath)
	{
		this();
		if (FilenameUtils.getExtension(filePath).toLowerCase().equals("nort"))
		{
			String fileContents = Assets.readFileAsString(filePath);
			parseFromJson(fileContents);
		}
		else if (FilenameUtils.getExtension(filePath).toLowerCase().equals("properties"))
		{
			loadFromOldPropertiesFile(filePath);
		}
		else
		{
			throw new IllegalArgumentException("The map settings file, '" + filePath + "', is not a supported file type. It must be either either a json file or a properties file.");
		}
	}

	public static boolean isOldPropertiesFile(String filePath)
	{
		return FilenameUtils.getExtension(filePath).toLowerCase().equals("properties");
	}

	/**
	 * Returns true if region boundaries are visible on the map, either because they are drawn explicitly ({@link #drawRegionBoundaries}) or
	 * because region colors create visible boundaries between regions ({@link #drawRegionColors}). This drives coastline/region-boundary
	 * smoothing and graph construction, which must treat region-color edges as boundaries to smooth even when explicit boundaries are off.
	 */
	public boolean areRegionBoundariesVisible()
	{
		return drawRegionBoundaries || drawRegionColors;
	}

	public boolean hasOldCustomImagesFolderStructure()
	{
		if (customImagesPath == null || customImagesPath.isEmpty())
		{
			return false;
		}

		if (isVersionGreaterThanOrEqualTo(version, "2.5"))
		{
			return false;
		}

		return isOldCustomImagesFolderStructure(customImagesPath);
	}

	public static boolean isOldCustomImagesFolderStructure(String customImagesPath)
	{
		String customImagesFolder = FileHelper.replaceHomeFolderPlaceholder(customImagesPath);
		File file = new File(customImagesFolder);
		if (!file.exists())
		{
			return false;
		}

		if (!file.isDirectory())
		{
			return false;
		}

		File iconsFolder = Paths.get(customImagesFolder, "icons").toFile();
		if (!iconsFolder.exists() || !iconsFolder.isDirectory())
		{
			return false;
		}

		boolean topFolderHasConvertedTypeFolder = Arrays.stream(IconType.values()).anyMatch(type -> Paths.get(customImagesFolder, type.toString()).toFile().isDirectory());
		if (topFolderHasConvertedTypeFolder)
		{
			return false;
		}

		return true;
	}

	public static void convertOldCustomImagesFolder(String customImagesPath) throws IOException
	{
		if (!isOldCustomImagesFolderStructure(customImagesPath))
		{
			return;
		}

		String customImagesFolder = FileHelper.replaceHomeFolderPlaceholder(customImagesPath);

		for (IconType type : IconType.values())
		{
			File oldTypeFile = Paths.get(customImagesFolder, "icons", type.toString()).toFile();
			if (oldTypeFile.exists() && oldTypeFile.isDirectory())
			{
				FileUtils.moveDirectoryToDirectory(Paths.get(customImagesFolder, "icons", type.toString()).toFile(), Paths.get(customImagesFolder).toFile(), false);
			}
		}

		File iconsFolder = Paths.get(customImagesFolder, "icons").toFile();
		if (iconsFolder.list().length == 0)
		{
			FileUtils.deleteDirectory(iconsFolder);
		}
	}

	public void writeToFile(String filePath) throws IOException
	{
		version = currentVersion;
		String json = toJson();
		FileHelper.writeToFile(filePath, json);
	}

	private String toJson()
	{
		return toJson(false);
	}

	@SuppressWarnings("unchecked")
	private String toJson(boolean skipEdits)
	{
		JSONObject root = new JSONObject();

		root.put("version", version);
		root.put("randomSeed", randomSeed);
		root.put("resolution", resolution);
		root.put("coastShadingLevel", coastShadingLevel);
		root.put("oceanWavesLevel", oceanWavesLevel);
		root.put("oceanShadingLevel", oceanShadingLevel);
		root.put("oceanEffectsLevel", oceanEffectsLevel);
		root.put("concentricWaveCount", concentricWaveCount);
		root.put("fadeConcentricWaves", fadeConcentricWaves);
		root.put("brokenLinesForConcentricWaves", brokenLinesForConcentricWaves);
		root.put("jitterToConcentricWaves", jitterToConcentricWaves);
		root.put("oceanEffect", enumToJson(oceanWavesType));
		root.put("drawOceanEffectsInLakes", drawOceanEffectsInLakes);
		root.put("worldSize", worldSize);
		root.put("riverColor", colorToString(riverColor));
		root.put("roadColor", colorToString(roadColor));
		root.put("roadStyle", strokeToJson(roadStyle));
		root.put("coastShadingColor", colorToString(coastShadingColor));
		root.put("oceanEffectsColor", colorToString(oceanEffectsColor));
		root.put("oceanWavesColor", colorToString(oceanWavesColor));
		root.put("oceanShadingColor", colorToString(oceanShadingColor));
		root.put("coastlineColor", colorToString(coastlineColor));
		root.put("coastlineWidth", coastlineWidth);
		root.put("edgeLandToWaterProbability", edgeLandToWaterProbability);
		root.put("centerLandToWaterProbability", centerLandToWaterProbability);
		if (landShape != null)
		{
			root.put("landShape", landShape.name());
		}
		if (regionCount > 0)
		{
			root.put("regionCount", regionCount);
		}
		root.put("frayedBorder", frayedBorder);
		root.put("frayedBorderColor", colorToString(frayedBorderColor));
		root.put("frayedBorderBlurLevel", frayedBorderBlurLevel);
		root.put("grungeWidth", grungeWidth);
		root.put("drawGrunge", drawGrunge);
		root.put("cityProbability", cityProbability);
		root.put("lineStyle", enumToJson(lineStyle));
		root.put("pointPrecision", pointPrecision);
		root.put("lloydRelaxationsScale", lloydRelaxationsScale);

		// Background settings.
		root.put("backgroundRandomSeed", backgroundRandomSeed);
		root.put("frayedBorderSeed", frayedBorderSeed);
		root.put("generateBackground", generateBackground);
		root.put("backgroundTextureImage", backgroundTextureImage);
		if (backgroundTextureResource != null)
		{
			root.put("backgroundTextureResource", backgroundTextureResource.toJSon());
		}
		root.put("backgroundTextureSource", enumToJson(backgroundTextureSource == null ? TextureSource.Assets : backgroundTextureSource));
		root.put("generateBackgroundFromTexture", generateBackgroundFromTexture);
		root.put("solidColorBackground", solidColorBackground);
		root.put("colorizeOcean", colorizeOcean);
		root.put("colorizeLand", colorizeLand);
		root.put("oceanColor", colorToString(oceanColor));
		root.put("landColor", colorToString(landColor));
		root.put("regionBaseColor", colorToString(regionBaseColor));
		root.put("generatedWidth", generatedWidth);
		root.put("generatedHeight", generatedHeight);

		// Region settings
		root.put("drawRegionColors", drawRegionColors);
		root.put("regionsRandomSeed", regionsRandomSeed);
		root.put("hueRange", hueRange);
		root.put("saturationRange", saturationRange);
		root.put("brightnessRange", brightnessRange);
		root.put("drawRegionBoundaries", drawRegionBoundaries);
		root.put("regionBoundaryStyle", regionBoundaryStyleToJson());
		root.put("regionBoundaryColor", colorToString(regionBoundaryColor));

		// Icons
		root.put("cityIconSetName", cityIconTypeName);

		root.put("drawText", drawText);
		root.put("textRandomSeed", textRandomSeed);

		JSONArray booksArray = new JSONArray();
		for (String book : books)
		{
			booksArray.add(book);
		}
		root.put("books", booksArray);

		root.put("titleFont", fontToString(titleFont));
		root.put("regionFont", fontToString(regionFont));
		root.put("mountainRangeFont", fontToString(mountainRangeFont));
		root.put("otherMountainsFont", fontToString(otherMountainsFont));
		root.put("citiesFont", fontToString(citiesFont));
		root.put("riverFont", fontToString(riverFont));
		root.put("boldBackgroundColor", colorToString(boldBackgroundColor));
		root.put("drawBoldBackground", drawBoldBackground);
		root.put("textColor", colorToString(textColor));

		root.put("drawBorder", drawBorder);
		if (borderResource != null)
		{
			root.put("borderResource", borderResource.toJSon());
		}
		root.put("borderWidth", borderWidth);
		root.put("borderPosition", enumToJson(borderPosition));
		root.put("borderColorOption", enumToJson(borderColorOption));
		root.put("borderColor", colorToString(borderColor));
		root.put("frayedBorderSize", frayedBorderSize);
		root.put("drawRoads", drawRoads);
		root.put("imageExportPath", imageExportPath);
		root.put("heightmapExportPath", heightmapExportPath);
		root.put("heightmapResolution", heightmapResolution);
		root.put("customImagesPath", customImagesPath);
		root.put("artPack", artPack);

		root.put("treeHeightScale", treeHeightScale);
		root.put("mountainScale", mountainScale);
		root.put("hillScale", hillScale);
		root.put("duneScale", duneScale);
		root.put("cityScale", cityScale);
		root.put("defaultMapExportAction", enumToJson(defaultMapExportAction != null ? defaultMapExportAction : defaultDefaultExportAction));
		root.put("defaultHeightmapExportAction", enumToJson(defaultHeightmapExportAction != null ? defaultHeightmapExportAction : defaultDefaultExportAction));

		root.put("drawOverlayImage", drawOverlayImage);
		root.put("overlayImagePath", overlayImagePath);
		root.put("overlayImageTransparency", overlayImageTransparency);
		root.put("overlayScale", overlayScale);
		root.put("overlayOffsetResolutionInvariant", overlayOffsetResolutionInvariant == null ? null : overlayOffsetResolutionInvariant.toJson());

		root.put("rightRotationCount", rightRotationCount);
		root.put("flipHorizontally", flipHorizontally);
		root.put("flipVertically", flipVertically);

		{
			JSONObject iconFillColorsObj = new JSONObject();
			for (Map.Entry<IconType, Color> entry : iconFillColorsByType.entrySet())
			{
				IconType key = entry.getKey();
				Color value = entry.getValue();

				// Only persist the type's fill color when it is actually shown for that type or it differs from the default. Otherwise
				// leave
				// it out so it tracks defaultIconFillColor, mirroring how individual icons are saved. This lets a later change to the
				// default
				// take effect for types that aren't filling with a color.
				if (getFillWithColorForType(key) || !value.equals(defaultIconFillColor))
				{
					iconFillColorsObj.put(key, colorToString(value));
				}
			}
			root.put("iconColorsByType", iconFillColorsObj);
		}

		{
			JSONObject iconFilterColorsObj = new JSONObject();
			for (Map.Entry<IconType, HSBColor> entry : iconFilterColorsByType.entrySet())
			{
				IconType key = entry.getKey();
				HSBColor value = entry.getValue();

				iconFilterColorsObj.put(key, value.toJson());
			}
			root.put("iconFilterColorsByType", iconFilterColorsObj);
		}

		{
			JSONObject maximizeOpacityByTypeObj = new JSONObject();
			for (Map.Entry<IconType, Boolean> entry : maximizeOpacityByType.entrySet())
			{
				IconType key = entry.getKey();
				boolean value = entry.getValue();

				maximizeOpacityByTypeObj.put(key, value);
			}
			root.put("maximizeOpacityByType", maximizeOpacityByTypeObj);
		}

		{
			JSONObject fillWithColorByTypeObj = new JSONObject();
			for (Map.Entry<IconType, Boolean> entry : fillWithColorByType.entrySet())
			{
				IconType key = entry.getKey();
				boolean value = entry.getValue();

				fillWithColorByTypeObj.put(key, value);
			}
			root.put("fillWithColorByType", fillWithColorByTypeObj);
		}


		root.put("drawGridOverlay", drawGridOverlay);
		root.put("gridOverlayShape", enumToJson(gridOverlayShape));
		root.put("gridOverlayRowOrColCount", gridOverlayRowOrColCount);
		root.put("gridOverlayColor", colorToString(gridOverlayColor));
		root.put("gridOverlayXOffset", gridOverlayXOffset.toString());
		root.put("gridOverlayYOffset", gridOverlayYOffset.toString());
		root.put("gridOverlayLineWidth", gridOverlayLineWidth);
		root.put("gridOverlayLayer", enumToJson(gridOverlayLayer));
		root.put("drawVoronoiGridOverlayOnlyOnLand", drawVoronoiGridOverlayOnlyOnLand);

		if (subMapInfo != null)
		{
			root.put("subMapInfo", subMapInfo.toJson());
		}

		// User edits.
		if (edits != null && !skipEdits)
		{
			JSONObject editsJson = new JSONObject();
			root.put("edits", editsJson);
			editsJson.put("textEdits", textEditsToJson());
			editsJson.put("centerEdits", centerEditsToJson());
			editsJson.put("iconEdits", iconsToJson());
			editsJson.put("regionEdits", regionEditsToJson());
			editsJson.put("hasIconEdits", edits.hasIconEdits);
			editsJson.put("roads", roadsToJson());
			editsJson.put("rivers", riversToJson());
			editsJson.put("hasInitializedRivers", edits.hasInitializedRivers);
			// Only write edgeEdits when migration hasn't happened yet (old file being converted). New files have hasInitializedRivers=true
			// and empty edgeEdits, so this key is omitted to keep file size small.
			if (!edits.hasInitializedRivers && !edits.edgeEdits.isEmpty())
			{
				editsJson.put("edgeEdits", edgeEditsToJson());
			}
		}

		return root.toJSONString();
	}

	@SuppressWarnings("unchecked")
	private JSONArray textEditsToJson()
	{
		JSONArray list = new JSONArray();
		for (MapText text : edits.text)
		{
			JSONObject mpObj = new JSONObject();
			mpObj.put("text", text.value);
			mpObj.put("locationX", text.location.x);
			mpObj.put("locationY", text.location.y);
			if (text.angle != 0.0)
			{
				mpObj.put("angle", text.angle);
			}
			mpObj.put("type", enumToJson(text.type));
			if (text.lineBreak != LineBreak.Auto)
			{
				mpObj.put("lineBreak", enumToJson(text.lineBreak));
			}
			if (text.colorOverride != null)
			{
				mpObj.put("colorOverride", colorToString(text.colorOverride));
			}
			if (text.boldBackgroundColorOverride != null)
			{
				mpObj.put("boldBackgroundColorOverride", colorToString(text.boldBackgroundColorOverride));
			}
			if (text.curvature != 0.0)
			{
				mpObj.put("curvature", text.curvature);
			}
			if (text.spacing != 0)
			{
				mpObj.put("spacing", text.spacing);
			}
			if (text.backgroundFade != MapText.defaultBackgroundFade)
			{
				mpObj.put("backgroundFade", text.backgroundFade);
			}
			if (text.fontOverride != null)
			{
				mpObj.put("fontOverride", fontToString(text.fontOverride));
			}
			list.add(mpObj);
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private JSONArray centerEditsToJson()
	{
		JSONArray list = new JSONArray();
		for (CenterEdit centerEdit : edits.centerEdits.values())
		{
			JSONObject mpObj = new JSONObject();
			if (centerEdit.isWater)
			{
				mpObj.put("isWater", centerEdit.isWater);
			}
			if (centerEdit.isLake)
			{
				mpObj.put("isLake", centerEdit.isLake);
			}
			if (centerEdit.regionId != null)
			{
				mpObj.put("regionId", centerEdit.regionId);
			}
			// I'm storing center trees, even though they're mostly only used for adding new trees using editing brushes, because
			// CenterTrees that failed to draw any trees due to their density being too low should be retried when the tree height slider
			// changes, because that changes the density. Without retrying those CenterTrees, trees would slowly disappear off the map as he
			// changed the tree height slighter.
			if (centerEdit.trees != null)
			{
				JSONObject treesObj = new JSONObject();
				treesObj.put("artPack", centerEdit.trees.artPack);
				treesObj.put("treeType", centerEdit.trees.treeType);
				treesObj.put("density", centerEdit.trees.density);
				treesObj.put("randomSeed", centerEdit.trees.randomSeed);
				treesObj.put("isDormant", centerEdit.trees.isDormant);
				// Persist the colors these trees were drawn with so dormant trees reappear with their original color rather than the
				// current per-type tree color. Absent for trees that use the per-type colors (the normal case).
				if (centerEdit.trees.colors != null)
				{
					putIconColors(treesObj, centerEdit.trees.colors);
				}
				mpObj.put("trees", treesObj);
			}
			list.add(mpObj);
		}
		return list;
	}

	/**
	 * Writes the four icon color properties of {@code colors} into {@code obj}. Mirrors {@link #parseIconColors}.
	 */
	@SuppressWarnings("unchecked")
	private void putIconColors(JSONObject obj, IconColors colors)
	{
		if (colors.fillColor != null)
		{
			obj.put("fillColor", colorToString(colors.fillColor));
		}
		if (colors.filterColor != null)
		{
			obj.put("filterColor", colors.filterColor.toJson());
		}
		obj.put("maximizeOpacity", colors.maximizeOpacity);
		obj.put("fillWithColor", colors.fillWithColor);
	}

	/**
	 * Reads the icon color properties written by {@link #putIconColors} from {@code obj}, or returns null if none are present (the normal
	 * case, meaning the per-type colors should be used).
	 */
	private IconColors parseIconColors(JSONObject obj)
	{
		if (obj == null || !(obj.containsKey("fillColor") || obj.containsKey("filterColor") || obj.containsKey("maximizeOpacity") || obj.containsKey("fillWithColor")))
		{
			return null;
		}
		Color fillColor = obj.containsKey("fillColor") ? parseColor((String) obj.get("fillColor")) : null;
		HSBColor filterColor = obj.containsKey("filterColor") ? HSBColor.fromJson((JSONObject) obj.get("filterColor")) : null;
		boolean maximizeOpacity = obj.containsKey("maximizeOpacity") ? (Boolean) obj.get("maximizeOpacity") : false;
		boolean fillWithColor = obj.containsKey("fillWithColor") ? (Boolean) obj.get("fillWithColor") : false;
		return new IconColors(fillColor, filterColor, maximizeOpacity, fillWithColor);
	}

	@SuppressWarnings("unchecked")
	private JSONArray iconsToJson()
	{
		JSONArray list = new JSONArray();
		for (FreeIcon icon : edits.freeIcons)
		{
			JSONObject iconObj = new JSONObject();
			iconObj.put("artPack", icon.artPack);
			iconObj.put("groupId", icon.groupId);
			iconObj.put("iconIndex", icon.iconIndex);
			if (icon.iconName != null)
			{
				iconObj.put("iconName", icon.iconName);
			}
			iconObj.put("type", icon.type.toString());
			iconObj.put("locationResolutionInvariant", icon.locationResolutionInvariant.toJson());
			if (icon.scale != 1.0)
			{
				iconObj.put("scale", icon.scale);
			}
			iconObj.put("centerIndex", icon.centerIndex);
			if (icon.density != 0.0)
			{
				iconObj.put("density", icon.density);
			}
			// Only persist the fill color when it is actually shown (fillWithColor) or it differs from the default. A hidden default color
			// is
			// left out so it tracks defaultIconFillColor, which keeps files small and lets a later change to the default take effect for
			// icons
			// that aren't displaying a color. A shown default color is written explicitly so changing the default won't alter existing
			// maps.
			if (icon.fillColor != null && (icon.fillWithColor || !icon.fillColor.equals(defaultIconFillColor)))
			{
				iconObj.put("color", colorToString(icon.fillColor));
			}
			if (icon.filterColor != null && !icon.filterColor.equals(defaultIconFilterColor))
			{
				iconObj.put("filterColor", icon.filterColor.toJson());
			}
			if (icon.maximizeOpacity)
			{
				iconObj.put("maximizeOpacity", icon.maximizeOpacity);
			}
			if (icon.fillWithColor)
			{
				iconObj.put("fillWithColor", icon.fillWithColor);
			}
			if (icon.originalScale != 1.0)
			{
				iconObj.put("originalScale", icon.originalScale);
			}
			list.add(iconObj);
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private JSONArray roadsToJson()
	{
		JSONArray roadsJson = new JSONArray();
		if (edits.roads == null || edits.roads.isEmpty())
		{
			return roadsJson;
		}

		for (Road road : edits.roads)
		{
			JSONObject roadObj = new JSONObject();

			JSONArray pathJson = new JSONArray();
			if (road.nodes != null)
			{
				for (RoadPathNode node : road.nodes)
				{
					pathJson.add(node.getLoc().toJson());
				}
			}
			roadObj.put("path", pathJson);
			roadsJson.add(roadObj);
		}

		return roadsJson;
	}

	@SuppressWarnings("unchecked")
	private JSONArray riversToJson()
	{
		JSONArray riversJson = new JSONArray();
		if (edits.rivers == null || edits.rivers.isEmpty())
		{
			return riversJson;
		}

		for (River river : edits.rivers)
		{
			JSONObject riverObj = new JSONObject();
			JSONArray nodesJson = new JSONArray();
			if (river.nodes != null)
			{
				for (RiverPathNode node : river.nodes)
				{
					JSONObject nodeJson = new JSONObject();
					nodeJson.put("loc", node.getLoc().toJson());
					nodeJson.put("widthToNext", (long) node.getWidthLevelToNext());
					nodeJson.put("seedToNext", node.getSeedToNext());
					// Persist the Voronoi edge index of the segment leaving this node so polygon-mode
					// rivers stay linked to their region-boundary edge after a save/reload. Omitted when
					// EDGE_INDEX_NONE to keep freehand rivers' JSON unchanged from the prior format.
					if (node.getEdgeIndexToNext() != RiverPathNode.EDGE_INDEX_NONE)
					{
						nodeJson.put("edgeIndexToNext", (long) node.getEdgeIndexToNext());
					}
					// Persist the Voronoi corner a mouth node is anchored to so it still tracks the coast after a
					// save/reload. Omitted when CORNER_INDEX_NONE to keep non-mouth nodes' JSON unchanged.
					if (node.getCornerIndexAnchor() != RiverPathNode.CORNER_INDEX_NONE)
					{
						nodeJson.put("cornerIndexAnchor", (long) node.getCornerIndexAnchor());
					}
					nodesJson.add(nodeJson);
				}
			}
			riverObj.put("nodes", nodesJson);
			riversJson.add(riverObj);
		}

		return riversJson;
	}

	// Reads EdgeEdit.riverLevel, which is the deprecated legacy river storage. This method only runs
	// during legacy-file migration writes (see edits.hasInitializedRivers guard at the call site), so
	// the deprecation warnings here are expected.
	@SuppressWarnings({ "unchecked", "deprecation" })
	private JSONArray edgeEditsToJson()
	{
		JSONArray list = new JSONArray();
		for (EdgeEdit eEdit : edits.edgeEdits.values())
		{
			if (eEdit.riverLevel > GraphRiver.RIVERS_THIS_SIZE_OR_SMALLER_WILL_NOT_BE_DRAWN)
			{
				JSONObject mpObj = new JSONObject();
				mpObj.put("riverLevel", (long) eEdit.riverLevel);
				mpObj.put("index", (long) eEdit.index);
				list.add(mpObj);
			}
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private JSONArray regionEditsToJson()
	{
		JSONArray list = new JSONArray();
		for (RegionEdit regionEdit : edits.regionEdits.values())
		{
			JSONObject mpObj = new JSONObject();
			mpObj.put("color", colorToString(regionEdit.color));
			mpObj.put("regionId", regionEdit.regionId);
			list.add(mpObj);
		}
		return list;
	}

	private JSONObject regionBoundaryStyleToJson()
	{
		return strokeToJson(regionBoundaryStyle);
	}

	private static <E extends Enum<E>> String enumToJson(E value)
	{
		return value.name().replace("_", " ");
	}

	@SuppressWarnings("unchecked")
	private JSONObject strokeToJson(Stroke stroke)
	{
		JSONObject obj = new JSONObject();
		obj.put("type", enumToJson(stroke.type));
		obj.put("width", stroke.width);
		return obj;
	}

	private String colorToString(Color c)
	{
		if (c != null)
		{
			return c.getRed() + "," + c.getGreen() + "," + c.getBlue() + "," + c.getAlpha();
		}
		else
		{
			return "";
		}
	}

	private String fontToString(Font font)
	{
		return font.getName() + "\t" + font.getStyle().value + "\t" + (int) font.getSize();
	}

	private void parseFromJson(String fileContents)
	{
		JSONObject root = null;
		try
		{
			root = (JSONObject) JSONValue.parseWithException(fileContents);
		}
		catch (ParseException e)
		{
			throw new RuntimeException(e);
		}

		version = (String) root.get("version");
		if (isVersionGreaterThanCurrent(version))
		{
			throw new RuntimeException("The map cannot be loaded because it was made in a new version of Nortantis. That map's version is " + version + ", but you're Nortantis version is "
					+ currentVersion + ". Try again with a newer version of Nortantis.");
		}
		randomSeed = (long) root.get("randomSeed");
		resolution = (double) root.get("resolution");
		coastShadingLevel = (int) (long) root.get("coastShadingLevel");

		concentricWaveCount = (int) (long) root.get("concentricWaveCount");
		if (root.containsKey("fadeConcentricWaves"))
		{
			fadeConcentricWaves = (boolean) root.get("fadeConcentricWaves");
		}
		if (root.containsKey("brokenLinesForConcentricWaves"))
		{
			brokenLinesForConcentricWaves = (boolean) root.get("brokenLinesForConcentricWaves");
		}
		if (root.containsKey("jitterToConcentricWaves"))
		{
			jitterToConcentricWaves = (boolean) root.get("jitterToConcentricWaves");
		}
		worldSize = (int) (long) root.get("worldSize");
		riverColor = parseColor((String) root.get("riverColor"));
		if (root.containsKey("roadColor"))
		{
			roadColor = parseColor((String) root.get("roadColor"));
		}
		else
		{
			roadColor = defaultRoadColor;
		}
		if (root.containsKey("roadStyle"))
		{
			roadStyle = parseStroke((JSONObject) root.get("roadStyle"));
		}
		else
		{
			roadStyle = defaultRoadStyle;
		}
		coastShadingColor = parseColor((String) root.get("coastShadingColor"));

		// oceanWavesColor and oceanShadingColor replaced oceanEffectsColor.
		if (root.containsKey("oceanWavesColor") && !((String) root.get("oceanWavesColor")).isEmpty())
		{
			oceanWavesColor = parseColor((String) root.get("oceanWavesColor"));
		}
		else
		{
			oceanWavesColor = parseColor((String) root.get("oceanEffectsColor"));
		}

		if (root.containsKey("oceanShadingColor") && !((String) root.get("oceanShadingColor")).isEmpty())
		{
			oceanShadingColor = parseColor((String) root.get("oceanShadingColor"));
		}
		else
		{
			oceanShadingColor = parseColor((String) root.get("oceanEffectsColor"));
		}

		coastlineColor = parseColor((String) root.get("coastlineColor"));
		if (root.containsKey("coastlineWidth"))
		{
			coastlineWidth = (double) root.get("coastlineWidth");
		}
		else
		{
			coastlineWidth = MapCreator.calcSizeMultiplierFromResolutionScaleRounded(1.0);
		}
		oceanWavesType = OceanWaves.valueOf((String) root.get("oceanEffect"));

		// oceanEffectsLevel was replaced by oceanShadingLevel and oceanWavesLevel, so convert the values here.
		if (root.containsKey("oceanShadingLevel"))
		{
			oceanShadingLevel = (int) (long) root.get("oceanShadingLevel");
		}
		else
		{
			if (oceanWavesType == OceanWaves.Blur)
			{
				oceanShadingLevel = (int) (long) root.get("oceanEffectsLevel");
			}
			else
			{
				oceanShadingLevel = 0;
			}
		}

		if (root.containsKey("oceanWavesLevel"))
		{
			oceanWavesLevel = (int) (long) root.get("oceanWavesLevel");
		}
		else
		{
			if (oceanWavesType != OceanWaves.Blur)
			{
				oceanWavesLevel = (int) (long) root.get("oceanEffectsLevel");
			}
			else
			{
				oceanWavesLevel = 0;
			}
		}

		if (oceanWavesType == OceanWaves.Blur)
		{
			oceanWavesType = OceanWaves.None;
		}

		drawOceanEffectsInLakes = root.containsKey("drawOceanEffectsInLakes") ? (boolean) root.get("drawOceanEffectsInLakes") : false;
		centerLandToWaterProbability = (double) root.get("centerLandToWaterProbability");
		edgeLandToWaterProbability = (double) root.get("edgeLandToWaterProbability");
		if (root.containsKey("landShape"))
		{
			landShape = LandShape.valueOf((String) root.get("landShape"));
		}
		if (root.containsKey("regionCount"))
		{
			regionCount = (int) (long) root.get("regionCount");
		}
		frayedBorder = (boolean) root.get("frayedBorder");
		if (root.containsKey("frayedBorderColor"))
		{
			frayedBorderColor = parseColor((String) root.get("frayedBorderColor"));
		}
		if (root.containsKey("frayedBorderColor"))
		{
			frayedBorderBlurLevel = (int) (long) root.get("frayedBorderBlurLevel");
		}
		grungeWidth = (int) (long) root.get("grungeWidth");
		if (root.containsKey("drawGrunge"))
		{
			drawGrunge = (boolean) root.get("drawGrunge");
		}
		else
		{
			drawGrunge = true;
		}
		cityProbability = (double) root.get("cityProbability");

		String lineStyleString = (String) root.get("lineStyle");
		// Convert old value.
		if (lineStyleString.equals("Smooth"))
		{
			lineStyle = LineStyle.Splines;
		}
		else
		{
			lineStyle = LineStyle.valueOf((String) root.get("lineStyle"));
		}

		pointPrecision = (double) root.get("pointPrecision");
		if (root.containsKey("lloydRelaxationsScale"))
		{
			lloydRelaxationsScale = (double) root.get("lloydRelaxationsScale");
		}
		else
		{
			lloydRelaxationsScale = 0.0;
		}

		// Background image stuff.
		generateBackground = (boolean) root.get("generateBackground");
		generateBackgroundFromTexture = (boolean) root.get("generateBackgroundFromTexture");
		if (root.containsKey("solidColorBackground"))
		{
			solidColorBackground = (boolean) root.get("solidColorBackground");
		}
		else
		{
			solidColorBackground = false;
		}
		colorizeOcean = (boolean) root.get("colorizeOcean");
		colorizeLand = (boolean) root.get("colorizeLand");
		if (root.containsKey("backgroundTextureSource"))
		{
			backgroundTextureSource = Enum.valueOf(TextureSource.class, ((String) root.get("backgroundTextureSource")));
		}
		else
		{
			backgroundTextureSource = TextureSource.File;
		}
		if (root.containsKey("backgroundTextureImage"))
		{
			backgroundTextureImage = (String) root.get("backgroundTextureImage");
		}
		if (root.containsKey("backgroundTextureResource"))
		{
			backgroundTextureResource = NamedResource.fromJson((JSONObject) root.get("backgroundTextureResource"));
		}
		backgroundRandomSeed = (long) root.get("backgroundRandomSeed");
		frayedBorderSeed = root.containsKey("frayedBorderSeed") ? (long) root.get("frayedBorderSeed") : backgroundRandomSeed;
		oceanColor = parseColor((String) root.get("oceanColor"));
		landColor = parseColor((String) root.get("landColor"));

		if (root.containsKey("regionBaseColor") && root.get("regionBaseColor") != null && !((String) root.get("regionBaseColor")).isEmpty())
		{
			regionBaseColor = parseColor((String) root.get("regionBaseColor"));
		}
		else
		{
			regionBaseColor = landColor;
		}

		generatedWidth = (int) (long) root.get("generatedWidth");
		generatedHeight = (int) (long) root.get("generatedHeight");

		drawRegionColors = (boolean) root.get("drawRegionColors");
		regionsRandomSeed = (long) root.get("regionsRandomSeed");
		hueRange = (int) (long) root.get("hueRange");
		saturationRange = (int) (long) root.get("saturationRange");
		brightnessRange = (int) (long) root.get("brightnessRange");
		drawRegionBoundaries = root.containsKey(("drawRegionBoundaries")) ? (boolean) root.get("drawRegionBoundaries") : drawRegionColors;
		regionBoundaryStyle = parseRegionBoundaryStyle((JSONObject) root.get("regionBoundaryStyle"));
		regionBoundaryColor = parseColor((String) root.get("regionBoundaryColor"));
		if (regionBoundaryColor == null)
		{
			regionBoundaryColor = coastlineColor;
		}

		drawRoads = (boolean) root.get("drawRoads");

		if (root.containsKey("cityIconSetName"))
		{
			cityIconTypeName = (String) root.get("cityIconSetName");
			if (cityIconTypeName == null)
			{
				cityIconTypeName = "";
			}
		}
		else
		{
			cityIconTypeName = "";
		}

		drawText = (boolean) root.get("drawText");
		textRandomSeed = (long) root.get("textRandomSeed");

		JSONArray booksArray = (JSONArray) root.get("books");
		books = new TreeSet<String>();
		for (Object bookObject : booksArray)
		{
			String bookName = (String) bookObject;
			books.add(bookName);
		}

		titleFont = parseFont((String) root.get("titleFont"));
		regionFont = parseFont((String) root.get("regionFont"));
		mountainRangeFont = parseFont((String) root.get("mountainRangeFont"));
		otherMountainsFont = parseFont((String) root.get("otherMountainsFont"));
		citiesFont = root.containsKey("citiesFont") ? parseFont((String) root.get("citiesFont")) : otherMountainsFont;
		riverFont = parseFont((String) root.get("riverFont"));

		boldBackgroundColor = parseColor((String) root.get("boldBackgroundColor"));
		drawBoldBackground = (boolean) root.get("drawBoldBackground");

		textColor = parseColor((String) root.get("textColor"));

		drawBorder = (boolean) root.get("drawBorder");
		if (root.containsKey("borderType"))
		{
			borderType = (String) root.get("borderType");
		}
		if (root.containsKey("borderResource"))
		{
			borderResource = NamedResource.fromJson((JSONObject) root.get("borderResource"));
		}

		if (root.containsKey("borderWidth"))
		{
			borderWidth = (int) (long) root.get("borderWidth");
		}
		else
		{
			borderWidth = 0;
		}

		if (root.containsKey("borderPosition"))
		{
			borderPosition = Enum.valueOf(BorderPosition.class, ((String) root.get("borderPosition")).replace(" ", "_"));
			;
		}
		else
		{
			borderPosition = BorderPosition.Outside_map;
		}

		if (root.containsKey("borderColorOption"))
		{
			borderColorOption = Enum.valueOf(BorderColorOption.class, ((String) root.get("borderColorOption")).replace(" ", "_"));
		}
		else
		{
			borderColorOption = BorderColorOption.Ocean_color;
		}

		if (root.containsKey("borderColor"))
		{
			borderColor = parseColor((String) root.get("borderColor"));
		}
		else
		{
			borderColor = landColor;
		}

		frayedBorderSize = (int) (long) root.get("frayedBorderSize");
		if (frayedBorderSize >= 100)
		{
			// Convert from the old format the held the number of the polygons to the new format that uses a small scale.
			// The +1 is just to make sure we don't try to find the log of 0.
			frayedBorderSize = (int) (Math.log((((frayedBorderSize - 100) / 2) + 1)) / Math.log(2));
		}

		imageExportPath = (String) root.get("imageExportPath");
		heightmapExportPath = (String) root.get("heightmapExportPath");
		if (root.containsKey("heightmapResolution"))
		{
			heightmapResolution = (double) root.get("heightmapResolution");
		}

		if (root.containsKey("customImagesPath"))
		{
			customImagesPath = (String) root.get("customImagesPath");
		}

		if (root.containsKey("artPack"))
		{
			artPack = (String) root.get("artPack");
		}
		else
		{
			artPack = StringUtils.isEmpty(customImagesPath) ? Assets.installedArtPack : Assets.customArtPack;
		}

		if (root.containsKey("treeHeightScale"))
		{
			treeHeightScale = (double) root.get("treeHeightScale");
		}
		else
		{
			treeHeightScale = defaultTreeHeightScaleForOldMaps;
		}

		if (root.containsKey("mountainScale"))
		{
			mountainScale = (double) root.get("mountainScale");
		}

		if (root.containsKey("hillScale"))
		{
			hillScale = (double) root.get("hillScale");
		}

		if (root.containsKey("duneScale"))
		{
			duneScale = (double) root.get("duneScale");
		}

		if (root.containsKey("cityScale"))
		{
			cityScale = (double) root.get("cityScale");
		}

		if (root.containsKey("defaultMapExportAction"))
		{
			defaultMapExportAction = ExportAction.valueOf((String) root.get("defaultMapExportAction"));
		}
		else
		{
			defaultMapExportAction = defaultDefaultExportAction;
		}

		if (root.containsKey("defaultHeightmapExportAction"))
		{
			defaultHeightmapExportAction = ExportAction.valueOf((String) root.get("defaultHeightmapExportAction"));
		}
		else
		{
			defaultHeightmapExportAction = defaultDefaultExportAction;
		}

		if (root.containsKey("drawOverlayImage"))
		{
			drawOverlayImage = (boolean) root.get("drawOverlayImage");
			overlayImagePath = (String) root.get("overlayImagePath");
			overlayImageTransparency = (int) (long) root.get("overlayImageTransparency");
			if (root.containsKey("overlayOffsetResolutionInvariant") && root.get("overlayOffsetResolutionInvariant") != null)
			{
				overlayOffsetResolutionInvariant = Point.fromJSonValue((String) root.get("overlayOffsetResolutionInvariant"));
			}
			overlayScale = (double) root.get("overlayScale");
		}
		else
		{
			overlayImageTransparency = overlayImageDefaultTransparency;
			overlayOffsetResolutionInvariant = new Point(0, 0);
			overlayScale = overlayImageDefaultScale;
		}

		if (root.containsKey("rightRotationCount"))
		{
			rightRotationCount = (int) (long) root.get("rightRotationCount");
		}
		else
		{
			rightRotationCount = 0;
		}
		if (root.containsKey(("flipHorizontally")))
		{
			flipHorizontally = (boolean) root.get("flipHorizontally");
		}
		else
		{
			flipHorizontally = false;
		}
		if (root.containsKey(("flipVertically")))
		{
			flipVertically = (boolean) root.get("flipVertically");
		}
		else
		{
			flipVertically = false;
		}

		iconFillColorsByType.clear();
		if (root.containsKey("iconColorsByType"))
		{
			JSONObject mapObj = (JSONObject) root.get("iconColorsByType");
			for (Object key : mapObj.keySet())
			{
				String keyString = (String) key;
				IconType iconType = IconType.valueOf(keyString);
				Color color = parseColor((String) mapObj.get(key));
				iconFillColorsByType.put(iconType, color);
			}
		}
		// Make sure they are all populated with default values.
		for (IconType iconType : IconType.values())
		{
			if (!iconFillColorsByType.containsKey(iconType))
			{
				iconFillColorsByType.put(iconType, defaultIconFillColor);
			}
		}

		iconFilterColorsByType.clear();
		if (root.containsKey("iconFilterColorsByType"))
		{
			JSONObject mapObj = (JSONObject) root.get("iconFilterColorsByType");
			for (Object key : mapObj.keySet())
			{
				String keyString = (String) key;
				IconType iconType = IconType.valueOf(keyString);
				HSBColor color = HSBColor.fromJson((JSONObject) mapObj.get(key));
				iconFilterColorsByType.put(iconType, color);
			}
		}
		// Make sure they are all populated with transparent values.
		for (IconType iconType : IconType.values())
		{
			if (!iconFilterColorsByType.containsKey(iconType))
			{
				iconFilterColorsByType.put(iconType, defaultIconFilterColor);
			}
		}

		maximizeOpacityByType.clear();
		if (root.containsKey("maximizeOpacityByType"))
		{
			JSONObject mapObj = (JSONObject) root.get("maximizeOpacityByType");
			for (Object key : mapObj.keySet())
			{
				String keyString = (String) key;
				IconType iconType = IconType.valueOf(keyString);
				boolean value = (Boolean) mapObj.get(key);
				maximizeOpacityByType.put(iconType, value);
			}
		}
		// Make sure they are all populated with false.
		for (IconType iconType : IconType.values())
		{
			if (!maximizeOpacityByType.containsKey(iconType))
			{
				maximizeOpacityByType.put(iconType, false);
			}
		}

		fillWithColorByType.clear();
		if (root.containsKey("fillWithColorByType"))
		{
			JSONObject mapObj = (JSONObject) root.get("fillWithColorByType");
			for (Object key : mapObj.keySet())
			{
				String keyString = (String) key;
				IconType iconType = IconType.valueOf(keyString);
				boolean value = (Boolean) mapObj.get(key);
				fillWithColorByType.put(iconType, value);
			}
		}
		// Make sure they are all populated with false.
		for (IconType iconType : IconType.values())
		{
			if (!fillWithColorByType.containsKey(iconType))
			{
				fillWithColorByType.put(iconType, false);
			}
		}


		if (root.containsKey("drawGridOverlay"))
		{
			drawGridOverlay = (boolean) root.get("drawGridOverlay");
			gridOverlayShape = Enum.valueOf(GridOverlayShape.class, ((String) root.get("gridOverlayShape")).replace(" ", "_"));
			gridOverlayColor = parseColor((String) root.get("gridOverlayColor"));
			gridOverlayRowOrColCount = (int) (long) root.get("gridOverlayRowOrColCount");
			gridOverlayXOffset = GridOverlayOffset.parse((String) root.get("gridOverlayXOffset"));
			gridOverlayYOffset = GridOverlayOffset.parse((String) root.get("gridOverlayYOffset"));
			gridOverlayLineWidth = (int) (long) root.get("gridOverlayLineWidth");
			gridOverlayLayer = Enum.valueOf(GridOverlayLayer.class, ((String) root.get("gridOverlayLayer")).replace(" ", "_"));
			if (root.containsKey("drawVoronoiGridOverlayOnlyOnLand"))
			{
				drawVoronoiGridOverlayOnlyOnLand = (boolean) root.get("drawVoronoiGridOverlayOnlyOnLand");
			}
		}

		if (root.containsKey("subMapInfo"))
		{
			subMapInfo = SubMapInfo.fromJson((JSONObject) root.get("subMapInfo"));
		}

		edits = new MapEdits();
		// hiddenTextIds is a comma-delimited list.

		boolean hasCustomImagesPath = !StringUtils.isEmpty(customImagesPath);
		JSONObject editsJson = (JSONObject) root.get("edits");
		edits.text = parseMapTexts(editsJson);
		edits.freeIcons = parseIconEdits(editsJson, hasCustomImagesPath);
		edits.centerEdits = parseCenterEdits(editsJson, hasCustomImagesPath);
		edits.regionEdits = parseRegionEdits(editsJson);
		edits.edgeEdits = parseEdgeEdits(editsJson);
		edits.hasIconEdits = (boolean) editsJson.get("hasIconEdits");
		edits.roads = parseRoads(editsJson);
		edits.rivers = parseRivers(editsJson);
		edits.hasInitializedRivers = editsJson.containsKey("hasInitializedRivers") && (boolean) editsJson.get("hasInitializedRivers");

		runConversionForShadingAlphaChange();
		runConversionForAllowingMultipleCityTypesInOneMap();
		runConversionToFixDunesGroupId();
		runConversionOnBackgroundTextureImagePaths();
		runConversionOnBorderType();
		runConversionToRemoveTrailingSpacesInImageNamesWithWidth();
		runConversionOnFadingConcentricWaves();
		runConversionToRemoveRegionIdsOfEditsThatAreWater();
		runConversionForNewRangesForRandomRegionColorGeneratorSettings();
		runConversionToFixCompassRosesGroupId();
		runConversionToFillInLandShape();
		runConversionForRegionCount();
		runConversionForIconFillColorDefaultChange();
		runConversionOnFillWithColorByType();
	}

	/**
	 * defaultIconFillColor changed in version 3.2. Per-type fill colors are stored explicitly for every type in older maps, so without this
	 * a type that was left at the old default would stay frozen at it. Release any per-type fill color that was at the old default and
	 * isn't being shown so it tracks the new default. Types the user actually colored, or that have fill enabled, are left untouched.
	 * Individual icons are handled inline in parseIconEdits.
	 */
	private void runConversionForIconFillColorDefaultChange()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.2"))
		{
			return;
		}

		for (IconType iconType : IconType.values())
		{
			if (!getFillWithColorForType(iconType) && defaultIconFillColorBeforeV3_2.equals(iconFillColorsByType.get(iconType)))
			{
				iconFillColorsByType.put(iconType, defaultIconFillColor);
			}
		}
	}

	private void runConversionForRegionCount()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.18"))
		{
			return;
		}

		if (regionCount > 0)
		{
			return;
		}

		Set<Integer> regionCounts = new HashSet<>();
		for (CenterEdit cEdit : edits.centerEdits.values())
		{
			if (!cEdit.isWater)
			{
				regionCounts.add(cEdit.regionId);
			}
		}
		regionCount = Math.min(SettingsGenerator.maxRegionCount, Math.max(2, regionCounts.size()));
	}


	/**
	 * LandShape was added in version 3.18. For older maps, infer it from the edge and center land-to-water probabilities that were
	 * previously used.
	 */
	private void runConversionToFillInLandShape()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.18"))
		{
			return;
		}

		if (landShape != null)
		{
			return;
		}

		if (edgeLandToWaterProbability < centerLandToWaterProbability)
		{
			landShape = LandShape.Continents;
		}
		else if (edgeLandToWaterProbability > centerLandToWaterProbability)
		{
			landShape = LandShape.Inland_Sea;
		}
		else
		{
			landShape = LandShape.Scattered;
		}
	}

	private void runConversionOnFillWithColorByType()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.2"))
		{
			return;
		}

		boolean convertFillColor = shouldConvertFillColor();
		if (convertFillColor)
		{
			for (IconType iconType : IconType.values())
			{
				if (iconFillColorsByType.containsKey(iconType))
				{
					Color color = iconFillColorsByType.get(iconType);
					// This conversion only runs for maps old enough (<= 3.14) to predate per-type fill colors, so every type's color here
					// was
					// just populated with the current defaultIconFillColor above (these files have no stored iconColorsByType). Compare
					// against
					// that same current default so a populated default is correctly read as "not filling" - otherwise every icon would fill
					// with the default color. transparentBlack was the even-older "no fill" sentinel.
					fillWithColorByType.put(iconType, !color.equals(Color.transparentBlack) && !color.equals(defaultIconFillColor));

					if (iconFillColorsByType.get(iconType).equals(Color.transparentBlack))
					{
						iconFillColorsByType.put(iconType, defaultIconFillColor);
					}
				}
			}
		}
	}

	/**
	 * This conversion is needed because I renamed the decorations folder "compasses" to "compass roses" to be more correct.
	 */
	private void runConversionToFixCompassRosesGroupId()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.17"))
		{
			return;
		}

		if (edits == null || edits.freeIcons == null)
		{
			return;
		}

		Map<String, ImageAndMasks> imagesInGroup = ImageCache.getInstance(Assets.installedArtPack, customImagesPath).getIconsByNameForGroup(IconType.decorations, "compass roses");
		if (imagesInGroup == null || imagesInGroup.isEmpty())
		{
			return;
		}
		List<String> compassRoseNames = new ArrayList<>(imagesInGroup.keySet());

		List<FreeIcon> toReplace = new ArrayList<>();
		for (FreeIcon icon : edits.freeIcons)
		{
			if (icon != null && Assets.installedArtPack.equals(icon.artPack) && "compasses".equals(icon.groupId))
			{
				toReplace.add(icon);
			}
		}

		for (FreeIcon icon : toReplace)
		{
			if (icon.iconName == null)
			{
				// Should never happen unless the nort file is corrupted.
				continue;
			}
			String nameToUse = compassRoseNames.get(Helper.safeAbs(icon.iconName.hashCode()) % compassRoseNames.size());
			edits.freeIcons.replace(icon, icon.copyWithGroupId("compass roses").copyWithName(nameToUse));
		}
	}

	private void runConversionForNewRangesForRandomRegionColorGeneratorSettings()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.16"))
		{
			return;
		}

		saturationRange = (int) Math.round(saturationRange * 100.0 / 255.0);
		brightnessRange = (int) Math.round(brightnessRange * 100.0 / 255.0);
	}

	/**
	 * Fixes the aftermath of an issue where the Land and Water tool wasn't clearing region IDs when drawing ocean and lakes.
	 */
	private void runConversionToRemoveRegionIdsOfEditsThatAreWater()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.04"))
		{
			return;
		}

		for (CenterEdit cEdit : edits.centerEdits.values())
		{
			if ((cEdit.isWater || cEdit.isLake) && cEdit.regionId != null)
			{
				edits.centerEdits.put(cEdit.index, cEdit.copyWithRegionId(null));
			}
		}
	}

	private void runConversionOnFadingConcentricWaves()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.04"))
		{
			return;
		}

		if (oceanWavesType == OceanWaves.FadingConcentricWaves)
		{
			oceanWavesType = OceanWaves.ConcentricWaves;
			fadeConcentricWaves = true;
		}
	}

	private void runConversionToRemoveTrailingSpacesInImageNamesWithWidth()
	{
		if (isVersionGreaterThanOrEqualTo(version, "3.01"))
		{
			return;
		}

		for (FreeIcon icon : edits.freeIcons)
		{
			String trimmed = StringHelper.trimTrailingSpacesAndUnderscores(icon.iconName);
			if (!Objects.equals(trimmed, icon.iconName))
			{
				edits.freeIcons.replace(icon, icon.copyWithName(trimmed));
			}
		}

		for (Entry<Integer, CenterEdit> entry : edits.centerEdits.entrySet())
		{
			CenterEdit cEdit = entry.getValue();
			if (cEdit.icon != null && !StringUtils.isEmpty(cEdit.icon.iconName))
			{
				String trimmed = StringHelper.trimTrailingSpacesAndUnderscores(cEdit.icon.iconName);
				if (!Objects.equals(trimmed, cEdit.icon.iconName))
				{
					edits.centerEdits.put(entry.getKey(), cEdit.copyWithIcon(cEdit.icon.copyWithIconName(trimmed)));
				}
			}
		}
	}

	/**
	 * Move the border type to the new field so it can support art packs.
	 */
	private void runConversionOnBorderType()
	{
		if (isVersionGreaterThanOrEqualTo(version, "2.91"))
		{
			return;
		}

		if (!StringUtils.isEmpty(borderType))
		{
			borderResource = new NamedResource(StringUtils.isEmpty(customImagesPath) ? Assets.installedArtPack : Assets.customArtPack, borderType);
			borderType = null;
		}
	}

	/**
	 * Convert background texture image to a resource if possible.
	 */
	private void runConversionOnBackgroundTextureImagePaths()
	{
		if (isVersionGreaterThanOrEqualTo(version, "2.91"))
		{
			return;
		}

		if (!OSHelper.isLinux() && !OSHelper.isWindows())
		{
			return;
		}

		if (backgroundTextureImage != null && !backgroundTextureImage.isEmpty())
		{
			// It should be absolute.
			if (new File(backgroundTextureImage).isAbsolute())
			{
				String oldExampleTexturesInstalledPath;
				if (OSHelper.isLinux())
				{
					oldExampleTexturesInstalledPath = "/opt/nortantis/lib/app/assets/example textures";
				}
				else
				{
					// Windows
					oldExampleTexturesInstalledPath = "C:\\Program Files\\Nortantis\\app\\assets\\example textures";
				}

				// This path only needs checked for maps that were created when running from source, such as my unit test maps.
				String oldExampleTexturesRunningFromSourcePath = Paths.get("assets", "example textures").toAbsolutePath().toString();

				if (backgroundTextureImage.startsWith(oldExampleTexturesInstalledPath) || backgroundTextureImage.startsWith(oldExampleTexturesRunningFromSourcePath))
				{
					backgroundTextureResource = new NamedResource(Assets.installedArtPack, FilenameUtils.getName(backgroundTextureImage));
					backgroundTextureSource = TextureSource.Assets;
				}
				else
				{
					backgroundTextureSource = TextureSource.File;
				}
			}
			else
			{
				backgroundTextureSource = TextureSource.File;
			}
		}
		else
		{
			backgroundTextureSource = TextureSource.Assets;
		}
	}

	/**
	 * Previous versions incorrectly used the group id "sand" for the "dunes" group, which didn't matter because previously I didn't allow
	 * multiple groups of sand dune images and the value was ignored. But now I do allow multiple sand dune image groups, so this fixes
	 * that.
	 */
	private void runConversionToFixDunesGroupId()
	{
		if (isVersionGreaterThanOrEqualTo(version, "2.4"))
		{
			return;
		}

		if (edits == null || edits.centerEdits == null)
		{
			return;
		}

		for (CenterEdit cEdit : edits.centerEdits.values())
		{
			if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Dune)
			{
				edits.centerEdits.put(cEdit.index, cEdit.copyWithIcon(cEdit.icon.copyWithIconGroupId("dunes")));
			}
		}
	}

	private void runConversionForAllowingMultipleCityTypesInOneMap()
	{
		if (isVersionGreaterThanOrEqualTo(version, "2.2"))
		{
			return;
		}

		if (edits == null || edits.centerEdits == null)
		{
			return;
		}

		for (CenterEdit cEdit : edits.centerEdits.values())
		{
			if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.City)
			{
				edits.centerEdits.put(cEdit.index, cEdit.copyWithIcon(cEdit.icon.copyWithIconGroupId(cityIconTypeName)));
			}
		}
	}

	/**
	 * Convert old map settings to compensate for a change I introduced to the level at which shading is darkened.
	 */
	private void runConversionForShadingAlphaChange()
	{
		if (isVersionGreaterThanOrEqualTo(version, "2.0"))
		{
			return;
		}

		if (coastShadingColor.getAlpha() == 255)
		{
			coastShadingColor = Color.create(coastShadingColor.getRed(), coastShadingColor.getGreen(), coastShadingColor.getBlue(), SettingsGenerator.defaultCoastShadingAlpha);
		}

		if (oceanShadingColor.getAlpha() == 255)
		{
			oceanShadingColor = Color.create(oceanShadingColor.getRed(), oceanShadingColor.getGreen(), oceanShadingColor.getBlue(), SettingsGenerator.defaultOceanShadingAlpha);
		}

		if (oceanWavesType == OceanWaves.Ripples && oceanWavesColor.getAlpha() == 255)
		{
			oceanWavesColor = Color.create(oceanWavesColor.getRed(), oceanWavesColor.getGreen(), oceanWavesColor.getBlue(), SettingsGenerator.defaultOceanRipplesAlpha);
		}
	}

	private CopyOnWriteArrayList<MapText> parseMapTexts(JSONObject editsJson)
	{
		if (editsJson == null)
		{
			return new CopyOnWriteArrayList<>();
		}

		JSONArray array = (JSONArray) editsJson.get("textEdits");
		CopyOnWriteArrayList<MapText> result = new CopyOnWriteArrayList<>();
		for (Object obj : array)
		{
			JSONObject jsonObj = (JSONObject) obj;
			String text = (String) jsonObj.get("text");
			Point location = new Point((Double) jsonObj.get("locationX"), (Double) jsonObj.get("locationY"));
			double angle = jsonObj.containsKey("angle") ? (Double) jsonObj.get("angle") : 0.0;
			TextType type = Enum.valueOf(TextType.class, ((String) jsonObj.get("type")).replace(" ", "_"));
			LineBreak lineBreak = jsonObj.containsKey("lineBreak") ? Enum.valueOf(LineBreak.class, ((String) jsonObj.get("lineBreak")).replace(" ", "_")) : LineBreak.Auto;
			Color colorOverride = jsonObj.containsKey("colorOverride") ? parseColor((String) jsonObj.get("colorOverride")) : null;
			Color boldBackgroundColorOverride = jsonObj.containsKey("boldBackgroundColorOverride") ? parseColor((String) jsonObj.get("boldBackgroundColorOverride")) : null;
			double curvature = jsonObj.containsKey("curvature") ? (Double) jsonObj.get("curvature") : 0.0;
			int spacing = jsonObj.containsKey("spacing") ? (int) (long) jsonObj.get("spacing") : 0;
			Font fontOverride = jsonObj.containsKey("fontOverride") ? parseFont((String) jsonObj.get("fontOverride")) : null;
			double backgroundFade = jsonObj.containsKey("backgroundFade") ? (Double) jsonObj.get("backgroundFade") : MapText.defaultBackgroundFade;
			MapText mp = new MapText(text, location, angle, type, lineBreak, colorOverride, boldBackgroundColorOverride, curvature, spacing, fontOverride, backgroundFade);
			result.add(mp);
		}

		return result;
	}

	private ConcurrentHashMap<Integer, CenterEdit> parseCenterEdits(JSONObject editsJson, boolean hasCustomImagesPath)
	{
		if (editsJson == null)
		{
			return new ConcurrentHashMap<>();
		}

		JSONArray array = (JSONArray) editsJson.get("centerEdits");
		ConcurrentHashMap<Integer, CenterEdit> result = new ConcurrentHashMap<>();
		if (array == null)
		{
			return result;
		}
		int index = 0;
		for (Object obj : array)
		{
			JSONObject jsonObj = (JSONObject) obj;
			boolean isWater = jsonObj.containsKey("isWater") ? (boolean) jsonObj.get("isWater") : false;
			boolean isLake = jsonObj.containsKey("isLake") ? (boolean) jsonObj.get("isLake") : false;
			Integer regionId = jsonObj.get("regionId") == null ? null : ((Long) jsonObj.get("regionId")).intValue();

			CenterIcon icon = null;
			{
				JSONObject iconObj = (JSONObject) jsonObj.get("icon");
				if (iconObj != null)
				{
					String artPack;
					if (iconObj.containsKey("artPack"))
					{
						artPack = (String) iconObj.get("artPack");
					}
					else
					{
						// Map versions before art packs either use the installed images or accustom images folder.
						artPack = hasCustomImagesPath ? Assets.customArtPack : Assets.installedArtPack;
					}
					String iconGroupId = (String) iconObj.get("iconGroupId");
					int iconIndex = (int) (long) iconObj.get("iconIndex");
					CenterIconType iconType = CenterIconType.valueOf((String) iconObj.get("iconType"));
					String iconName = (String) iconObj.get("iconName");
					if (iconName != null && !iconName.isEmpty())
					{
						icon = new CenterIcon(iconType, artPack, iconGroupId, iconName);
					}
					else
					{
						icon = new CenterIcon(iconType, artPack, iconGroupId, iconIndex);
					}
				}
			}

			CenterTrees trees = null;
			{
				JSONObject treesObj = (JSONObject) jsonObj.get("trees");
				if (treesObj != null)
				{
					String artPack;
					if (treesObj.containsKey("artPack"))
					{
						artPack = (String) treesObj.get("artPack");
					}
					else
					{
						// Map versions before art packs either use the installed images or accustom images folder.
						artPack = hasCustomImagesPath ? Assets.customArtPack : Assets.installedArtPack;
					}
					String treeType = (String) treesObj.get("treeType");
					double density = (Double) treesObj.get("density");
					long randomSeed = (Long) treesObj.get("randomSeed");
					boolean isDormant = treesObj.containsKey("isDormant") ? (Boolean) treesObj.get("isDormant") : false;
					IconColors colors = parseIconColors(treesObj);
					trees = new CenterTrees(artPack, treeType, density, randomSeed, isDormant, colors);
				}
			}

			result.put(index, new CenterEdit(index, isWater, isLake, regionId, icon, trees));
			index++;
		}

		return result;
	}

	private FreeIconCollection parseIconEdits(JSONObject editsJson, boolean hasCustomImagesPath)
	{
		if (editsJson == null)
		{
			return new FreeIconCollection();
		}

		JSONArray array = (JSONArray) editsJson.get("iconEdits");
		FreeIconCollection result = new FreeIconCollection();
		if (array == null)
		{
			return result;
		}

		boolean convertFillColor = shouldConvertFillColor();

		for (Object obj : array)
		{
			JSONObject iconObj = (JSONObject) obj;
			IconType type = IconType.valueOf((String) iconObj.get("type"));

			String artPack;
			if (iconObj.containsKey("artPack"))
			{
				artPack = (String) iconObj.get("artPack");
			}
			else
			{
				// Map versions before art packs either use the installed images or accustom images folder.
				artPack = hasCustomImagesPath ? Assets.customArtPack : Assets.installedArtPack;
			}
			String groupId = (String) iconObj.get("groupId");
			int iconIndex = (int) (long) iconObj.get("iconIndex");
			String iconName = iconObj.containsKey("iconName") ? (String) iconObj.get("iconName") : null;
			Point locationResolutionInvariant = Point.fromJSonValue((String) iconObj.get("locationResolutionInvariant"));
			double scale = iconObj.containsKey("scale") ? (double) iconObj.get("scale") : 1.0;
			Integer centerIndex = null;
			if (iconObj.containsKey("centerIndex") && iconObj.get("centerIndex") != null)
			{
				centerIndex = (int) (long) iconObj.get("centerIndex");
			}
			double density = iconObj.containsKey("density") ? (double) iconObj.get("density") : 0.0;
			Color fillColorFromJSon = iconObj.containsKey("color") ? parseColor((String) iconObj.get("color")) : null;

			boolean fillWithColor;
			if (convertFillColor)
			{
				fillWithColor = fillColorFromJSon != null && !Color.transparentBlack.equals(fillColorFromJSon);
			}
			else
			{
				fillWithColor = iconObj.containsKey("fillWithColor") ? (Boolean) iconObj.get("fillWithColor") : false;
			}

			Color fillColor;
			if (fillColorFromJSon != null)
			{
				fillColor = fillColorFromJSon;
			}
			else if (fillWithColor && !isVersionGreaterThanOrEqualTo(version, "3.2"))
			{
				// The color was omitted, which means it equaled the default when the file was saved. For maps saved before 3.2 (when the
				// default fill color changed) a shown color was displaying the old default, so resolve it back to that to preserve the
				// icon's
				// appearance. A hidden omitted color falls through to the current default below so it tracks the new default.
				fillColor = defaultIconFillColorBeforeV3_2;
			}
			else
			{
				fillColor = defaultIconFillColor;
			}

			HSBColor filterColor = iconObj.containsKey("filterColor") ? HSBColor.fromJson((JSONObject) iconObj.get("filterColor")) : defaultIconFilterColor;
			boolean maximizeOpacity = iconObj.containsKey("maximizeOpacity") ? (Boolean) iconObj.get("maximizeOpacity") : false;

			double originalScale;
			if (iconObj.containsKey("originalScale") && iconObj.get("originalScale") != null)
			{
				originalScale = (double) iconObj.get("originalScale");
			}
			else
			{
				if (isVersionGreaterThan(version, "3.14"))
				{
					originalScale = 1.0;
				}
				else
				{
					// Older maps don't have this setting, so guess at what it should be.
					if (type == IconType.mountains || type == IconType.hills)
					{
						originalScale = scale;
					}
					else
					{
						originalScale = 1.0;
					}
				}
			}

			result.addOrReplace(new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, fillColor, filterColor, maximizeOpacity,
					fillWithColor, originalScale));
		}

		return result;
	}

	private boolean shouldConvertFillColor()
	{
		return !isVersionGreaterThan(version, "3.14");
	}

	private CopyOnWriteArrayList<Road> parseRoads(JSONObject editsJson)
	{
		CopyOnWriteArrayList<Road> roads = new CopyOnWriteArrayList<>();

		if (!editsJson.containsKey("roads"))
		{
			return roads;
		}

		JSONArray list = (JSONArray) editsJson.get("roads");
		for (Object obj : list)
		{
			JSONObject roadJson = (JSONObject) obj;
			List<Point> path = new ArrayList<Point>();
			if (roadJson.containsKey("path"))
			{
				for (Object obj2 : (JSONArray) roadJson.get("path"))
				{
					String pointString = (String) obj2;
					path.add(Point.fromJSonValue(pointString));
				}
			}
			roads.add(Road.fromLocations(path));
		}
		return roads;
	}

	private CopyOnWriteArrayList<River> parseRivers(JSONObject editsJson)
	{
		CopyOnWriteArrayList<River> rivers = new CopyOnWriteArrayList<>();

		if (!editsJson.containsKey("rivers"))
		{
			return rivers;
		}

		JSONArray list = (JSONArray) editsJson.get("rivers");
		for (Object obj : list)
		{
			JSONObject riverJson = (JSONObject) obj;
			List<RiverPathNode> nodes = new ArrayList<>();
			if (riverJson.containsKey("nodes"))
			{
				for (Object obj2 : (JSONArray) riverJson.get("nodes"))
				{
					JSONObject nodeJson = (JSONObject) obj2;
					Point loc = Point.fromJSonValue((String) nodeJson.get("loc"));
					int widthToNext = nodeJson.containsKey("widthToNext") ? (int) (long) nodeJson.get("widthToNext") : 0;
					long seedToNext = nodeJson.containsKey("seedToNext") ? (long) nodeJson.get("seedToNext") : 0L;
					int edgeIndexToNext = nodeJson.containsKey("edgeIndexToNext") ? (int) (long) nodeJson.get("edgeIndexToNext") : RiverPathNode.EDGE_INDEX_NONE;
					int cornerIndexAnchor = nodeJson.containsKey("cornerIndexAnchor") ? (int) (long) nodeJson.get("cornerIndexAnchor") : RiverPathNode.CORNER_INDEX_NONE;
					nodes.add(new RiverPathNode(loc, widthToNext, seedToNext, edgeIndexToNext, cornerIndexAnchor));
				}
			}
			rivers.add(new River(nodes));
		}
		return rivers;
	}

	private ConcurrentHashMap<Integer, RegionEdit> parseRegionEdits(JSONObject editsJson)
	{
		if (editsJson == null)
		{
			return new ConcurrentHashMap<>();
		}
		JSONArray array = (JSONArray) editsJson.get("regionEdits");
		ConcurrentHashMap<Integer, RegionEdit> result = new ConcurrentHashMap<>();
		for (Object obj : array)
		{
			JSONObject jsonObj = (JSONObject) obj;
			Color color = parseColor((String) jsonObj.get("color"));
			int regionId = (int) (long) jsonObj.get("regionId");
			result.put(regionId, new RegionEdit(regionId, color));
		}

		return result;
	}

	private Stroke parseRegionBoundaryStyle(JSONObject obj)
	{
		Stroke parsed = parseStroke(obj);
		if (obj == null)
		{
			return new Stroke(StrokeType.Solid, (float) (MapCreator.calcSizeMultiplierFromResolutionScaleRounded(1.0)));
		}

		return parsed;
	}

	private Stroke parseStroke(JSONObject obj)
	{
		if (obj == null)
		{
			return null;
		}

		StrokeType type = Enum.valueOf(StrokeType.class, ((String) obj.get("type")).replace(" ", "_"));
		float width = (float) (double) obj.get("width");
		return new Stroke(type, width);
	}

	private Map<Integer, EdgeEdit> parseEdgeEdits(JSONObject editsJson)
	{
		if (editsJson == null)
		{
			return new TreeMap<>();
		}
		JSONArray array = (JSONArray) editsJson.get("edgeEdits");
		Map<Integer, EdgeEdit> result = new TreeMap<>();
		if (array == null)
		{
			return result;
		}
		for (Object obj : array)
		{
			JSONObject jsonObj = (JSONObject) obj;
			int riverLevel = 0;
			if (jsonObj.containsKey("riverLevel"))
			{
				riverLevel = (int) (long) jsonObj.get("riverLevel");
			}
			if (riverLevel <= GraphRiver.RIVERS_THIS_SIZE_OR_SMALLER_WILL_NOT_BE_DRAWN)
			{
				continue;
			}
			int index = (int) (long) jsonObj.get("index");
			result.put(index, new EdgeEdit(index, riverLevel));
		}

		return result;
	}

	private static Color parseColor(String str)
	{
		if (str == null || str.isEmpty())
		{
			return null;
		}
		String[] parts = str.split(",");
		if (parts.length == 3)
		{
			return Color.create(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		if (parts.length == 4)
		{
			return Color.create(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
		}
		throw new IllegalArgumentException("Unable to parse color from string: " + str);
	}

	public static Font parseFont(String str)
	{
		String[] parts = str.split("\t");
		if (parts.length != 3)
		{
			throw new IllegalArgumentException("Unable to parse the value of the font: \"" + str + "\"");
		}
		Font font = Font.create(parts[0], FontStyle.fromNumber(Integer.parseInt(parts[1])), Integer.parseInt(parts[2]));
		if (!Font.isInstalled(font.getName()))
		{
			if (Font.isInstalled("Gabriola"))
			{
				// Windows has this font
				font = Font.create("Gabriola", FontStyle.fromNumber(Integer.parseInt(parts[1])), Integer.parseInt(parts[2]));
			}
			else if (Font.isInstalled("Z003"))
			{
				// Ubuntu has this font
				font = Font.create("Z003", FontStyle.fromNumber(Integer.parseInt(parts[1])), Integer.parseInt(parts[2]));
			}
			else if (Font.isInstalled("Apple Chancery"))
			{
				// Mac has this font
				font = Font.create("Apple Chancery", FontStyle.fromNumber(Integer.parseInt(parts[1])), Integer.parseInt(parts[2]));
			}
			else
			{
				// Generic fallback - use Serif logical font name, which
				// each platform maps to an available serif font.
				font = Font.create("Serif", FontStyle.fromNumber(Integer.parseInt(parts[1])), Integer.parseInt(parts[2]));
			}
		}
		return font;
	}

	private void loadFromOldPropertiesFile(String propertiesFilePath)
	{
		OldPropertyBasedMapSettings old = new OldPropertyBasedMapSettings(propertiesFilePath);
		version = "0.0";
		randomSeed = old.randomSeed;
		resolution = old.resolution;
		artPack = Assets.installedArtPack;
		oceanEffectsLevel = old.oceanEffectsLevel;
		concentricWaveCount = old.concentricWaveCount;
		oceanWavesType = old.oceanEffect;
		worldSize = old.worldSize;
		riverColor = old.riverColor;
		roadColor = defaultRoadColor;
		roadStyle = defaultRoadStyle;
		coastShadingColor = old.coastShadingColor;
		coastShadingLevel = old.coastShadingLevel;
		oceanEffectsColor = old.oceanEffectsColor;
		coastlineColor = old.coastlineColor;
		coastlineWidth = MapCreator.calcSizeMultiplierFromResolutionScaleRounded(1.0);
		centerLandToWaterProbability = old.centerLandToWaterProbability;
		edgeLandToWaterProbability = old.edgeLandToWaterProbability;
		frayedBorder = old.frayedBorder;
		frayedBorderColor = old.frayedBorderColor;
		frayedBorderBlurLevel = old.frayedBorderBlurLevel;
		grungeWidth = old.grungeWidth;
		drawGrunge = true;
		generateBackground = old.generateBackground;
		generateBackgroundFromTexture = old.generateBackgroundFromTexture;
		solidColorBackground = false;
		colorizeOcean = old.colorizeOcean;
		colorizeLand = old.colorizeLand;
		backgroundTextureImage = old.backgroundTextureImage;
		backgroundRandomSeed = old.backgroundRandomSeed;
		frayedBorderSeed = old.backgroundRandomSeed;
		oceanColor = old.oceanColor;
		landColor = old.landColor;
		generatedWidth = old.generatedWidth;
		generatedHeight = old.generatedHeight;
		hueRange = old.hueRange;
		saturationRange = old.saturationRange;
		brightnessRange = old.brightnessRange;
		drawText = old.drawText;
		textRandomSeed = old.textRandomSeed;
		books = old.books;
		titleFont = old.titleFont;
		regionFont = old.regionFont;
		mountainRangeFont = old.mountainRangeFont;
		otherMountainsFont = old.otherMountainsFont;
		citiesFont = old.otherMountainsFont;
		riverFont = old.riverFont;
		boldBackgroundColor = old.boldBackgroundColor;
		textColor = old.textColor;
		drawBoldBackground = old.drawBoldBackground;
		drawRegionColors = old.drawRegionColors;
		drawRegionBoundaries = old.drawRegionColors;
		regionBoundaryStyle = parseRegionBoundaryStyle(null);
		regionBoundaryColor = coastlineColor;
		regionsRandomSeed = old.regionsRandomSeed;
		drawBorder = old.drawBorder;
		borderType = old.borderType;
		borderWidth = old.borderWidth;
		borderPosition = BorderPosition.Outside_map;
		borderColorOption = BorderColorOption.Ocean_color;
		borderColor = landColor;
		frayedBorderSize = old.frayedBorderSize;
		drawRoads = old.drawRoads;
		cityProbability = old.cityProbability;
		lineStyle = old.lineStyle;
		cityIconTypeName = old.cityIconSetName;
		pointPrecision = old.pointPrecision;
		lloydRelaxationsScale = 0.0;
		edits = old.edits;
		treeHeightScale = defaultTreeHeightScaleForOldMaps;

		// Convert the settings to json and back to an object to pick up any conversions added in the json parse.
		String json = toJson();

		// Clear out deprecated properties to make sure I don't accidentally use them, and to make my unit test for this code succeed.
		// Note that I could instead create the "json" object above by assigning the OldPropertyBasedMapSettings to a new object to avoid
		// this, but that would then require me to create new loading code for any deprecated properties in OldPropertyBasedMapSettings if a
		// conversion uses the old value in parseFromJson.
		oceanEffectsColor = null;
		oceanEffectsLevel = 0;

		try
		{
			parseFromJson(json);
		}
		catch (Exception e)
		{
			Logger.println("Error while parsing json in conversion. JSON: " + json);
			throw e;
		}
	}

	public static boolean isVersionGreaterThanCurrent(String version)
	{
		return isVersionGreaterThan(version, currentVersion);
	}

	public static boolean isVersionGreaterThan(String version1, String version2)
	{
		if (version1 == null || version1.isEmpty())
		{
			return false;
		}
		if (version2 == null || version2.isEmpty())
		{
			return true;
		}
		return Double.parseDouble(version1) > Double.parseDouble(version2);
	}

	private boolean isVersionGreaterThanOrEqualTo(String version1, String version2)
	{
		if (Objects.equals(version1, version2))
		{
			return true;
		}

		return isVersionGreaterThan(version1, version2);
	}

	public boolean hasOceanShading(double resolutionScale)
	{
		double sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScaleRounded(resolutionScale);
		return (int) (sizeMultiplier * oceanShadingLevel) > 0;
	}

	public boolean hasRippleWaves(double resolutionScale)
	{
		double sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScaleRounded(resolutionScale);
		return oceanWavesType == OceanWaves.Ripples && ((int) oceanWavesLevel * sizeMultiplier) > 0;
	}

	public boolean hasConcentricWaves()
	{
		return (oceanWavesType == OceanWaves.ConcentricWaves) && concentricWaveCount > 0;
	}

	public boolean equalsIgnoringEdits(MapSettings other)
	{
		return toJson(true).equals(other.toJson(true));
	}

	/**
	 * Gets the path to the background texture image to use.
	 *
	 * @return Piece 1 - The path Piece 2 - An optional warning message.
	 */
	public Tuple2<Path, String> getBackgroundImagePath()
	{
		if (backgroundTextureSource == TextureSource.File && StringUtils.isEmpty(backgroundTextureImage))
		{
			return new Tuple2<>(Assets.getBackgroundTextureResourcePath(backgroundTextureResource, customImagesPath),
					nortantis.swing.translation.Translation.get("warning.backgroundTextureFallback", backgroundTextureSource));
		}
		if (backgroundTextureSource == TextureSource.Assets)
		{
			return new Tuple2<>(Assets.getBackgroundTextureResourcePath(backgroundTextureResource, customImagesPath), null);
		}
		else
		{
			// File
			return new Tuple2<>(Paths.get(FileHelper.replaceHomeFolderPlaceholder(backgroundTextureImage)), null);
		}
	}

	public Color getIconFillColorForType(IconType iconType)
	{
		if (iconFillColorsByType.containsKey(iconType))
		{
			return iconFillColorsByType.get(iconType);
		}
		return defaultIconFillColor;
	}

	public void setIconFillColorForType(IconType iconType, Color color)
	{
		iconFillColorsByType.put(iconType, color);
	}

	public Map<IconType, Color> copyIconFillColorsByType()
	{
		return Collections.unmodifiableMap(iconFillColorsByType);
	}

	public HSBColor getIconFilterColorForType(IconType iconType)
	{
		if (iconFilterColorsByType.containsKey(iconType))
		{
			return iconFilterColorsByType.get(iconType);
		}
		return defaultIconFilterColor;
	}

	public Map<IconType, HSBColor> copyIconFilterColorsByType()
	{
		return Collections.unmodifiableMap(iconFilterColorsByType);
	}

	public void setIconFilterColorForType(IconType iconType, HSBColor filterColor)
	{
		iconFilterColorsByType.put(iconType, filterColor);
	}

	public boolean getMaximizeOpacityForType(IconType iconType)
	{
		if (maximizeOpacityByType.containsKey(iconType))
		{
			return maximizeOpacityByType.get(iconType);
		}
		return false;
	}

	public Map<IconType, Boolean> copymaximizeOpacityByType()
	{
		return Collections.unmodifiableMap(maximizeOpacityByType);
	}

	public void setMaximizeOpacityForType(IconType iconType, boolean value)
	{
		maximizeOpacityByType.put(iconType, value);
	}

	public boolean getFillWithColorForType(IconType iconType)
	{
		if (fillWithColorByType.containsKey(iconType))
		{
			return fillWithColorByType.get(iconType);
		}
		return false;
	}

	public Map<IconType, Boolean> copyFillWithColorByType()
	{
		return Collections.unmodifiableMap(fillWithColorByType);
	}

	public void setFillWithColorForType(IconType iconType, boolean value)
	{
		fillWithColorByType.put(iconType, value);
	}


	/**
	 * Creates a deep copy of this. Note - This is not thread safe because it temporarily changes the edits pointer in this.
	 */
	public MapSettings deepCopy()
	{
		// I'm copying edits without using Helper.deepCopy because my hand-written deep copy method is 10x faster.
		MapSettings copy = deepCopyExceptEdits();
		if (edits != null)
		{
			copy.edits = edits.deepCopy();
		}

		return copy;
	}

	/**
	 * Creates a deep copy of this, except for the edits object, which will be the same pointer in the copy. Note - This is not thread safe
	 * because it temporarily changes the edits pointer in this.
	 */
	public MapSettings deepCopyExceptEdits()
	{
		MapEdits editsTemp = edits;
		edits = null;
		MapSettings copy = Helper.deepCopy(this);
		edits = editsTemp;
		return copy;
	}

	/**
	 * Records how a sub-map was created, so the user can reproduce it. See {@link MapSettings#subMapInfo}. The selection box is stored in
	 * resolution-invariant (original-map) pixels — the same coordinates shown in the SubMapDialog spinners.
	 */
	public static class SubMapInfo implements java.io.Serializable
	{
		private static final long serialVersionUID = 1L;

		/** File name (not full path) of the original map the sub-map was created from, or null if that map had not been saved. */
		public String originalFileName;
		public double selectionX;
		public double selectionY;
		public double selectionWidth;
		public double selectionHeight;
		/** The sub-map's detail (world size), as actually used after clamping. */
		public int worldSize;
		public long randomSeed;
		/** True if icons and rivers were redistributed for the sub-map's detail; false if they were matched to the source detail. */
		public boolean redistributeIconsAndRivers;

		@SuppressWarnings("unchecked")
		public JSONObject toJson()
		{
			JSONObject obj = new JSONObject();
			if (originalFileName != null)
			{
				obj.put("originalFileName", originalFileName);
			}
			obj.put("selectionX", selectionX);
			obj.put("selectionY", selectionY);
			obj.put("selectionWidth", selectionWidth);
			obj.put("selectionHeight", selectionHeight);
			obj.put("worldSize", worldSize);
			obj.put("randomSeed", randomSeed);
			obj.put("redistributeIconsAndRivers", redistributeIconsAndRivers);
			return obj;
		}

		public static SubMapInfo fromJson(JSONObject obj)
		{
			SubMapInfo info = new SubMapInfo();
			info.originalFileName = (String) obj.get("originalFileName");
			info.selectionX = ((Number) obj.get("selectionX")).doubleValue();
			info.selectionY = ((Number) obj.get("selectionY")).doubleValue();
			info.selectionWidth = ((Number) obj.get("selectionWidth")).doubleValue();
			info.selectionHeight = ((Number) obj.get("selectionHeight")).doubleValue();
			info.worldSize = ((Number) obj.get("worldSize")).intValue();
			info.randomSeed = ((Number) obj.get("randomSeed")).longValue();
			info.redistributeIconsAndRivers = (boolean) obj.get("redistributeIconsAndRivers");
			return info;
		}
	}

	public enum LineStyle
	{
		Jagged, Splines, SplinesWithSmoothedCoastlines
	}

	public enum OceanWaves
	{
		@Deprecated
		Blur, Ripples, ConcentricWaves, @Deprecated
		FadingConcentricWaves, None
	}

	public enum GridOverlayLayer
	{
		Under_icons, Over_icons;

		public String toString()
		{
			return Translation.get("GridOverlayLayer." + name());
		}
	}

	/**
	 * Compares this MapSettings to another and returns a description of which fields differ. Useful for debugging. Does not do a deep
	 * comparison of the edits object.
	 */
	public String findDifferences(MapSettings other)
	{
		if (other == null)
		{
			return "other is null";
		}

		List<String> differences = new ArrayList<>();

		if (!Objects.equals(artPack, other.artPack))
			differences.add("artPack: " + artPack + " vs " + other.artPack);
		if (backgroundRandomSeed != other.backgroundRandomSeed)
			differences.add("backgroundRandomSeed: " + backgroundRandomSeed + " vs " + other.backgroundRandomSeed);
		if (!Objects.equals(backgroundTextureImage, other.backgroundTextureImage))
			differences.add("backgroundTextureImage: " + backgroundTextureImage + " vs " + other.backgroundTextureImage);
		if (!Objects.equals(backgroundTextureResource, other.backgroundTextureResource))
			differences.add("backgroundTextureResource: " + backgroundTextureResource + " vs " + other.backgroundTextureResource);
		if (backgroundTextureSource != other.backgroundTextureSource)
			differences.add("backgroundTextureSource: " + backgroundTextureSource + " vs " + other.backgroundTextureSource);
		if (!Objects.equals(boldBackgroundColor, other.boldBackgroundColor))
			differences.add("boldBackgroundColor: " + boldBackgroundColor + " vs " + other.boldBackgroundColor);
		if (!Objects.equals(books, other.books))
			differences.add("books: " + books + " vs " + other.books);
		if (!Objects.equals(borderColor, other.borderColor))
			differences.add("borderColor: " + borderColor + " vs " + other.borderColor);
		if (borderColorOption != other.borderColorOption)
			differences.add("borderColorOption: " + borderColorOption + " vs " + other.borderColorOption);
		if (borderPosition != other.borderPosition)
			differences.add("borderPosition: " + borderPosition + " vs " + other.borderPosition);
		if (!Objects.equals(borderResource, other.borderResource))
			differences.add("borderResource: " + borderResource + " vs " + other.borderResource);
		if (!Objects.equals(borderType, other.borderType))
			differences.add("borderType: " + borderType + " vs " + other.borderType);
		if (borderWidth != other.borderWidth)
			differences.add("borderWidth: " + borderWidth + " vs " + other.borderWidth);
		if (brightnessRange != other.brightnessRange)
			differences.add("brightnessRange: " + brightnessRange + " vs " + other.brightnessRange);
		if (brokenLinesForConcentricWaves != other.brokenLinesForConcentricWaves)
			differences.add("brokenLinesForConcentricWaves: " + brokenLinesForConcentricWaves + " vs " + other.brokenLinesForConcentricWaves);
		if (Double.doubleToLongBits(centerLandToWaterProbability) != Double.doubleToLongBits(other.centerLandToWaterProbability))
			differences.add("centerLandToWaterProbability: " + centerLandToWaterProbability + " vs " + other.centerLandToWaterProbability);
		if (!Objects.equals(citiesFont, other.citiesFont))
			differences.add("citiesFont: " + citiesFont + " vs " + other.citiesFont);
		if (!Objects.equals(cityIconTypeName, other.cityIconTypeName))
			differences.add("cityIconTypeName: " + cityIconTypeName + " vs " + other.cityIconTypeName);
		if (Double.doubleToLongBits(cityProbability) != Double.doubleToLongBits(other.cityProbability))
			differences.add("cityProbability: " + cityProbability + " vs " + other.cityProbability);
		if (Double.doubleToLongBits(cityScale) != Double.doubleToLongBits(other.cityScale))
			differences.add("cityScale: " + cityScale + " vs " + other.cityScale);
		if (!Objects.equals(coastShadingColor, other.coastShadingColor))
			differences.add("coastShadingColor: " + coastShadingColor + " vs " + other.coastShadingColor);
		if (coastShadingLevel != other.coastShadingLevel)
			differences.add("coastShadingLevel: " + coastShadingLevel + " vs " + other.coastShadingLevel);
		if (!Objects.equals(coastlineColor, other.coastlineColor))
			differences.add("coastlineColor: " + coastlineColor + " vs " + other.coastlineColor);
		if (Double.doubleToLongBits(coastlineWidth) != Double.doubleToLongBits(other.coastlineWidth))
			differences.add("coastlineWidth: " + coastlineWidth + " vs " + other.coastlineWidth);
		if (colorizeLand != other.colorizeLand)
			differences.add("colorizeLand: " + colorizeLand + " vs " + other.colorizeLand);
		if (colorizeOcean != other.colorizeOcean)
			differences.add("colorizeOcean: " + colorizeOcean + " vs " + other.colorizeOcean);
		if (concentricWaveCount != other.concentricWaveCount)
			differences.add("concentricWaveCount: " + concentricWaveCount + " vs " + other.concentricWaveCount);
		if (!Objects.equals(customImagesPath, other.customImagesPath))
			differences.add("customImagesPath: " + customImagesPath + " vs " + other.customImagesPath);
		if (defaultDefaultExportAction != other.defaultDefaultExportAction)
			differences.add("defaultDefaultExportAction: " + defaultDefaultExportAction + " vs " + other.defaultDefaultExportAction);
		if (defaultHeightmapExportAction != other.defaultHeightmapExportAction)
			differences.add("defaultHeightmapExportAction: " + defaultHeightmapExportAction + " vs " + other.defaultHeightmapExportAction);
		if (defaultMapExportAction != other.defaultMapExportAction)
			differences.add("defaultMapExportAction: " + defaultMapExportAction + " vs " + other.defaultMapExportAction);
		if (!Objects.equals(defaultRoadColor, other.defaultRoadColor))
			differences.add("defaultRoadColor: " + defaultRoadColor + " vs " + other.defaultRoadColor);
		if (!Objects.equals(defaultRoadStyle, other.defaultRoadStyle))
			differences.add("defaultRoadStyle: " + defaultRoadStyle + " vs " + other.defaultRoadStyle);
		if (Double.doubleToLongBits(defaultRoadWidth) != Double.doubleToLongBits(other.defaultRoadWidth))
			differences.add("defaultRoadWidth: " + defaultRoadWidth + " vs " + other.defaultRoadWidth);
		if (Double.doubleToLongBits(defaultTreeHeightScaleForOldMaps) != Double.doubleToLongBits(other.defaultTreeHeightScaleForOldMaps))
			differences.add("defaultTreeHeightScaleForOldMaps: " + defaultTreeHeightScaleForOldMaps + " vs " + other.defaultTreeHeightScaleForOldMaps);
		if (drawBoldBackground != other.drawBoldBackground)
			differences.add("drawBoldBackground: " + drawBoldBackground + " vs " + other.drawBoldBackground);
		if (drawBorder != other.drawBorder)
			differences.add("drawBorder: " + drawBorder + " vs " + other.drawBorder);
		if (drawGridOverlay != other.drawGridOverlay)
			differences.add("drawGridOverlay: " + drawGridOverlay + " vs " + other.drawGridOverlay);
		if (drawGrunge != other.drawGrunge)
			differences.add("drawGrunge: " + drawGrunge + " vs " + other.drawGrunge);
		if (drawOceanEffectsInLakes != other.drawOceanEffectsInLakes)
			differences.add("drawOceanEffectsInLakes: " + drawOceanEffectsInLakes + " vs " + other.drawOceanEffectsInLakes);
		if (drawOverlayImage != other.drawOverlayImage)
			differences.add("drawOverlayImage: " + drawOverlayImage + " vs " + other.drawOverlayImage);
		if (drawRegionBoundaries != other.drawRegionBoundaries)
			differences.add("drawRegionBoundaries: " + drawRegionBoundaries + " vs " + other.drawRegionBoundaries);
		if (drawRegionColors != other.drawRegionColors)
			differences.add("drawRegionColors: " + drawRegionColors + " vs " + other.drawRegionColors);
		if (drawRoads != other.drawRoads)
			differences.add("drawRoads: " + drawRoads + " vs " + other.drawRoads);
		if (drawText != other.drawText)
			differences.add("drawText: " + drawText + " vs " + other.drawText);
		if (drawVoronoiGridOverlayOnlyOnLand != other.drawVoronoiGridOverlayOnlyOnLand)
			differences.add("drawVoronoiGridOverlayOnlyOnLand: " + drawVoronoiGridOverlayOnlyOnLand + " vs " + other.drawVoronoiGridOverlayOnlyOnLand);
		if (Double.doubleToLongBits(duneScale) != Double.doubleToLongBits(other.duneScale))
			differences.add("duneScale: " + duneScale + " vs " + other.duneScale);
		if (Double.doubleToLongBits(edgeLandToWaterProbability) != Double.doubleToLongBits(other.edgeLandToWaterProbability))
			differences.add("edgeLandToWaterProbability: " + edgeLandToWaterProbability + " vs " + other.edgeLandToWaterProbability);
		if (edits != other.edits)
			differences.add("edits: (reference differs, deep comparison skipped)");
		if (fadeConcentricWaves != other.fadeConcentricWaves)
			differences.add("fadeConcentricWaves: " + fadeConcentricWaves + " vs " + other.fadeConcentricWaves);
		if (!Objects.equals(fillWithColorByType, other.fillWithColorByType))
			differences.add("fillWithColorByType: " + fillWithColorByType + " vs " + other.fillWithColorByType);
		if (flipHorizontally != other.flipHorizontally)
			differences.add("flipHorizontally: " + flipHorizontally + " vs " + other.flipHorizontally);
		if (flipVertically != other.flipVertically)
			differences.add("flipVertically: " + flipVertically + " vs " + other.flipVertically);
		if (frayedBorder != other.frayedBorder)
			differences.add("frayedBorder: " + frayedBorder + " vs " + other.frayedBorder);
		if (frayedBorderBlurLevel != other.frayedBorderBlurLevel)
			differences.add("frayedBorderBlurLevel: " + frayedBorderBlurLevel + " vs " + other.frayedBorderBlurLevel);
		if (!Objects.equals(frayedBorderColor, other.frayedBorderColor))
			differences.add("frayedBorderColor: " + frayedBorderColor + " vs " + other.frayedBorderColor);
		if (frayedBorderSeed != other.frayedBorderSeed)
			differences.add("frayedBorderSeed: " + frayedBorderSeed + " vs " + other.frayedBorderSeed);
		if (frayedBorderSize != other.frayedBorderSize)
			differences.add("frayedBorderSize: " + frayedBorderSize + " vs " + other.frayedBorderSize);
		if (generateBackground != other.generateBackground)
			differences.add("generateBackground: " + generateBackground + " vs " + other.generateBackground);
		if (generateBackgroundFromTexture != other.generateBackgroundFromTexture)
			differences.add("generateBackgroundFromTexture: " + generateBackgroundFromTexture + " vs " + other.generateBackgroundFromTexture);
		if (generatedHeight != other.generatedHeight)
			differences.add("generatedHeight: " + generatedHeight + " vs " + other.generatedHeight);
		if (generatedWidth != other.generatedWidth)
			differences.add("generatedWidth: " + generatedWidth + " vs " + other.generatedWidth);
		if (!Objects.equals(gridOverlayColor, other.gridOverlayColor))
			differences.add("gridOverlayColor: " + gridOverlayColor + " vs " + other.gridOverlayColor);
		if (gridOverlayLayer != other.gridOverlayLayer)
			differences.add("gridOverlayLayer: " + gridOverlayLayer + " vs " + other.gridOverlayLayer);
		if (gridOverlayLineWidth != other.gridOverlayLineWidth)
			differences.add("gridOverlayLineWidth: " + gridOverlayLineWidth + " vs " + other.gridOverlayLineWidth);
		if (gridOverlayRowOrColCount != other.gridOverlayRowOrColCount)
			differences.add("gridOverlayRowOrColCount: " + gridOverlayRowOrColCount + " vs " + other.gridOverlayRowOrColCount);
		if (gridOverlayShape != other.gridOverlayShape)
			differences.add("gridOverlayShape: " + gridOverlayShape + " vs " + other.gridOverlayShape);
		if (gridOverlayXOffset != other.gridOverlayXOffset)
			differences.add("gridOverlayXOffset: " + gridOverlayXOffset + " vs " + other.gridOverlayXOffset);
		if (gridOverlayYOffset != other.gridOverlayYOffset)
			differences.add("gridOverlayYOffset: " + gridOverlayYOffset + " vs " + other.gridOverlayYOffset);
		if (grungeWidth != other.grungeWidth)
			differences.add("grungeWidth: " + grungeWidth + " vs " + other.grungeWidth);
		if (!Objects.equals(heightmapExportPath, other.heightmapExportPath))
			differences.add("heightmapExportPath: " + heightmapExportPath + " vs " + other.heightmapExportPath);
		if (Double.doubleToLongBits(heightmapResolution) != Double.doubleToLongBits(other.heightmapResolution))
			differences.add("heightmapResolution: " + heightmapResolution + " vs " + other.heightmapResolution);
		if (Double.doubleToLongBits(hillScale) != Double.doubleToLongBits(other.hillScale))
			differences.add("hillScale: " + hillScale + " vs " + other.hillScale);
		if (hueRange != other.hueRange)
			differences.add("hueRange: " + hueRange + " vs " + other.hueRange);
		if (!Objects.equals(iconFillColorsByType, other.iconFillColorsByType))
			differences.add("iconFillColorsByType: " + iconFillColorsByType + " vs " + other.iconFillColorsByType);
		if (!Objects.equals(iconFilterColorsByType, other.iconFilterColorsByType))
			differences.add("iconFilterColorsByType: " + iconFilterColorsByType + " vs " + other.iconFilterColorsByType);
		if (!Objects.equals(imageExportPath, other.imageExportPath))
			differences.add("imageExportPath: " + imageExportPath + " vs " + other.imageExportPath);
		if (jitterToConcentricWaves != other.jitterToConcentricWaves)
			differences.add("jitterToConcentricWaves: " + jitterToConcentricWaves + " vs " + other.jitterToConcentricWaves);
		if (!Objects.equals(landColor, other.landColor))
			differences.add("landColor: " + landColor + " vs " + other.landColor);
		if (landShape != other.landShape)
			differences.add("landShape: " + landShape + " vs " + other.landShape);
		if (lineStyle != other.lineStyle)
			differences.add("lineStyle: " + lineStyle + " vs " + other.lineStyle);
		if (Double.doubleToLongBits(lloydRelaxationsScale) != Double.doubleToLongBits(other.lloydRelaxationsScale))
			differences.add("lloydRelaxationsScale: " + lloydRelaxationsScale + " vs " + other.lloydRelaxationsScale);
		if (!Objects.equals(maximizeOpacityByType, other.maximizeOpacityByType))
			differences.add("maximizeOpacityByType: " + maximizeOpacityByType + " vs " + other.maximizeOpacityByType);
		if (!Objects.equals(mountainRangeFont, other.mountainRangeFont))
			differences.add("mountainRangeFont: " + mountainRangeFont + " vs " + other.mountainRangeFont);
		if (Double.doubleToLongBits(mountainScale) != Double.doubleToLongBits(other.mountainScale))
			differences.add("mountainScale: " + mountainScale + " vs " + other.mountainScale);
		if (!Objects.equals(oceanColor, other.oceanColor))
			differences.add("oceanColor: " + oceanColor + " vs " + other.oceanColor);
		if (!Objects.equals(oceanEffectsColor, other.oceanEffectsColor))
			differences.add("oceanEffectsColor: " + oceanEffectsColor + " vs " + other.oceanEffectsColor);
		if (oceanEffectsLevel != other.oceanEffectsLevel)
			differences.add("oceanEffectsLevel: " + oceanEffectsLevel + " vs " + other.oceanEffectsLevel);
		if (!Objects.equals(oceanShadingColor, other.oceanShadingColor))
			differences.add("oceanShadingColor: " + oceanShadingColor + " vs " + other.oceanShadingColor);
		if (oceanShadingLevel != other.oceanShadingLevel)
			differences.add("oceanShadingLevel: " + oceanShadingLevel + " vs " + other.oceanShadingLevel);
		if (!Objects.equals(oceanWavesColor, other.oceanWavesColor))
			differences.add("oceanWavesColor: " + oceanWavesColor + " vs " + other.oceanWavesColor);
		if (oceanWavesLevel != other.oceanWavesLevel)
			differences.add("oceanWavesLevel: " + oceanWavesLevel + " vs " + other.oceanWavesLevel);
		if (oceanWavesType != other.oceanWavesType)
			differences.add("oceanWavesType: " + oceanWavesType + " vs " + other.oceanWavesType);
		if (!Objects.equals(otherMountainsFont, other.otherMountainsFont))
			differences.add("otherMountainsFont: " + otherMountainsFont + " vs " + other.otherMountainsFont);
		if (Double.doubleToLongBits(overlayImageDefaultScale) != Double.doubleToLongBits(other.overlayImageDefaultScale))
			differences.add("overlayImageDefaultScale: " + overlayImageDefaultScale + " vs " + other.overlayImageDefaultScale);
		if (overlayImageDefaultTransparency != other.overlayImageDefaultTransparency)
			differences.add("overlayImageDefaultTransparency: " + overlayImageDefaultTransparency + " vs " + other.overlayImageDefaultTransparency);
		if (!Objects.equals(overlayImagePath, other.overlayImagePath))
			differences.add("overlayImagePath: " + overlayImagePath + " vs " + other.overlayImagePath);
		if (overlayImageTransparency != other.overlayImageTransparency)
			differences.add("overlayImageTransparency: " + overlayImageTransparency + " vs " + other.overlayImageTransparency);
		if (!Objects.equals(overlayOffsetResolutionInvariant, other.overlayOffsetResolutionInvariant))
			differences.add("overlayOffsetResolutionInvariant: " + overlayOffsetResolutionInvariant + " vs " + other.overlayOffsetResolutionInvariant);
		if (Double.doubleToLongBits(overlayScale) != Double.doubleToLongBits(other.overlayScale))
			differences.add("overlayScale: " + overlayScale + " vs " + other.overlayScale);
		if (Double.doubleToLongBits(pointPrecision) != Double.doubleToLongBits(other.pointPrecision))
			differences.add("pointPrecision: " + pointPrecision + " vs " + other.pointPrecision);
		if (randomSeed != other.randomSeed)
			differences.add("randomSeed: " + randomSeed + " vs " + other.randomSeed);
		if (!Objects.equals(regionBaseColor, other.regionBaseColor))
			differences.add("regionBaseColor: " + regionBaseColor + " vs " + other.regionBaseColor);
		if (!Objects.equals(regionBoundaryColor, other.regionBoundaryColor))
			differences.add("regionBoundaryColor: " + regionBoundaryColor + " vs " + other.regionBoundaryColor);
		if (!Objects.equals(regionBoundaryStyle, other.regionBoundaryStyle))
			differences.add("regionBoundaryStyle: " + regionBoundaryStyle + " vs " + other.regionBoundaryStyle);
		if (regionCount != other.regionCount)
			differences.add("regionCount: " + regionCount + " vs " + other.regionCount);
		if (!Objects.equals(regionFont, other.regionFont))
			differences.add("regionFont: " + regionFont + " vs " + other.regionFont);
		if (regionsRandomSeed != other.regionsRandomSeed)
			differences.add("regionsRandomSeed: " + regionsRandomSeed + " vs " + other.regionsRandomSeed);
		if (Double.doubleToLongBits(resolution) != Double.doubleToLongBits(other.resolution))
			differences.add("resolution: " + resolution + " vs " + other.resolution);
		if (rightRotationCount != other.rightRotationCount)
			differences.add("rightRotationCount: " + rightRotationCount + " vs " + other.rightRotationCount);
		if (!Objects.equals(riverColor, other.riverColor))
			differences.add("riverColor: " + riverColor + " vs " + other.riverColor);
		if (!Objects.equals(riverFont, other.riverFont))
			differences.add("riverFont: " + riverFont + " vs " + other.riverFont);
		if (!Objects.equals(roadColor, other.roadColor))
			differences.add("roadColor: " + roadColor + " vs " + other.roadColor);
		if (!Objects.equals(roadStyle, other.roadStyle))
			differences.add("roadStyle: " + roadStyle + " vs " + other.roadStyle);
		if (saturationRange != other.saturationRange)
			differences.add("saturationRange: " + saturationRange + " vs " + other.saturationRange);
		if (solidColorBackground != other.solidColorBackground)
			differences.add("solidColorBackground: " + solidColorBackground + " vs " + other.solidColorBackground);
		if (!Objects.equals(textColor, other.textColor))
			differences.add("textColor: " + textColor + " vs " + other.textColor);
		if (textRandomSeed != other.textRandomSeed)
			differences.add("textRandomSeed: " + textRandomSeed + " vs " + other.textRandomSeed);
		if (!Objects.equals(titleFont, other.titleFont))
			differences.add("titleFont: " + titleFont + " vs " + other.titleFont);
		if (Double.doubleToLongBits(treeHeightScale) != Double.doubleToLongBits(other.treeHeightScale))
			differences.add("treeHeightScale: " + treeHeightScale + " vs " + other.treeHeightScale);
		if (!Objects.equals(version, other.version))
			differences.add("version: " + version + " vs " + other.version);
		if (worldSize != other.worldSize)
			differences.add("worldSize: " + worldSize + " vs " + other.worldSize);

		if (differences.isEmpty())
		{
			return "No differences found";
		}
		return String.join("\n", differences);
	}

	@Override
	public String toString()
	{
		return "MapSettings [" + toJson() + "]";
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(artPack, backgroundRandomSeed, backgroundTextureImage, backgroundTextureResource, backgroundTextureSource, boldBackgroundColor, books, borderColor, borderColorOption,
				borderPosition, borderResource, borderType, borderWidth, brightnessRange, brokenLinesForConcentricWaves, centerLandToWaterProbability, citiesFont, cityIconTypeName, cityProbability,
				cityScale, coastShadingColor, coastShadingLevel, coastlineColor, coastlineWidth, colorizeLand, colorizeOcean, concentricWaveCount, customImagesPath, defaultDefaultExportAction,
				defaultHeightmapExportAction, defaultMapExportAction, defaultRoadColor, defaultRoadStyle, defaultRoadWidth, defaultTreeHeightScaleForOldMaps, drawBoldBackground, drawBorder,
				drawGridOverlay, drawGrunge, drawOceanEffectsInLakes, drawOverlayImage, drawRegionBoundaries, drawRegionColors, drawRoads, drawText, drawVoronoiGridOverlayOnlyOnLand, duneScale,
				edgeLandToWaterProbability, edits, fadeConcentricWaves, fillWithColorByType, flipHorizontally, flipVertically, frayedBorder, frayedBorderBlurLevel, frayedBorderColor, frayedBorderSeed,
				frayedBorderSize, generateBackground, generateBackgroundFromTexture, generatedHeight, generatedWidth, gridOverlayColor, gridOverlayLayer, gridOverlayLineWidth,
				gridOverlayRowOrColCount, gridOverlayShape, gridOverlayXOffset, gridOverlayYOffset, grungeWidth, heightmapExportPath, heightmapResolution, hillScale, hueRange, iconFillColorsByType,
				iconFilterColorsByType, imageExportPath, jitterToConcentricWaves, landColor, landShape, lineStyle, lloydRelaxationsScale, maximizeOpacityByType, mountainRangeFont, mountainScale,
				oceanColor, oceanEffectsColor, oceanEffectsLevel, oceanShadingColor, oceanShadingLevel, oceanWavesColor, oceanWavesLevel, oceanWavesType, otherMountainsFont, overlayImageDefaultScale,
				overlayImageDefaultTransparency, overlayImagePath, overlayImageTransparency, overlayOffsetResolutionInvariant, overlayScale, pointPrecision, randomSeed, regionBaseColor,
				regionBoundaryColor, regionBoundaryStyle, regionCount, regionFont, regionsRandomSeed, resolution, rightRotationCount, riverColor, riverFont, roadColor, roadStyle, saturationRange,
				solidColorBackground, textColor, textRandomSeed, titleFont, treeHeightScale, version, worldSize);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		MapSettings other = (MapSettings) obj;
		return Objects.equals(artPack, other.artPack) && backgroundRandomSeed == other.backgroundRandomSeed && Objects.equals(backgroundTextureImage, other.backgroundTextureImage)
				&& Objects.equals(backgroundTextureResource, other.backgroundTextureResource) && backgroundTextureSource == other.backgroundTextureSource
				&& Objects.equals(boldBackgroundColor, other.boldBackgroundColor) && Objects.equals(books, other.books) && Objects.equals(borderColor, other.borderColor)
				&& borderColorOption == other.borderColorOption && borderPosition == other.borderPosition && Objects.equals(borderResource, other.borderResource)
				&& Objects.equals(borderType, other.borderType) && borderWidth == other.borderWidth && brightnessRange == other.brightnessRange
				&& brokenLinesForConcentricWaves == other.brokenLinesForConcentricWaves
				&& Double.doubleToLongBits(centerLandToWaterProbability) == Double.doubleToLongBits(other.centerLandToWaterProbability) && Objects.equals(citiesFont, other.citiesFont)
				&& Objects.equals(cityIconTypeName, other.cityIconTypeName) && Double.doubleToLongBits(cityProbability) == Double.doubleToLongBits(other.cityProbability)
				&& Double.doubleToLongBits(cityScale) == Double.doubleToLongBits(other.cityScale) && Objects.equals(coastShadingColor, other.coastShadingColor)
				&& coastShadingLevel == other.coastShadingLevel && Objects.equals(coastlineColor, other.coastlineColor)
				&& Double.doubleToLongBits(coastlineWidth) == Double.doubleToLongBits(other.coastlineWidth) && colorizeLand == other.colorizeLand && colorizeOcean == other.colorizeOcean
				&& concentricWaveCount == other.concentricWaveCount && Objects.equals(customImagesPath, other.customImagesPath) && defaultDefaultExportAction == other.defaultDefaultExportAction
				&& defaultHeightmapExportAction == other.defaultHeightmapExportAction && defaultMapExportAction == other.defaultMapExportAction
				&& Objects.equals(defaultRoadColor, other.defaultRoadColor) && Objects.equals(defaultRoadStyle, other.defaultRoadStyle)
				&& Double.doubleToLongBits(defaultRoadWidth) == Double.doubleToLongBits(other.defaultRoadWidth)
				&& Double.doubleToLongBits(defaultTreeHeightScaleForOldMaps) == Double.doubleToLongBits(other.defaultTreeHeightScaleForOldMaps) && drawBoldBackground == other.drawBoldBackground
				&& drawBorder == other.drawBorder && drawGridOverlay == other.drawGridOverlay && drawGrunge == other.drawGrunge && drawOceanEffectsInLakes == other.drawOceanEffectsInLakes
				&& drawOverlayImage == other.drawOverlayImage && drawRegionBoundaries == other.drawRegionBoundaries && drawRegionColors == other.drawRegionColors && drawRoads == other.drawRoads
				&& drawText == other.drawText && drawVoronoiGridOverlayOnlyOnLand == other.drawVoronoiGridOverlayOnlyOnLand
				&& Double.doubleToLongBits(duneScale) == Double.doubleToLongBits(other.duneScale)
				&& Double.doubleToLongBits(edgeLandToWaterProbability) == Double.doubleToLongBits(other.edgeLandToWaterProbability) && Objects.equals(edits, other.edits)
				&& fadeConcentricWaves == other.fadeConcentricWaves && Objects.equals(fillWithColorByType, other.fillWithColorByType) && flipHorizontally == other.flipHorizontally
				&& flipVertically == other.flipVertically && frayedBorder == other.frayedBorder && frayedBorderBlurLevel == other.frayedBorderBlurLevel
				&& Objects.equals(frayedBorderColor, other.frayedBorderColor) && frayedBorderSeed == other.frayedBorderSeed && frayedBorderSize == other.frayedBorderSize
				&& generateBackground == other.generateBackground && generateBackgroundFromTexture == other.generateBackgroundFromTexture && generatedHeight == other.generatedHeight
				&& generatedWidth == other.generatedWidth && Objects.equals(gridOverlayColor, other.gridOverlayColor) && gridOverlayLayer == other.gridOverlayLayer
				&& gridOverlayLineWidth == other.gridOverlayLineWidth && gridOverlayRowOrColCount == other.gridOverlayRowOrColCount && gridOverlayShape == other.gridOverlayShape
				&& gridOverlayXOffset == other.gridOverlayXOffset && gridOverlayYOffset == other.gridOverlayYOffset && grungeWidth == other.grungeWidth
				&& Objects.equals(heightmapExportPath, other.heightmapExportPath) && Double.doubleToLongBits(heightmapResolution) == Double.doubleToLongBits(other.heightmapResolution)
				&& Double.doubleToLongBits(hillScale) == Double.doubleToLongBits(other.hillScale) && hueRange == other.hueRange && Objects.equals(iconFillColorsByType, other.iconFillColorsByType)
				&& Objects.equals(iconFilterColorsByType, other.iconFilterColorsByType) && Objects.equals(imageExportPath, other.imageExportPath)
				&& jitterToConcentricWaves == other.jitterToConcentricWaves && Objects.equals(landColor, other.landColor) && landShape == other.landShape && lineStyle == other.lineStyle
				&& Double.doubleToLongBits(lloydRelaxationsScale) == Double.doubleToLongBits(other.lloydRelaxationsScale) && Objects.equals(maximizeOpacityByType, other.maximizeOpacityByType)
				&& Objects.equals(mountainRangeFont, other.mountainRangeFont) && Double.doubleToLongBits(mountainScale) == Double.doubleToLongBits(other.mountainScale)
				&& Objects.equals(oceanColor, other.oceanColor) && Objects.equals(oceanEffectsColor, other.oceanEffectsColor) && oceanEffectsLevel == other.oceanEffectsLevel
				&& Objects.equals(oceanShadingColor, other.oceanShadingColor) && oceanShadingLevel == other.oceanShadingLevel && Objects.equals(oceanWavesColor, other.oceanWavesColor)
				&& oceanWavesLevel == other.oceanWavesLevel && oceanWavesType == other.oceanWavesType && Objects.equals(otherMountainsFont, other.otherMountainsFont)
				&& Double.doubleToLongBits(overlayImageDefaultScale) == Double.doubleToLongBits(other.overlayImageDefaultScale)
				&& overlayImageDefaultTransparency == other.overlayImageDefaultTransparency && Objects.equals(overlayImagePath, other.overlayImagePath)
				&& overlayImageTransparency == other.overlayImageTransparency && Objects.equals(overlayOffsetResolutionInvariant, other.overlayOffsetResolutionInvariant)
				&& Double.doubleToLongBits(overlayScale) == Double.doubleToLongBits(other.overlayScale) && Double.doubleToLongBits(pointPrecision) == Double.doubleToLongBits(other.pointPrecision)
				&& randomSeed == other.randomSeed && Objects.equals(regionBaseColor, other.regionBaseColor) && Objects.equals(regionBoundaryColor, other.regionBoundaryColor)
				&& Objects.equals(regionBoundaryStyle, other.regionBoundaryStyle) && regionCount == other.regionCount && Objects.equals(regionFont, other.regionFont)
				&& regionsRandomSeed == other.regionsRandomSeed && Double.doubleToLongBits(resolution) == Double.doubleToLongBits(other.resolution) && rightRotationCount == other.rightRotationCount
				&& Objects.equals(riverColor, other.riverColor) && Objects.equals(riverFont, other.riverFont) && Objects.equals(roadColor, other.roadColor)
				&& Objects.equals(roadStyle, other.roadStyle) && saturationRange == other.saturationRange && solidColorBackground == other.solidColorBackground
				&& Objects.equals(textColor, other.textColor) && textRandomSeed == other.textRandomSeed && Objects.equals(titleFont, other.titleFont)
				&& Double.doubleToLongBits(treeHeightScale) == Double.doubleToLongBits(other.treeHeightScale) && Objects.equals(version, other.version) && worldSize == other.worldSize;
	}

}
