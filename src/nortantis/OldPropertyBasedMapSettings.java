package nortantis;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.editor.*;
import nortantis.geom.Point;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.swing.MapEdits;
import nortantis.util.Assets;
import java.util.function.Supplier;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The old version of MapSettings using Java properties files. This exists only for backward compatibility when loading old properties-based
 * map settings file.
 * 
 * @author joseph
 */
@SuppressWarnings("serial")
public class OldPropertyBasedMapSettings implements Serializable
{
	public static final double defaultPointPrecision = 2.0;

	public long randomSeed;
	/**
	 * A scalar multiplied by the map height and width to get the final resolution.
	 */
	public double resolution;
	public int coastShadingLevel;
	public int oceanEffectsLevel;
	public int concentricWaveCount;
	public OceanWaves oceanEffect;
	public int worldSize;
	public Color riverColor;
	public Color coastShadingColor;
	public Color oceanEffectsColor;
	public Color coastlineColor;
	public double centerLandToWaterProbability;
	public double edgeLandToWaterProbability;
	public boolean frayedBorder;
	public Color frayedBorderColor;
	public int frayedBorderBlurLevel;
	public int grungeWidth;
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
	public int frayedBorderSize;
	public boolean drawRoads = false;
	public double cityProbability;
	public LineStyle lineStyle;
	public String cityIconSetName;
	public double pointPrecision = defaultPointPrecision; // Not exposed for editing. Only for backwards compatibility so I can change it
															// without braking older settings files that have edits.

	/**
	 * Default values for new settings
	 */
	private final Color defaultRoadColor = Color.black;

	public OldPropertyBasedMapSettings(String propertiesFilename)
	{
		final Properties props;
		try
		{
			props = Assets.loadPropertiesFile(propertiesFilename);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		// Load parameters from the properties file.

		randomSeed = getProperty("randomSeed", () -> (long) (Long.parseLong(props.getProperty("randomSeed"))));
		resolution = getProperty("resolution", () -> Double.parseDouble(props.getProperty("resolution")));
		coastShadingLevel = getProperty("landBlur", () -> (int) Integer.parseInt(props.getProperty("landBlur")));
		oceanEffectsLevel = getProperty("oceanEffects", () -> (int) Integer.parseInt(props.getProperty("oceanEffects")));
		concentricWaveCount = getProperty("concentricWaveCount", () ->
		{
			// I split concentricWaveCount out as a separate property from oceanEffectSize, so older setting files
			// won't have the former, and so it must be derived from the latter.
			if (props.getProperty("concentricWaveCount") == null || Integer.parseInt(props.getProperty("concentricWaveCount")) < 1)
			{
				return Math.max(1, oceanEffectsLevel / 14);
			}

			return (int) Integer.parseInt(props.getProperty("concentricWaveCount"));
		});
		worldSize = getProperty("worldSize", () -> (int) Integer.parseInt(props.getProperty("worldSize")));
		riverColor = getProperty("riverColor", () -> parseColor(props.getProperty("riverColor")));
		coastShadingColor = getProperty("landBlurColor", () -> parseColor(props.getProperty("landBlurColor")));
		oceanEffectsColor = getProperty("oceanEffectsColor", () -> parseColor(props.getProperty("oceanEffectsColor")));
		coastlineColor = getProperty("coastlineColor", () -> parseColor(props.getProperty("coastlineColor")));
		oceanEffect = getProperty("addWavesToOcean", () ->
		{
			String str = props.getProperty("oceanEffect");
			if (str == null || str.equals(""))
			{
				// Try the old property name.
				String str2 = props.getProperty("addWavesToOcean");
				if (str2 == null || str2.equals(""))
				{
					return OceanWaves.Ripples;
				}
				return parseBoolean(str2) ? OceanWaves.Ripples : OceanWaves.Ripples;
			}
			return OceanWaves.valueOf(str);
		});
		centerLandToWaterProbability = getProperty("centerLandToWaterProbability", () -> Double.parseDouble(props.getProperty("centerLandToWaterProbability")));
		edgeLandToWaterProbability = getProperty("edgeLandToWaterProbability", () -> Double.parseDouble(props.getProperty("edgeLandToWaterProbability")));
		frayedBorder = getProperty("frayedBorder", () -> parseBoolean(props.getProperty("frayedBorder")));
		frayedBorderColor = getProperty("frayedBorderColor", () -> parseColor(props.getProperty("frayedBorderColor")));
		frayedBorderBlurLevel = getProperty("frayedBorderBlurLevel", () -> (int) (Integer.parseInt(props.getProperty("frayedBorderBlurLevel"))));
		grungeWidth = getProperty("grungeWidth", () ->
		{
			String str = props.getProperty("grungeWidth");
			return str == null ? 0 : (int) (Integer.parseInt(str));
		});
		cityProbability = getProperty("cityProbability", () ->
		{
			String str = props.getProperty("cityProbability");
			return str == null ? 0.0 : (double) (Double.parseDouble(str));
		});
		lineStyle = getProperty("lineStyle", () ->
		{
			String str = props.getProperty("lineStyle");
			if (str == null || str.isEmpty())
			{
				return LineStyle.Jagged;
			}
			if (str.equals("Smooth"))
			{
				return LineStyle.Splines;
			}
			return LineStyle.valueOf(str);
		});
		pointPrecision = getProperty("pointPrecision", () ->
		{
			String str = props.getProperty("pointPrecision");
			return (str == null || str == "") ? 10.0 : (double) (Double.parseDouble(str)); // 10.0 was the value used before I made a
																							// setting for it.
		});

		// Background image stuff.
		generateBackground = getProperty("generateBackground", () -> parseBoolean(props.getProperty("generateBackground")));
		generateBackgroundFromTexture = getProperty("generateBackgroundFromTexture", () ->
		{
			String propString = props.getProperty("generateBackgroundFromTexture");
			if (propString == null)
			{
				return false;
			}
			return parseBoolean(propString);
		});
		colorizeOcean = getProperty("colorizeOcean", () ->
		{
			String propString = props.getProperty("colorizeOcean");
			if (propString == null)
			{
				return true;
			}
			return parseBoolean(propString);
		});
		colorizeLand = getProperty("colorizeLand", () ->
		{
			String propString = props.getProperty("colorizeLand");
			if (propString == null)
			{
				return true;
			}
			return parseBoolean(propString);
		});
		backgroundTextureImage = getProperty("backgroundTextureImage", () -> props.getProperty("backgroundTextureImage"));
		backgroundRandomSeed = getProperty("backgroundRandomSeed", () -> (long) (Long.parseLong(props.getProperty("backgroundRandomSeed"))));
		oceanColor = getProperty("oceanColor", () -> parseColor(props.getProperty("oceanColor")));
		landColor = getProperty("landColor", () -> parseColor(props.getProperty("landColor")));
		generatedWidth = getProperty("generatedWidth", () -> (int) (Integer.parseInt(props.getProperty("generatedWidth"))));
		generatedHeight = getProperty("generatedHeight", () -> (int) (Integer.parseInt(props.getProperty("generatedHeight"))));

		drawRegionColors = getProperty("drawRegionColors", () ->
		{
			String str = props.getProperty("drawRegionColors");
			return str == null ? true : parseBoolean(str);
		});
		regionsRandomSeed = getProperty("regionsRandomSeed", () ->
		{
			String str = props.getProperty("regionsRandomSeed");
			return str == null ? 0 : (long) Long.parseLong(str);
		});
		hueRange = getProperty("hueRange", () ->
		{
			String str = props.getProperty("hueRange");
			return str == null ? 16 : Integer.parseInt(str); // default value
		});
		saturationRange = getProperty("saturationRange", () ->
		{
			String str = props.getProperty("saturationRange");
			return str == null ? 20 : Integer.parseInt(str); // default value
		});
		brightnessRange = getProperty("brightnessRange", () ->
		{
			String str = props.getProperty("brightnessRange");
			return str == null ? 25 : Integer.parseInt(str); // default value
		});

		cityIconSetName = getProperty("cityIconSetName", () ->
		{
			String setName;
			try
			{
				setName = props.getProperty("cityIconSetName");
			}
			catch (Exception ex)
			{
				setName = "";
			}

			if (setName == null || setName.isEmpty())
			{
				List<String> cityTypes = ImageCache.getInstance(Assets.installedArtPack, null).getIconGroupNames(IconType.cities);
				if (cityTypes.size() > 0)
				{
					setName = cityTypes.iterator().next();
				}
				else
				{
					setName = "";
				}
			}
			return setName;
		});

		drawText = getProperty("drawText", () -> parseBoolean(props.getProperty("drawText")));
		textRandomSeed = getProperty("textRandomSeed", () -> (long) (Long.parseLong(props.getProperty("textRandomSeed"))));
		books = new TreeSet<>(getProperty("books", () -> Arrays.asList(props.getProperty("books").split("\t"))));

		titleFont = getProperty("titleFont", () -> parseFont(props.getProperty("titleFont")));
		regionFont = getProperty("regionFont", () -> parseFont(props.getProperty("regionFont")));

		mountainRangeFont = getProperty("mountainRangeFont", () -> parseFont(props.getProperty("mountainRangeFont")));
		otherMountainsFont = getProperty("otherMountainsFont", () -> parseFont(props.getProperty("otherMountainsFont")));
		riverFont = getProperty("riverFont", () -> parseFont(props.getProperty("riverFont")));
		boldBackgroundColor = getProperty("boldBackgroundColor", () -> parseColor(props.getProperty("boldBackgroundColor")));
		drawBoldBackground = getProperty("drawBoldBackground", () ->
		{
			String value = props.getProperty("drawBoldBackground");
			if (value == null)
				return true; // default value
			return parseBoolean(value);
		});
		textColor = getProperty("textColor", () -> parseColor(props.getProperty("textColor")));
		drawBorder = getProperty("drawBorder", () ->
		{
			String value = props.getProperty("drawBorder");
			if (value == null)
				return false; // default value
			return parseBoolean(value);
		});
		borderType = getProperty("borderType", () ->
		{
			String result = props.getProperty("borderType");
			if (result == null)
				return "";
			return result;
		});
		borderWidth = getProperty("borderWidth", () ->
		{
			String value = props.getProperty("borderWidth");
			if (value == null)
			{
				return 0;
			}
			return Integer.parseInt(value);
		});
		frayedBorderSize = getProperty("frayedBorderSize", () ->
		{
			String value = props.getProperty("frayedBorderSize");
			if (value == null)
			{
				return 10000;
			}
			return Integer.parseInt(value);
		});

		edits = new MapEdits();
		// hiddenTextIds is a comma delimited list.

		edits.text = getProperty("editedText", () ->
		{
			String str = props.getProperty("editedText");
			if (str == null || str.isEmpty())
				return new CopyOnWriteArrayList<>();
			JSONArray array = (JSONArray) JSONValue.parse(str);
			CopyOnWriteArrayList<MapText> result = new CopyOnWriteArrayList<>();
			for (Object obj : array)
			{
				JSONObject jsonObj = (JSONObject) obj;
				String text = (String) jsonObj.get("text");
				Point location = new Point((Double) jsonObj.get("locationX"), (Double) jsonObj.get("locationY"));
				double angle = (Double) jsonObj.get("angle");
				TextType type = Enum.valueOf(TextType.class, ((String) jsonObj.get("type")).replace(" ", "_"));
				MapText mp = new MapText(text, location, angle, type, LineBreak.Auto, null, null, 0.0, 0, null, MapText.defaultBackgroundFade);
				result.add(mp);
			}

			return result;
		});

		edits.centerEdits = getProperty("centerEdits", () ->
		{
			String str = props.getProperty("centerEdits");
			if (str == null || str.isEmpty())
				return new ConcurrentHashMap<>();
			JSONArray array = (JSONArray) JSONValue.parse(str);
			ConcurrentHashMap<Integer, CenterEdit> result = new ConcurrentHashMap<>();
			int index = 0;
			for (Object obj : array)
			{
				JSONObject jsonObj = (JSONObject) obj;
				boolean isWater = (boolean) jsonObj.get("isWater");
				Boolean isLakeObject = (Boolean) jsonObj.get("isLake");
				boolean isLake = false;
				if (isLakeObject != null)
				{
					isLake = isLakeObject;
				}
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
						icon = new CenterIcon(iconType, Assets.installedArtPack, iconGroupId, iconIndex);
						if (iconName != null && !iconName.isEmpty())
						{
							icon = new CenterIcon(iconType, Assets.installedArtPack, iconGroupId, iconName);
						}
						else
						{
							icon = new CenterIcon(iconType, Assets.installedArtPack, iconGroupId, iconIndex);
						}
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
						trees = new CenterTrees(Assets.installedArtPack, treeType, density, randomSeed);
					}
				}

				result.put(index, new CenterEdit(index, isWater, isLake, regionId, icon, trees));
				index++;
			}

			return result;
		});

		edits.regionEdits = getProperty("regionEdits", () ->
		{
			String str = props.getProperty("regionEdits");
			if (str == null || str.isEmpty())
				return new ConcurrentHashMap<>();
			JSONArray array = (JSONArray) JSONValue.parse(str);
			ConcurrentHashMap<Integer, RegionEdit> result = new ConcurrentHashMap<>();
			for (Object obj : array)
			{
				JSONObject jsonObj = (JSONObject) obj;
				Color color = parseColor((String) jsonObj.get("color"));
				int regionId = (int) (long) jsonObj.get("regionId");
				result.put(regionId, new RegionEdit(regionId, color));
			}

			return result;
		});

		edits.edgeEdits = getProperty("edgeEdits", () ->
		{
			String str = props.getProperty("edgeEdits");
			if (str == null || str.isEmpty())
				return new TreeMap<>();
			JSONArray array = (JSONArray) JSONValue.parse(str);
			Map<Integer, EdgeEdit> result = new TreeMap<>();
			for (Object obj : array)
			{
				JSONObject jsonObj = (JSONObject) obj;
				int riverLevel = (int) (long) jsonObj.get("riverLevel");
				int index = (int) (long) jsonObj.get("index");
				result.put(index, new EdgeEdit(index, riverLevel));
			}

			return result;
		});

		edits.hasIconEdits = getProperty("hasIconEdits", () ->
		{
			String value = props.getProperty("hasIconEdits");
			if (value == null)
				return false; // default value
			return parseBoolean(value);
		});
	}

	private static boolean parseBoolean(String str)
	{
		if (str == null)
			throw new NullPointerException();
		if (!(str.equals("true") || str.equals("false")))
			throw new IllegalArgumentException();
		return (boolean) Boolean.parseBoolean(str);
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
			return Color.create(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
		}
		throw new IllegalArgumentException("Unable to parse color from string: " + str);
	}

	private static <T> T getProperty(String propName, Supplier<T> getter)
	{
		try
		{
			return getter.get();
		}
		catch (NullPointerException e)
		{
			throw new RuntimeException("Property \"" + propName + "\" is missing or cannot be read.", e);
		}
		catch (NumberFormatException e)
		{
			if (e.getMessage().equals("null"))
				throw new RuntimeException("Property \"" + propName + "\" is missing.", e);
			else
				throw new RuntimeException("Property \"" + propName + "\" is invalid.", e);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Property \"" + propName + "\" is invalid.", e);
		}
	}

	public static Font parseFont(String str)
	{
		String[] parts = str.split("\t");
		if (parts.length != 3)
			throw new IllegalArgumentException("Unable to parse the value of the font: \"" + str + "\"");
		Font font = Font.create(parts[0], FontStyle.fromNumber(Integer.parseInt(parts[1])), Integer.parseInt(parts[2]));
		if (parts[0].startsWith("URW Chancery") && font.getFamily().equals("Dialog"))
		{
			// Windows doesn't have URW Chancery, so change it to another font.
			return Font.create("Gabriola", FontStyle.fromNumber(Integer.parseInt(parts[1])), Integer.parseInt(parts[2]));
		}
		else
		{
			return font;
		}
	}
}
