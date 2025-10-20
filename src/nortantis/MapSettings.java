package nortantis;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.EdgeEdit;
import nortantis.editor.ExportAction;
import nortantis.editor.FreeIcon;
import nortantis.editor.RegionEdit;
import nortantis.editor.Road;
import nortantis.geom.Point;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.swing.MapEdits;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.Helper;
import nortantis.util.Logger;
import nortantis.util.OSHelper;
import nortantis.util.Tuple2;

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
	 * When updating this, also update installers/version.txt
	 */
	public static final String currentVersion = "3.13";
	public static final double defaultPointPrecision = 2.0;
	public static final double defaultLloydRelaxationsScale = 0.1;
	private final double defaultTreeHeightScaleForOldMaps = 0.5;
	private final double defaultRoadWidth = 1.0;
	private final Stroke defaultRoadStyle = new Stroke(StrokeType.Dots,
			(float) (MapCreator.calcSizeMultipilerFromResolutionScaleRounded(1.0) * defaultRoadWidth));
	private final Color defaultRoadColor = Color.black;
	public static final Color defaultIconColor = Color.create(0, 0, 0, 0);

	public String version;
	public long randomSeed;
	/**
	 * A scalar multiplied by the map height and width to get the final resolution.
	 */
	public double resolution;
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
	public double heightmapResolution = 1.0;
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
	private final ExportAction defaultDefaultExportAction = ExportAction.SaveToFile;
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

	private ConcurrentHashMap<IconType, Color> iconColorsByType;

	public MapSettings()
	{
		iconColorsByType = new ConcurrentHashMap<IconType, Color>();
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
			throw new IllegalArgumentException("The map settings file, '" + filePath
					+ "', is not a supported file type. It must be either either a json file or a properties file.");
		}
	}

	public static boolean isOldPropertiesFile(String filePath)
	{
		return FilenameUtils.getExtension(filePath).toLowerCase().equals("properties");
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

		boolean topFolderHasConvertedTypeFolder = Arrays.stream(IconType.values())
				.anyMatch(type -> Paths.get(customImagesFolder, type.toString()).toFile().isDirectory());
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
				FileUtils.moveDirectoryToDirectory(Paths.get(customImagesFolder, "icons", type.toString()).toFile(),
						Paths.get(customImagesFolder).toFile(), false);
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
		root.put("oceanEffect", oceanWavesType.toString());
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
		root.put("frayedBorder", frayedBorder);
		root.put("frayedBorderColor", colorToString(frayedBorderColor));
		root.put("frayedBorderBlurLevel", frayedBorderBlurLevel);
		root.put("grungeWidth", grungeWidth);
		root.put("drawGrunge", drawGrunge);
		root.put("cityProbability", cityProbability);
		root.put("lineStyle", lineStyle.toString());
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
		root.put("backgroundTextureSource",
				backgroundTextureSource == null ? TextureSource.Assets.toString() : backgroundTextureSource.toString());
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
		root.put("borderPosition", borderPosition.toString());
		root.put("borderColorOption", borderColorOption.toString());
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
		root.put("defaultMapExportAction",
				defaultMapExportAction != null ? defaultMapExportAction.toString() : defaultDefaultExportAction.toString());
		root.put("defaultHeightmapExportAction",
				defaultHeightmapExportAction != null ? defaultHeightmapExportAction.toString() : defaultDefaultExportAction.toString());

		root.put("drawOverlayImage", drawOverlayImage);
		root.put("overlayImagePath", overlayImagePath);
		root.put("overlayImageTransparency", overlayImageTransparency);
		root.put("overlayScale", overlayScale);
		root.put("overlayOffsetResolutionInvariant",
				overlayOffsetResolutionInvariant == null ? null : overlayOffsetResolutionInvariant.toJson());

		root.put("rightRotationCount", rightRotationCount);
		root.put("flipHorizontally", flipHorizontally);
		root.put("flipVertically", flipVertically);

		JSONObject iconColorsObj = new JSONObject();
		for (Map.Entry<IconType, Color> entry : iconColorsByType.entrySet())
		{
			IconType key = entry.getKey();
			Color value = entry.getValue();

			// JSONSimple will handle most basic types
			iconColorsObj.put(key, colorToString(value));
		}
		root.put("iconColorsByType", iconColorsObj);

		// User edits.
		if (edits != null && !skipEdits)
		{
			JSONObject editsJson = new JSONObject();
			root.put("edits", editsJson);
			editsJson.put("textEdits", textEditsToJson());
			editsJson.put("centerEdits", centerEditsToJson());
			editsJson.put("iconEdits", iconsToJson());
			editsJson.put("regionEdits", regionEditsToJson());
			editsJson.put("edgeEdits", edgeEditsToJson());
			editsJson.put("hasIconEdits", edits.hasIconEdits);
			editsJson.put("roads", roadsToJson());
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
			mpObj.put("angle", text.angle);
			mpObj.put("type", text.type.toString());
			mpObj.put("lineBreak", text.lineBreak.toString());
			if (text.colorOverride != null)
			{
				mpObj.put("colorOverride", colorToString(text.colorOverride));
			}
			if (text.boldBackgroundColorOverride != null)
			{
				mpObj.put("boldBackgroundColorOverride", colorToString(text.boldBackgroundColorOverride));
			}
			mpObj.put("curvature", text.curvature);
			mpObj.put("spacing", text.spacing);
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
				mpObj.put("trees", treesObj);
			}
			list.add(mpObj);
		}
		return list;
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
			iconObj.put("iconName", icon.iconName);
			iconObj.put("type", icon.type.toString());
			iconObj.put("locationResolutionInvariant", icon.locationResolutionInvariant.toJson());
			iconObj.put("scale", icon.scale);
			iconObj.put("centerIndex", icon.centerIndex);
			iconObj.put("density", icon.density);
			iconObj.put("color", colorToString(icon.color));
			iconObj.put("originalScale", icon.originalScale);
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
			if (road.path != null)
			{
				for (Point point : road.path)
				{
					pathJson.add(point.toJson());
				}
			}
			roadObj.put("path", pathJson);
			roadsJson.add(roadObj);
		}

		return roadsJson;
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

	@SuppressWarnings("unchecked")
	private JSONObject strokeToJson(Stroke stroke)
	{
		JSONObject obj = new JSONObject();
		obj.put("type", stroke.type.toString());
		obj.put("width", stroke.width);
		return obj;
	}

	@SuppressWarnings("unchecked")
	private JSONArray edgeEditsToJson()
	{
		JSONArray list = new JSONArray();
		for (EdgeEdit eEdit : edits.edgeEdits)
		{
			JSONObject mpObj = new JSONObject();
			if (eEdit.riverLevel > 0)
			{
				mpObj.put("riverLevel", eEdit.riverLevel);
			}
			mpObj.put("index", eEdit.index);
			list.add(mpObj);
		}
		return list;
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
		return font.getFontName() + "\t" + font.getStyle().value + "\t" + (int) font.getSize();
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
		if (isVersionGreatherThanCurrent(version))
		{
			throw new RuntimeException("The map cannot be loaded because it was made in a new version of Nortantis. That map's version is "
					+ version + ", but you're Nortantis version is " + currentVersion + ". Try again with a newer version of Nortantis.");
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
			coastlineWidth = MapCreator.calcSizeMultipilerFromResolutionScaleRounded(1.0);
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

		iconColorsByType.clear();
		if (root.containsKey("iconColorsByType"))
		{
			JSONObject mapObj = (JSONObject) root.get("iconColorsByType");
			for (Object key : mapObj.keySet())
			{
				String keyString = (String) key;
				IconType iconType = IconType.valueOf(keyString);
				Color color = parseColor((String) mapObj.get(key));
				iconColorsByType.put(iconType, color);
			}
		}

		// Make they are all populated with transparent values.
		for (IconType iconType : IconType.values())
		{
			if (!iconColorsByType.containsKey(iconType))
			{
				iconColorsByType.put(iconType, defaultIconColor);
			}
		}

		edits = new MapEdits();
		// hiddenTextIds is a comma delimited list.

		boolean hasCustomImagesPath = !StringUtils.isEmpty(customImagesPath);
		JSONObject editsJson = (JSONObject) root.get("edits");
		edits.text = parseMapTexts(editsJson);
		edits.freeIcons = parseIconEdits(editsJson, hasCustomImagesPath);
		edits.centerEdits = parseCenterEdits(editsJson, hasCustomImagesPath);
		edits.regionEdits = parseRegionEdits(editsJson);
		edits.edgeEdits = parseEdgeEdits(editsJson);
		edits.hasIconEdits = (boolean) editsJson.get("hasIconEdits");
		edits.roads = parseRoads(editsJson);

		runConversionForShadingAlphaChange();
		runConversionForAllowingMultipleCityTypesInOneMap();
		runConversionToFixDunesGroupId();
		runConversionOnBackgroundTextureImagePaths();
		runConversionOnBorderType();
		runConversionToRemoveTrailingSpacesInImageNamesWithWidth();
		runConversionOnFadingConcentricWaves();
		runConversionToRemoveRegionIdsOfEditsThatAreWater();
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
			borderResource = new NamedResource(StringUtils.isEmpty(customImagesPath) ? Assets.installedArtPack : Assets.customArtPack,
					borderType);
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

				if (backgroundTextureImage.startsWith(oldExampleTexturesInstalledPath)
						|| backgroundTextureImage.startsWith(oldExampleTexturesRunningFromSourcePath))
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
			coastShadingColor = Color.create(coastShadingColor.getRed(), coastShadingColor.getGreen(), coastShadingColor.getBlue(),
					SettingsGenerator.defaultCoastShadingAlpha);
		}

		if (oceanShadingColor.getAlpha() == 255)
		{
			oceanShadingColor = Color.create(oceanShadingColor.getRed(), oceanShadingColor.getGreen(), oceanShadingColor.getBlue(),
					SettingsGenerator.defaultOceanShadingAlpha);
		}

		if (oceanWavesType == OceanWaves.Ripples && oceanWavesColor.getAlpha() == 255)
		{
			oceanWavesColor = Color.create(oceanWavesColor.getRed(), oceanWavesColor.getGreen(), oceanWavesColor.getBlue(),
					SettingsGenerator.defaultOceanRipplesAlpha);
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
			double angle = (Double) jsonObj.get("angle");
			TextType type = Enum.valueOf(TextType.class, ((String) jsonObj.get("type")).replace(" ", "_"));
			LineBreak lineBreak = jsonObj.containsKey("lineBreak")
					? Enum.valueOf(LineBreak.class, ((String) jsonObj.get("lineBreak")).replace(" ", "_"))
					: LineBreak.Auto;
			Color colorOverride = jsonObj.containsKey("colorOverride") ? parseColor((String) jsonObj.get("colorOverride")) : null;
			Color boldBackgroundColorOverride = jsonObj.containsKey("boldBackgroundColorOverride")
					? parseColor((String) jsonObj.get("boldBackgroundColorOverride"))
					: null;
			double curvature = jsonObj.containsKey("curvature") ? (Double) jsonObj.get("curvature") : 0.0;
			int spacing = jsonObj.containsKey("spacing") ? (int) (long) jsonObj.get("spacing") : 0;
			MapText mp = new MapText(text, location, angle, type, lineBreak, colorOverride, boldBackgroundColorOverride, curvature,
					spacing);
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
					trees = new CenterTrees(artPack, treeType, density, randomSeed, isDormant);
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
			String iconName = (String) iconObj.get("iconName");
			Point locationResolutionInvariant = Point.fromJSonValue((String) iconObj.get("locationResolutionInvariant"));
			double scale = (double) iconObj.get("scale");
			Integer centerIndex = null;
			if (iconObj.containsKey("centerIndex") && iconObj.get("centerIndex") != null)
			{
				centerIndex = (int) (long) iconObj.get("centerIndex");
			}
			double density = (double) iconObj.get("density");
			Color color = iconObj.containsKey("color") ? parseColor((String) iconObj.get("color")) : defaultIconColor;
			double originalScale;
			if (iconObj.containsKey("originalScale") && iconObj.get("originalScale") != null)
			{
				originalScale = (double) iconObj.get("originalScale");
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

			result.addOrReplace(new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex,
					density, color, originalScale));
		}

		return result;
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
			Road road = new Road(path);
			roads.add(road);
		}
		return roads;
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
			return new Stroke(StrokeType.Solid, (float) (MapCreator.calcSizeMultipilerFromResolutionScaleRounded(1.0)));
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

	private List<EdgeEdit> parseEdgeEdits(JSONObject editsJson)
	{
		if (editsJson == null)
		{
			return new ArrayList<>();
		}
		JSONArray array = (JSONArray) editsJson.get("edgeEdits");
		List<EdgeEdit> result = new ArrayList<>();
		for (Object obj : array)
		{
			JSONObject jsonObj = (JSONObject) obj;
			int riverLevel = 0;
			if (jsonObj.containsKey("riverLevel"))
			{
				riverLevel = (int) (long) jsonObj.get("riverLevel");
			}
			int index = (int) (long) jsonObj.get("index");
			result.add(new EdgeEdit(index, riverLevel));
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
			return Color.create(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
					Integer.parseInt(parts[3]));
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
		coastlineWidth = MapCreator.calcSizeMultipilerFromResolutionScaleRounded(1.0);
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

	public static boolean isVersionGreatherThanCurrent(String version)
	{
		return isVersionGreaterThan(version, currentVersion);
	}

	public static boolean isVersionGreaterThan(String version1, String version2)
	{
		if (version1 == null || version1.equals(""))
		{
			return false;
		}
		if (version2 == null || version2.equals(""))
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
		double sizeMultiplier = MapCreator.calcSizeMultipilerFromResolutionScaleRounded(resolutionScale);
		return (int) (sizeMultiplier * oceanShadingLevel) > 0;
	}

	public boolean hasRippleWaves(double resolutionScale)
	{
		double sizeMultiplier = MapCreator.calcSizeMultipilerFromResolutionScaleRounded(resolutionScale);
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
					"The selected background texture source is '" + backgroundTextureSource
							+ "', but no texture image file is selected. An image from assets was used instead.");
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

	public Color getIconColorForType(IconType iconType)
	{
		if (iconColorsByType.containsKey(iconType))
		{
			return iconColorsByType.get(iconType);
		}
		return defaultIconColor;
	}

	public Map<IconType, Color> copyIconColorsByType()
	{
		return Collections.unmodifiableMap(iconColorsByType);
	}

	public void setIconColorForType(IconType iconType, Color color)
	{
		iconColorsByType.put(iconType, color);
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

	public static final String fileExtension = "nort";
	public static final String fileExtensionWithDot = "." + fileExtension;

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
		return Objects.equals(artPack, other.artPack) && backgroundRandomSeed == other.backgroundRandomSeed
				&& Objects.equals(backgroundTextureImage, other.backgroundTextureImage)
				&& Objects.equals(backgroundTextureResource, other.backgroundTextureResource)
				&& backgroundTextureSource == other.backgroundTextureSource
				&& Objects.equals(boldBackgroundColor, other.boldBackgroundColor) && Objects.equals(books, other.books)
				&& Objects.equals(borderColor, other.borderColor) && borderColorOption == other.borderColorOption
				&& borderPosition == other.borderPosition && Objects.equals(borderResource, other.borderResource)
				&& Objects.equals(borderType, other.borderType) && borderWidth == other.borderWidth
				&& brightnessRange == other.brightnessRange && brokenLinesForConcentricWaves == other.brokenLinesForConcentricWaves
				&& Double.doubleToLongBits(centerLandToWaterProbability) == Double.doubleToLongBits(other.centerLandToWaterProbability)
				&& Objects.equals(cityIconTypeName, other.cityIconTypeName)
				&& Double.doubleToLongBits(cityProbability) == Double.doubleToLongBits(other.cityProbability)
				&& Double.doubleToLongBits(cityScale) == Double.doubleToLongBits(other.cityScale)
				&& Objects.equals(coastShadingColor, other.coastShadingColor) && coastShadingLevel == other.coastShadingLevel
				&& Objects.equals(coastlineColor, other.coastlineColor)
				&& Double.doubleToLongBits(coastlineWidth) == Double.doubleToLongBits(other.coastlineWidth)
				&& colorizeLand == other.colorizeLand && colorizeOcean == other.colorizeOcean
				&& concentricWaveCount == other.concentricWaveCount && Objects.equals(customImagesPath, other.customImagesPath)
				&& defaultDefaultExportAction == other.defaultDefaultExportAction
				&& defaultHeightmapExportAction == other.defaultHeightmapExportAction
				&& defaultMapExportAction == other.defaultMapExportAction && Objects.equals(defaultRoadColor, other.defaultRoadColor)
				&& Objects.equals(defaultRoadStyle, other.defaultRoadStyle)
				&& Double.doubleToLongBits(defaultRoadWidth) == Double.doubleToLongBits(other.defaultRoadWidth)
				&& Double.doubleToLongBits(defaultTreeHeightScaleForOldMaps) == Double
						.doubleToLongBits(other.defaultTreeHeightScaleForOldMaps)
				&& drawBoldBackground == other.drawBoldBackground && drawBorder == other.drawBorder && drawGrunge == other.drawGrunge
				&& drawOceanEffectsInLakes == other.drawOceanEffectsInLakes && drawOverlayImage == other.drawOverlayImage
				&& drawRegionBoundaries == other.drawRegionBoundaries && drawRegionColors == other.drawRegionColors
				&& drawRoads == other.drawRoads && drawText == other.drawText
				&& Double.doubleToLongBits(duneScale) == Double.doubleToLongBits(other.duneScale)
				&& Double.doubleToLongBits(edgeLandToWaterProbability) == Double.doubleToLongBits(other.edgeLandToWaterProbability)
				&& Objects.equals(edits, other.edits) && fadeConcentricWaves == other.fadeConcentricWaves
				&& flipHorizontally == other.flipHorizontally && flipVertically == other.flipVertically
				&& frayedBorder == other.frayedBorder && frayedBorderBlurLevel == other.frayedBorderBlurLevel
				&& Objects.equals(frayedBorderColor, other.frayedBorderColor) && frayedBorderSeed == other.frayedBorderSeed
				&& frayedBorderSize == other.frayedBorderSize && generateBackground == other.generateBackground
				&& generateBackgroundFromTexture == other.generateBackgroundFromTexture && generatedHeight == other.generatedHeight
				&& generatedWidth == other.generatedWidth && grungeWidth == other.grungeWidth
				&& Objects.equals(heightmapExportPath, other.heightmapExportPath)
				&& Double.doubleToLongBits(heightmapResolution) == Double.doubleToLongBits(other.heightmapResolution)
				&& Double.doubleToLongBits(hillScale) == Double.doubleToLongBits(other.hillScale) && hueRange == other.hueRange
				&& Objects.equals(iconColorsByType, other.iconColorsByType) && Objects.equals(imageExportPath, other.imageExportPath)
				&& jitterToConcentricWaves == other.jitterToConcentricWaves && Objects.equals(landColor, other.landColor)
				&& lineStyle == other.lineStyle
				&& Double.doubleToLongBits(lloydRelaxationsScale) == Double.doubleToLongBits(other.lloydRelaxationsScale)
				&& Objects.equals(mountainRangeFont, other.mountainRangeFont)
				&& Double.doubleToLongBits(mountainScale) == Double.doubleToLongBits(other.mountainScale)
				&& Objects.equals(oceanColor, other.oceanColor) && Objects.equals(oceanEffectsColor, other.oceanEffectsColor)
				&& oceanEffectsLevel == other.oceanEffectsLevel && Objects.equals(oceanShadingColor, other.oceanShadingColor)
				&& oceanShadingLevel == other.oceanShadingLevel && Objects.equals(oceanWavesColor, other.oceanWavesColor)
				&& oceanWavesLevel == other.oceanWavesLevel && oceanWavesType == other.oceanWavesType
				&& Objects.equals(otherMountainsFont, other.otherMountainsFont)
				&& Double.doubleToLongBits(overlayImageDefaultScale) == Double.doubleToLongBits(other.overlayImageDefaultScale)
				&& overlayImageDefaultTransparency == other.overlayImageDefaultTransparency
				&& Objects.equals(overlayImagePath, other.overlayImagePath) && overlayImageTransparency == other.overlayImageTransparency
				&& Objects.equals(overlayOffsetResolutionInvariant, other.overlayOffsetResolutionInvariant)
				&& Double.doubleToLongBits(overlayScale) == Double.doubleToLongBits(other.overlayScale)
				&& Double.doubleToLongBits(pointPrecision) == Double.doubleToLongBits(other.pointPrecision)
				&& randomSeed == other.randomSeed && Objects.equals(regionBaseColor, other.regionBaseColor)
				&& Objects.equals(regionBoundaryColor, other.regionBoundaryColor)
				&& Objects.equals(regionBoundaryStyle, other.regionBoundaryStyle) && Objects.equals(regionFont, other.regionFont)
				&& regionsRandomSeed == other.regionsRandomSeed
				&& Double.doubleToLongBits(resolution) == Double.doubleToLongBits(other.resolution)
				&& rightRotationCount == other.rightRotationCount && Objects.equals(riverColor, other.riverColor)
				&& Objects.equals(riverFont, other.riverFont) && Objects.equals(roadColor, other.roadColor)
				&& Objects.equals(roadStyle, other.roadStyle) && saturationRange == other.saturationRange
				&& solidColorBackground == other.solidColorBackground && Objects.equals(textColor, other.textColor)
				&& textRandomSeed == other.textRandomSeed && Objects.equals(titleFont, other.titleFont)
				&& Double.doubleToLongBits(treeHeightScale) == Double.doubleToLongBits(other.treeHeightScale)
				&& Objects.equals(version, other.version) && worldSize == other.worldSize;
	}

}
