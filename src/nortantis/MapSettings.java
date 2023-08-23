package nortantis;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
import nortantis.editor.RegionEdit;
import nortantis.graph.geom.Point;
import nortantis.swing.MapEdits;
import nortantis.util.AssetsPath;
import nortantis.util.Helper;

/**
 * For parsing and storing map settings.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapSettings implements Serializable
{
	public static final String currentVersion = "0.1";
	public static final double defaultPointPrecision = 2.0;

	public String version;
	public long randomSeed;
	/**
	 *  A scalar multiplied by the map height and width to get the final resolution.
	 */
	public double resolution;
	public int coastShadingLevel;
	public int oceanEffectsLevel;
	public int concentricWaveCount;
	public OceanEffect oceanEffect;
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
	/**
	 * This setting actually means fractal generated as opposed to generated from texture.
	 */
	public boolean generateBackground; // This means generate fractal background. It is mutually exclusive with generateBackgroundFromTexture.
	public boolean generateBackgroundFromTexture;
	public boolean colorizeOcean; // For backgrounds generated from a texture.
	public boolean colorizeLand; // For backgrounds generated from a texture.
	public String backgroundTextureImage;
	public long backgroundRandomSeed;
	public Color oceanColor;
	public Color landColor;
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
	public boolean drawIcons = true;
	public boolean drawRivers = true; // Not saved
	public boolean drawRoads = true;
	public double cityProbability;
	public LineStyle lineStyle;
	public String cityIconSetName;
	// Not exposed for editing. Only for backwards compatibility so I can change it without braking older settings
	// files that have edits.
	public double pointPrecision = defaultPointPrecision;
	
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
	 * Loads map settings file. The file can either be the newer JSON format, or the older *.properties format, which 
	 * is supported only for converting old files to the new format.
	 * @param filePath file path and file name
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
		
		// Always store the current version number when saving. If the map was loaded from a previous version, 
		// then it was converted to the current version while loading.
		root.put("version", currentVersion);
		
		root.put("randomSeed", randomSeed);
		root.put("resolution", resolution);
		root.put("coastShadingLevel", coastShadingLevel);
		root.put("oceanEffectsLevel", oceanEffectsLevel);
		root.put("concentricWaveCount", concentricWaveCount);
		root.put("oceanEffect", oceanEffect.toString());
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
		root.put("cityProbability", cityProbability);
		root.put("lineStyle", lineStyle.toString());
		root.put("pointPrecision", pointPrecision);

		// Background image settings.
		root.put("backgroundRandomSeed", backgroundRandomSeed);
		root.put("generateBackground", generateBackground);
		root.put("backgroundTextureImage", backgroundTextureImage);
		root.put("generateBackgroundFromTexture", generateBackgroundFromTexture);
		root.put("colorizeOcean", colorizeOcean);
		root.put("colorizeLand", colorizeLand);
		root.put("oceanColor", colorToString(oceanColor));
		root.put("landColor", colorToString(landColor));
		root.put("generatedWidth", generatedWidth);
		root.put("generatedHeight", generatedHeight);
		
		// Region settings
		root.put("drawRegionColors", drawRegionColors);
		root.put("regionsRandomSeed", regionsRandomSeed);
		root.put("hueRange", hueRange);
		root.put("saturationRange", saturationRange);
		root.put("brightnessRange", brightnessRange);
		
		// Icon sets
		root.put("cityIconSetName", cityIconSetName);

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
		root.put("drawIcons", drawIcons);
		root.put("drawRoads", drawRoads);
		
		// User edits.
		if (edits != null && !skipEdits)
		{
			JSONObject editsJson = new JSONObject();
			root.put("edits", editsJson);
			editsJson.put("textEdits", textEditsToJson());
			editsJson.put("centerEdits", centerEditsToJson());
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
			if (centerEdit.icon != null)
			{
				JSONObject iconObj = new JSONObject();
				iconObj.put("iconGroupId", centerEdit.icon.iconGroupId);
				iconObj.put("iconIndex", centerEdit.icon.iconIndex);
				iconObj.put("iconName", centerEdit.icon.iconName);
				iconObj.put("iconType", centerEdit.icon.iconType.toString());
				mpObj.put("icon", iconObj);
			}
			if (centerEdit.trees != null)
			{
				JSONObject treesObj = new JSONObject();
				treesObj.put("treeType", centerEdit.trees.treeType);
				treesObj.put("density", centerEdit.trees.density);
				treesObj.put("randomSeed", centerEdit.trees.randomSeed);
				mpObj.put("trees", treesObj);
			}
			list.add(mpObj);
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
		return font.getFontName() + "\t" + font.getStyle() + "\t" + font.getSize();
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
		cityProbability = (double) root.get("cityProbability");
		lineStyle = LineStyle.valueOf((String) root.get("lineStyle"));
		pointPrecision = (double) root.get("pointPrecision");
		
		
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
			backgroundTextureImage = Paths.get(AssetsPath.get(), "example textures").toString();
		}
		backgroundRandomSeed = (long) (long) root.get("backgroundRandomSeed");
		oceanColor = parseColor((String) root.get("oceanColor"));
		landColor = parseColor((String) root.get("landColor"));
		generatedWidth = (int) (long) root.get("generatedWidth");
		generatedHeight = (int) (long) root.get("generatedHeight");
				
		drawRegionColors = (boolean) root.get("drawRegionColors");
		regionsRandomSeed = (long) root.get("regionsRandomSeed");
		hueRange = (int) (long) root.get("hueRange");
		saturationRange = (int) (long) root.get("saturationRange");
		brightnessRange = (int) (long) root.get("brightnessRange");
		drawIcons = (boolean) root.get("drawIcons");
		drawRoads = (boolean) root.get("drawRoads");
		
		if (root.containsKey("cityIconSetName"))
		{
			cityIconSetName = (String) root.get("cityIconSetName");
		}
		else
		{
			cityIconSetName = "";
		}
	
		drawText = (boolean) root.get("drawText");
		textRandomSeed = (long) root.get("textRandomSeed");
		
		JSONArray booksArray = (JSONArray) root.get("books");
		books = new TreeSet<String>(); 
		for (Object bookObject: booksArray)
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
		
		edits = new MapEdits();
		// hiddenTextIds is a comma delimited list.
				
		JSONObject editsJson = (JSONObject) root.get("edits");
		edits.text = parseMapTexts(editsJson);
		edits.centerEdits = parseCenterEdits(editsJson);
		edits.regionEdits = parseRegionEdits(editsJson);
		edits.edgeEdits = parseEdgeEdits(editsJson);
		edits.hasIconEdits = (boolean) editsJson.get("hasIconEdits");
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
			Point location = new Point((Double)jsonObj.get("locationX"), (Double)jsonObj.get("locationY"));
			double angle = (Double)jsonObj.get("angle");
			TextType type = Enum.valueOf(TextType.class, ((String)jsonObj.get("type")).replace(" ", "_"));
			MapText mp = new MapText(text, location, angle, type);
			result.add(mp);
		}
		
		return result;
	}
	
	public List<CenterEdit> parseCenterEdits(JSONObject editsJson)
	{
		if (editsJson == null)
		{
			return new ArrayList<CenterEdit>();
		}
		
		JSONArray array = (JSONArray) editsJson.get("centerEdits");
		List<CenterEdit> result = new ArrayList<>();
		int index = 0;
		for (Object obj : array)
		{
			JSONObject jsonObj = (JSONObject) obj;
			boolean isWater = jsonObj.containsKey("isWater") ? (boolean) jsonObj.get("isWater") : false;
			boolean isLake = jsonObj.containsKey("isLake") ? (boolean) jsonObj.get("isLake") : false;
			Integer regionId = jsonObj.get("regionId") == null ? null : ((Long) jsonObj.get("regionId")).intValue();
			
			CenterIcon icon = null;
			{
				JSONObject iconObj = (JSONObject)jsonObj.get("icon");
				if (iconObj != null)
				{
					String iconGroupId = (String)iconObj.get("iconGroupId");
					int iconIndex = (int)(long)iconObj.get("iconIndex");
					String iconName = (String)iconObj.get("iconName");
					CenterIconType iconType = CenterIconType.valueOf((String)iconObj.get("iconType")); 
					icon = new CenterIcon(iconType, iconGroupId, iconIndex);
					icon.iconName = iconName;
				}
			}
			
			CenterTrees trees = null;
			{
				JSONObject treesObj = (JSONObject)jsonObj.get("trees");
				if (treesObj != null)
				{
					String treeType = (String)treesObj.get("treeType");
					double density = (Double)treesObj.get("density");
					long randomSeed = (Long)treesObj.get("randomSeed");
					trees = new CenterTrees(treeType, density, randomSeed);
				}
			}
			
			result.add(new CenterEdit(index, isWater, isLake, regionId, icon, trees));
			index++;
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
			Color color = parseColor((String)jsonObj.get("color"));
			int regionId = (int)(long)jsonObj.get("regionId");
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
				riverLevel = (int)(long)jsonObj.get("riverLevel");
			}
			int index = (int)(long)jsonObj.get("index");
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
			return new Color(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		if (parts.length == 4)
		{
			return new Color(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
		}
		throw new IllegalArgumentException("Unable to parse color from string: " + str);
	}

	public static Font parseFont(String str)
	{
		String[] parts = str.split("\t");
		if (parts.length != 3)
			throw new IllegalArgumentException("Unable to parse the value of the font: \"" + str + "\"");
		Font font = new Font(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		if (parts[0].startsWith("URW Chancery") && font.getFamily().equals("Dialog"))
		{
			// Windows doesn't have URW Chancery, so change it to another font.
			return new Font("Gabriola", Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		else
		{
			return font;
		}
	}
	
	private void loadFromOldPropertiesFile(String propertiesFilePath)
	{
		OldPropertyBasedMapSettings old = new OldPropertyBasedMapSettings(propertiesFilePath);
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
		drawIcons = old.drawIcons;
		drawRivers = old.drawRivers;
		drawRoads = old.drawRoads;
		cityProbability = old.cityProbability;
		lineStyle = old.lineStyle;
		cityIconSetName = old.cityIconSetName;
		pointPrecision = old.pointPrecision;
		edits = old.edits;

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
	
	@Override
	public boolean equals(Object other)
	{
		MapSettings o = (MapSettings)other;
		
//		Helper.writeToFile("this.json", toJson());
//		Helper.writeToFile("other.json", o.toJson());
		
		return toJson().equals(o.toJson());
	}
	
	public boolean equalsIgnoringEdits(MapSettings other)
	{
		return toJson(true).equals(other.toJson(true));
	}
	
	public MapSettings deepCopy()
	{
		MapSettings copy = Helper.deepCopy(this);
		if (copy.edits != null)
		{
			copy.edits.copyMapEdits(this.edits);
		}
		
		return copy;
	}
	
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
		Jagged,
		Smooth
	}

	public enum OceanEffect
	{
		Blur,
		Ripples,
		ConcentricWaves,
	}
	
	public static final String fileExtension = "nort";
	public static final String fileExtensionWithDot = "." + fileExtension;
}
