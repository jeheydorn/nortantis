package nortantis;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.EdgeEdit;
import nortantis.editor.FreeIcon;
import nortantis.editor.RegionEdit;
import nortantis.geom.Point;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.swing.MapEdits;
import nortantis.util.AssetsPath;
import nortantis.util.Helper;

/**
 * For parsing and storing map settings.
 * 
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapSettings implements Serializable
{
	public static final String currentVersion = "2.4";
	public static final double defaultPointPrecision = 2.0;
	public static final double defaultLloydRelaxationsScale = 0.1;
	private final double defaultTreeHeightScaleForOldMaps = 0.5;

	public String version;
	public long randomSeed;
	/**
	 * A scalar multiplied by the map height and width to get the final resolution.
	 */
	public double resolution;
	public int coastShadingLevel;
	public int oceanEffectsLevel;
	public int concentricWaveCount;
	public OceanEffect oceanEffect;
	public boolean drawOceanEffectsInLakes;
	public int worldSize;
	public Color riverColor;
	public Color roadColor;
	public Color coastShadingColor;
	public Color oceanEffectsColor;
	public Color coastlineColor;
	public double centerLandToWaterProbability;
	public double edgeLandToWaterProbability;
	public boolean frayedBorder;
	public int frayedBorderSize;
	public Color frayedBorderColor;
	public int frayedBorderBlurLevel;
	public int grungeWidth;
	public boolean drawGrunge;
	/**
	 * This setting actually means fractal generated as opposed to generated from texture.
	 */
	public boolean generateBackground; // This means generate fractal background. It is mutually exclusive with
										// generateBackgroundFromTexture.
	public boolean generateBackgroundFromTexture;
	public boolean colorizeOcean; // For backgrounds generated from a texture.
	public boolean colorizeLand; // For backgrounds generated from a texture.
	public String backgroundTextureImage;
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
	public boolean drawRegionColors;
	public long regionsRandomSeed;
	public boolean drawBorder;
	public String borderType;
	public int borderWidth;
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
	public double treeHeightScale;
	public double mountainScale = 1.0;
	public double hillScale = 1.0;
	public double duneScale = 1.0;
	public double cityScale = 1.0;

	/**
	 * Default values for new settings
	 */
	private final Color defaultRoadColor = Color.black;


	public MapSettings()
	{
		edits = new MapEdits();
		roadColor = defaultRoadColor;
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
		if (FilenameUtils.getExtension(filePath).toLowerCase().equals("nort"))
		{
			String fileContents = Helper.readFile(filePath);
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

	public void writeToFile(String filePath) throws IOException
	{
		version = currentVersion;
		String json = toJson();
		Helper.writeToFile(filePath, json);
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
		root.put("oceanEffectsLevel", oceanEffectsLevel);
		root.put("concentricWaveCount", concentricWaveCount);
		root.put("oceanEffect", oceanEffect.toString());
		root.put("drawOceanEffectsInLakes", drawOceanEffectsInLakes);
		root.put("worldSize", worldSize);
		root.put("riverColor", colorToString(riverColor));
		root.put("roadColor", colorToString(roadColor));
		root.put("coastShadingColor", colorToString(coastShadingColor));
		root.put("oceanEffectsColor", colorToString(oceanEffectsColor));
		root.put("coastlineColor", colorToString(coastlineColor));
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

		// Background image settings.
		root.put("backgroundRandomSeed", backgroundRandomSeed);
		root.put("generateBackground", generateBackground);
		root.put("backgroundTextureImage", backgroundTextureImage);
		root.put("generateBackgroundFromTexture", generateBackgroundFromTexture);
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
		root.put("borderType", borderType);
		root.put("borderWidth", borderWidth);
		root.put("frayedBorderSize", frayedBorderSize);
		root.put("drawRoads", drawRoads);
		root.put("imageExportPath", imageExportPath);
		root.put("heightmapExportPath", heightmapExportPath);
		root.put("heightmapResolution", heightmapResolution);
		root.put("customImagesPath", customImagesPath);

		root.put("treeHeightScale", treeHeightScale);
		root.put("mountainScale", mountainScale);
		root.put("hillScale", hillScale);
		root.put("duneScale", duneScale);
		root.put("cityScale", cityScale);

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
			list.add(mpObj);
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private JSONArray centerEditsToJson()
	{
		JSONArray list = new JSONArray();
		for (CenterEdit centerEdit : edits.centerEdits)
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
			iconObj.put("groupId", icon.groupId);
			iconObj.put("iconIndex", icon.iconIndex);
			iconObj.put("iconName", icon.iconName);
			iconObj.put("type", icon.type.toString());
			iconObj.put("locationResolutionInvariant", icon.locationResolutionInvariant.toJson());
			iconObj.put("scale", icon.scale);
			iconObj.put("centerIndex", icon.centerIndex);
			iconObj.put("density", icon.density);
			list.add(iconObj);
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
		oceanEffectsLevel = (int) (long) root.get("oceanEffectsLevel");
		concentricWaveCount = (int) (long) root.get("concentricWaveCount");
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
		coastShadingColor = parseColor((String) root.get("coastShadingColor"));
		oceanEffectsColor = parseColor((String) root.get("oceanEffectsColor"));
		coastlineColor = parseColor((String) root.get("coastlineColor"));
		oceanEffect = OceanEffect.valueOf((String) root.get("oceanEffect"));
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
		colorizeOcean = (boolean) root.get("colorizeOcean");
		colorizeLand = (boolean) root.get("colorizeLand");
		if (root.containsKey("backgroundTextureImage"))
		{
			backgroundTextureImage = (String) root.get("backgroundTextureImage");
		}
		if (backgroundTextureImage == null || backgroundTextureImage.isEmpty())
		{
			backgroundTextureImage = Paths.get(AssetsPath.getInstallPath(), "example textures").toString();
		}
		backgroundRandomSeed = (long) (long) root.get("backgroundRandomSeed");
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
		if (root.containsKey("borderWidth"))
		{
			borderWidth = (int) (long) root.get("borderWidth");
		}
		else
		{
			borderWidth = 0;
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

		edits = new MapEdits();
		// hiddenTextIds is a comma delimited list.

		JSONObject editsJson = (JSONObject) root.get("edits");
		edits.text = parseMapTexts(editsJson);
		edits.freeIcons = parseIconEdits(editsJson);
		edits.centerEdits = parseCenterEdits(editsJson);
		edits.regionEdits = parseRegionEdits(editsJson);
		edits.edgeEdits = parseEdgeEdits(editsJson);
		edits.hasIconEdits = (boolean) editsJson.get("hasIconEdits");

		runConversionForShadingAlphaChange();
		runConversionForAllowingMultipleCityTypesInOneMap();
		runConversionToFixDunesGroupId();
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

		for (CenterEdit cEdit : edits.centerEdits)
		{
			if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Dune)
			{
				cEdit.icon.iconGroupId = "dunes";
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

		for (CenterEdit cEdit : edits.centerEdits)
		{
			if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.City)
			{
				cEdit.icon.iconGroupId = cityIconTypeName;
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

		if (oceanEffect == OceanEffect.Blur && oceanEffectsColor.getAlpha() == 255)
		{
			oceanEffectsColor = Color.create(oceanEffectsColor.getRed(), oceanEffectsColor.getGreen(), oceanEffectsColor.getBlue(),
					SettingsGenerator.defaultOceanShadingAlpha);
		}

		if (oceanEffect == OceanEffect.Ripples && oceanEffectsColor.getAlpha() == 255)
		{
			oceanEffectsColor = Color.create(oceanEffectsColor.getRed(), oceanEffectsColor.getGreen(), oceanEffectsColor.getBlue(),
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
			MapText mp = new MapText(text, location, angle, type, lineBreak);
			result.add(mp);
		}

		return result;
	}

	private List<CenterEdit> parseCenterEdits(JSONObject editsJson)
	{
		if (editsJson == null)
		{
			return new ArrayList<CenterEdit>();
		}

		JSONArray array = (JSONArray) editsJson.get("centerEdits");
		List<CenterEdit> result = new ArrayList<>();
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
					String iconGroupId = (String) iconObj.get("iconGroupId");
					int iconIndex = (int) (long) iconObj.get("iconIndex");
					String iconName = (String) iconObj.get("iconName");
					CenterIconType iconType = CenterIconType.valueOf((String) iconObj.get("iconType"));
					icon = new CenterIcon(iconType, iconGroupId, iconIndex);
					icon.iconName = iconName;
				}
			}

			CenterTrees trees = null;
			{
				JSONObject treesObj = (JSONObject) jsonObj.get("trees");
				if (treesObj != null)
				{
					String treeType = (String) treesObj.get("treeType");
					double density = (Double) treesObj.get("density");
					long randomSeed = (Long) treesObj.get("randomSeed");
					trees = new CenterTrees(treeType, density, randomSeed);
				}
			}

			result.add(new CenterEdit(index, isWater, isLake, regionId, icon, trees));
			index++;
		}

		return result;
	}

	private FreeIconCollection parseIconEdits(JSONObject editsJson)
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
			FreeIcon icon = new FreeIcon(type);
			icon.groupId = (String) iconObj.get("groupId");
			icon.iconIndex = (int) (long) iconObj.get("iconIndex");
			icon.iconName = (String) iconObj.get("iconName");
			icon.locationResolutionInvariant = Point.fromJSonValue((String) iconObj.get("locationResolutionInvariant"));
			icon.scale = (double) iconObj.get("scale");
			if (iconObj.containsKey("centerIndex") && iconObj.get("centerIndex") != null)
			{
				icon.centerIndex = (int) (long) iconObj.get("centerIndex");
			}
			icon.density = (double) iconObj.get("density");

			result.addOrReplace(icon);
		}

		return result;
	}


	public ConcurrentHashMap<Integer, RegionEdit> parseRegionEdits(JSONObject editsJson)
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

	public List<EdgeEdit> parseEdgeEdits(JSONObject editsJson)
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
		if (str == null)
			throw new NullPointerException("A color is null.");
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
		oceanEffectsLevel = old.oceanEffectsLevel;
		concentricWaveCount = old.concentricWaveCount;
		oceanEffect = old.oceanEffect;
		worldSize = old.worldSize;
		riverColor = old.riverColor;
		roadColor = old.roadColor;
		coastShadingColor = old.coastShadingColor;
		coastShadingLevel = old.coastShadingLevel;
		oceanEffectsColor = old.oceanEffectsColor;
		coastlineColor = old.coastlineColor;
		centerLandToWaterProbability = old.centerLandToWaterProbability;
		edgeLandToWaterProbability = old.edgeLandToWaterProbability;
		frayedBorder = old.frayedBorder;
		frayedBorderColor = old.frayedBorderColor;
		frayedBorderBlurLevel = old.frayedBorderBlurLevel;
		grungeWidth = old.grungeWidth;
		drawGrunge = true;
		generateBackground = old.generateBackground;
		generateBackgroundFromTexture = old.generateBackgroundFromTexture;
		colorizeOcean = old.colorizeOcean;
		colorizeLand = old.colorizeLand;
		backgroundTextureImage = old.backgroundTextureImage;
		backgroundRandomSeed = old.backgroundRandomSeed;
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
		regionsRandomSeed = old.regionsRandomSeed;
		drawBorder = old.drawBorder;
		borderType = old.borderType;
		borderWidth = old.borderWidth;
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
			System.out.println("Exception while parsing json in conversion. JSON: " + json);
			throw e;
		}
	}

	private boolean isVersionGreatherThanCurrent(String version)
	{
		return isVersionGreaterThan(version, currentVersion);
	}

	private boolean isVersionGreaterThan(String version1, String version2)
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

	public boolean equalsIgnoringEdits(MapSettings other)
	{
		return toJson(true).equals(other.toJson(true));
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

	public enum OceanEffect
	{
		Blur, Ripples, ConcentricWaves, FadingConcentricWaves
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
		return backgroundRandomSeed == other.backgroundRandomSeed && Objects.equals(backgroundTextureImage, other.backgroundTextureImage)
				&& Objects.equals(boldBackgroundColor, other.boldBackgroundColor) && Objects.equals(books, other.books)
				&& Objects.equals(borderType, other.borderType) && borderWidth == other.borderWidth
				&& brightnessRange == other.brightnessRange
				&& Double.doubleToLongBits(centerLandToWaterProbability) == Double.doubleToLongBits(other.centerLandToWaterProbability)
				&& Objects.equals(cityIconTypeName, other.cityIconTypeName)
				&& Double.doubleToLongBits(cityProbability) == Double.doubleToLongBits(other.cityProbability)
				&& Double.doubleToLongBits(cityScale) == Double.doubleToLongBits(other.cityScale)
				&& Objects.equals(coastShadingColor, other.coastShadingColor) && coastShadingLevel == other.coastShadingLevel
				&& Objects.equals(coastlineColor, other.coastlineColor) && colorizeLand == other.colorizeLand
				&& colorizeOcean == other.colorizeOcean && concentricWaveCount == other.concentricWaveCount
				&& Objects.equals(customImagesPath, other.customImagesPath) && Objects.equals(defaultRoadColor, other.defaultRoadColor)
				&& Double.doubleToLongBits(defaultTreeHeightScaleForOldMaps) == Double
						.doubleToLongBits(other.defaultTreeHeightScaleForOldMaps)
				&& drawBoldBackground == other.drawBoldBackground && drawBorder == other.drawBorder && drawGrunge == other.drawGrunge
				&& drawOceanEffectsInLakes == other.drawOceanEffectsInLakes && drawRegionColors == other.drawRegionColors
				&& drawRoads == other.drawRoads && drawText == other.drawText
				&& Double.doubleToLongBits(duneScale) == Double.doubleToLongBits(other.duneScale)
				&& Double.doubleToLongBits(edgeLandToWaterProbability) == Double.doubleToLongBits(other.edgeLandToWaterProbability)
				&& Objects.equals(edits, other.edits) && frayedBorder == other.frayedBorder
				&& frayedBorderBlurLevel == other.frayedBorderBlurLevel && Objects.equals(frayedBorderColor, other.frayedBorderColor)
				&& frayedBorderSize == other.frayedBorderSize && generateBackground == other.generateBackground
				&& generateBackgroundFromTexture == other.generateBackgroundFromTexture && generatedHeight == other.generatedHeight
				&& generatedWidth == other.generatedWidth && grungeWidth == other.grungeWidth
				&& Objects.equals(heightmapExportPath, other.heightmapExportPath)
				&& Double.doubleToLongBits(heightmapResolution) == Double.doubleToLongBits(other.heightmapResolution)
				&& Double.doubleToLongBits(hillScale) == Double.doubleToLongBits(other.hillScale) && hueRange == other.hueRange
				&& Objects.equals(imageExportPath, other.imageExportPath) && Objects.equals(landColor, other.landColor)
				&& lineStyle == other.lineStyle
				&& Double.doubleToLongBits(lloydRelaxationsScale) == Double.doubleToLongBits(other.lloydRelaxationsScale)
				&& Objects.equals(mountainRangeFont, other.mountainRangeFont)
				&& Double.doubleToLongBits(mountainScale) == Double.doubleToLongBits(other.mountainScale)
				&& Objects.equals(oceanColor, other.oceanColor) && oceanEffect == other.oceanEffect
				&& Objects.equals(oceanEffectsColor, other.oceanEffectsColor) && oceanEffectsLevel == other.oceanEffectsLevel
				&& Objects.equals(otherMountainsFont, other.otherMountainsFont)
				&& Double.doubleToLongBits(pointPrecision) == Double.doubleToLongBits(other.pointPrecision)
				&& randomSeed == other.randomSeed && Objects.equals(regionBaseColor, other.regionBaseColor)
				&& Objects.equals(regionFont, other.regionFont) && regionsRandomSeed == other.regionsRandomSeed
				&& Double.doubleToLongBits(resolution) == Double.doubleToLongBits(other.resolution)
				&& Objects.equals(riverColor, other.riverColor) && Objects.equals(riverFont, other.riverFont)
				&& Objects.equals(roadColor, other.roadColor) && saturationRange == other.saturationRange
				&& Objects.equals(textColor, other.textColor) && textRandomSeed == other.textRandomSeed
				&& Objects.equals(titleFont, other.titleFont)
				&& Double.doubleToLongBits(treeHeightScale) == Double.doubleToLongBits(other.treeHeightScale)
				&& Objects.equals(version, other.version) && worldSize == other.worldSize;
	}

}
