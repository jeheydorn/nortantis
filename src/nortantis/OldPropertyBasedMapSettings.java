package nortantis;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.EdgeEdit;
import nortantis.editor.RegionEdit;
import nortantis.geom.Point;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.swing.MapEdits;
import nortantis.util.Assets;
import nortantis.util.Function0;

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
	public Color roadColor;
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
	public boolean transparentBackground;
	public boolean colorizeOcean; // For backgrounds generated from a texture.
	public boolean colorizeLand; // For backgrounds generated from a texture.
	public String backgroundTextureImage;
	public long backgroundRandomSeed;
	public Color oceanColor;
	public Color landColor;
	public int generatedWidth;
	public int generatedHeight;
	public float fractalPower;
	public String landBackgroundImage;
	public String oceanBackgroundImage;
	public int hueRange;
	public int saturationRange;
	public int brightnessRange;
	public boolean drawText;
	public boolean alwaysUpdateLandBackgroundWithOcean; // Not saved
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
	public boolean drawIcons = true;
	public boolean drawRivers = true; // Not saved
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

		randomSeed = getProperty("randomSeed", new Function0<Long>()
		{
			public Long apply()
			{
				return (long) (Long.parseLong(props.getProperty("randomSeed")));
			}
		});
		resolution = getProperty("resolution", new Function0<Double>()
		{
			public Double apply()
			{
				return Double.parseDouble(props.getProperty("resolution"));
			}
		});
		coastShadingLevel = getProperty("landBlur", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int) Integer.parseInt(props.getProperty("landBlur"));
			}
		});
		oceanEffectsLevel = getProperty("oceanEffects", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int) Integer.parseInt(props.getProperty("oceanEffects"));
			}
		});
		concentricWaveCount = getProperty("concentricWaveCount", new Function0<Integer>()
		{
			public Integer apply()
			{
				// I split concentricWaveCount out as a separate property from oceanEffectSize, so older setting files
				// won't have the former, and so it must be derived from the latter.
				if (props.getProperty("concentricWaveCount") == null || Integer.parseInt(props.getProperty("concentricWaveCount")) < 1)
				{
					return Math.max(1, oceanEffectsLevel / 14);
				}

				return (int) Integer.parseInt(props.getProperty("concentricWaveCount"));
			}
		});
		worldSize = getProperty("worldSize", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int) Integer.parseInt(props.getProperty("worldSize"));
			}
		});
		riverColor = getProperty("riverColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("riverColor"));
			}
		});
		roadColor = getProperty("roadColor", new Function0<Color>()
		{
			public Color apply()
			{
				String roadColorString = props.getProperty("roadColor");
				if (roadColorString == null || roadColorString.equals(""))
				{
					return defaultRoadColor;
				}
				return parseColor(roadColorString);
			}
		});
		coastShadingColor = getProperty("landBlurColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("landBlurColor"));
			}
		});
		oceanEffectsColor = getProperty("oceanEffectsColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("oceanEffectsColor"));
			}
		});
		coastlineColor = getProperty("coastlineColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("coastlineColor"));
			}
		});
		oceanEffect = getProperty("addWavesToOcean", new Function0<OceanWaves>()
		{
			public OceanWaves apply()
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
			}
		});
		centerLandToWaterProbability = getProperty("centerLandToWaterProbability", new Function0<Double>()
		{
			public Double apply()
			{
				return Double.parseDouble(props.getProperty("centerLandToWaterProbability"));
			}
		});
		edgeLandToWaterProbability = getProperty("edgeLandToWaterProbability", new Function0<Double>()
		{
			public Double apply()
			{
				return Double.parseDouble(props.getProperty("edgeLandToWaterProbability"));
			}
		});
		frayedBorder = getProperty("frayedBorder", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				return parseBoolean(props.getProperty("frayedBorder"));
			}
		});
		frayedBorderColor = getProperty("frayedBorderColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("frayedBorderColor"));
			}
		});
		frayedBorderBlurLevel = getProperty("frayedBorderBlurLevel", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int) (Integer.parseInt(props.getProperty("frayedBorderBlurLevel")));
			}
		});
		grungeWidth = getProperty("grungeWidth", new Function0<Integer>()
		{
			public Integer apply()
			{
				String str = props.getProperty("grungeWidth");
				return str == null ? 0 : (int) (Integer.parseInt(str));
			}
		});
		cityProbability = getProperty("cityProbability", new Function0<Double>()
		{
			public Double apply()
			{
				String str = props.getProperty("cityProbability");
				return str == null ? 0.0 : (double) (Double.parseDouble(str));
			}
		});
		lineStyle = getProperty("lineStyle", () ->
		{
			String str = props.getProperty("lineStyle");
			if (str == null || str.equals(""))
			{
				return LineStyle.Jagged;
			}
			if (str.equals("Smooth"))
			{
				return LineStyle.Splines;
			}
			return LineStyle.valueOf(str);
		});
		pointPrecision = getProperty("pointPrecision", new Function0<Double>()
		{
			public Double apply()
			{
				String str = props.getProperty("pointPrecision");
				return (str == null || str == "") ? 10.0 : (double) (Double.parseDouble(str)); // 10.0 was the value used before I made a
																								// setting for it.
			}
		});

		// Background image stuff.
		generateBackground = getProperty("generateBackground", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				return parseBoolean(props.getProperty("generateBackground"));
			}
		});
		generateBackgroundFromTexture = getProperty("generateBackgroundFromTexture", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String propString = props.getProperty("generateBackgroundFromTexture");
				if (propString == null)
				{
					return false;
				}
				return parseBoolean(propString);
			}
		});
		transparentBackground = getProperty("transparentBackground", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String propString = props.getProperty("transparentBackground");
				if (propString == null)
				{
					return false;
				}
				return parseBoolean(propString);
			}
		});
		colorizeOcean = getProperty("colorizeOcean", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String propString = props.getProperty("colorizeOcean");
				if (propString == null)
				{
					return true;
				}
				return parseBoolean(propString);
			}
		});
		colorizeLand = getProperty("colorizeLand", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String propString = props.getProperty("colorizeLand");
				if (propString == null)
				{
					return true;
				}
				return parseBoolean(propString);
			}
		});
		backgroundTextureImage = getProperty("backgroundTextureImage", new Function0<String>()
		{
			public String apply()
			{
				String result = props.getProperty("backgroundTextureImage");
				return result;
			}
		});
		backgroundRandomSeed = getProperty("backgroundRandomSeed", new Function0<Long>()
		{
			public Long apply()
			{
				return (long) (Long.parseLong(props.getProperty("backgroundRandomSeed")));
			}
		});
		oceanColor = getProperty("oceanColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("oceanColor"));
			}
		});
		landColor = getProperty("landColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("landColor"));
			}
		});
		generatedWidth = getProperty("generatedWidth", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int) (Integer.parseInt(props.getProperty("generatedWidth")));
			}
		});
		generatedHeight = getProperty("generatedHeight", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int) (Integer.parseInt(props.getProperty("generatedHeight")));
			}
		});
		fractalPower = getProperty("fractalPower", new Function0<Float>()
		{
			public Float apply()
			{
				return Float.parseFloat(props.getProperty("fractalPower"));
			}
		});
		landBackgroundImage = getProperty("landBackgroundImage", new Function0<String>()
		{
			public String apply()
			{
				String result = props.getProperty("landBackgroundImage");
				if (result == null)
					throw new NullPointerException();
				return result;
			}
		});
		oceanBackgroundImage = getProperty("oceanBackgroundImage", new Function0<String>()
		{
			public String apply()
			{
				String result = props.getProperty("oceanBackgroundImage");
				if (result == null)
					throw new NullPointerException();
				return result;
			}
		});

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
		drawIcons = getProperty("drawIcons", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String str = props.getProperty("drawIcons");
				return str == null ? true : parseBoolean(str);
			}
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

		drawText = getProperty("drawText", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				return parseBoolean(props.getProperty("drawText"));
			}
		});
		textRandomSeed = getProperty("textRandomSeed", new Function0<Long>()
		{
			public Long apply()
			{
				return (long) (Long.parseLong(props.getProperty("textRandomSeed")));
			}
		});
		books = new TreeSet<>(getProperty("books", new Function0<List<String>>()
		{
			public List<String> apply()
			{
				return Arrays.asList(props.getProperty("books").split("\t"));
			}
		}));

		titleFont = getProperty("titleFont", new Function0<Font>()
		{
			public Font apply()
			{
				return parseFont(props.getProperty("titleFont"));
			}
		});
		regionFont = getProperty("regionFont", new Function0<Font>()
		{
			public Font apply()
			{
				return parseFont(props.getProperty("regionFont"));
			}
		});

		mountainRangeFont = getProperty("mountainRangeFont", new Function0<Font>()
		{
			public Font apply()
			{
				return parseFont(props.getProperty("mountainRangeFont"));
			}
		});
		otherMountainsFont = getProperty("otherMountainsFont", new Function0<Font>()
		{
			public Font apply()
			{
				return parseFont(props.getProperty("otherMountainsFont"));
			}
		});
		riverFont = getProperty("riverFont", new Function0<Font>()
		{
			public Font apply()
			{
				return parseFont(props.getProperty("riverFont"));
			}
		});
		boldBackgroundColor = getProperty("boldBackgroundColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("boldBackgroundColor"));
			}
		});
		drawBoldBackground = getProperty("drawBoldBackground", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String value = props.getProperty("drawBoldBackground");
				if (value == null)
					return true; // default value
				return parseBoolean(value);
			}
		});
		textColor = getProperty("textColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("textColor"));
			}
		});
		drawBorder = getProperty("drawBorder", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String value = props.getProperty("drawBorder");
				if (value == null)
					return false; // default value
				return parseBoolean(value);
			}
		});
		borderType = getProperty("borderType", new Function0<String>()
		{
			public String apply()
			{
				String result = props.getProperty("borderType");
				if (result == null)
					return "";
				return result;
			}
		});
		borderWidth = getProperty("borderWidth", new Function0<Integer>()
		{
			public Integer apply()
			{
				String value = props.getProperty("borderWidth");
				if (value == null)
				{
					return 0;
				}
				return Integer.parseInt(value);
			}
		});
		frayedBorderSize = getProperty("frayedBorderSize", new Function0<Integer>()
		{
			public Integer apply()
			{
				String value = props.getProperty("frayedBorderSize");
				if (value == null)
				{
					return 10000;
				}
				return Integer.parseInt(value);
			}
		});

		edits = new MapEdits();
		// hiddenTextIds is a comma delimited list.

		edits.text = getProperty("editedText", new Function0<CopyOnWriteArrayList<MapText>>()
		{

			@Override
			public CopyOnWriteArrayList<MapText> apply()
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
					MapText mp = new MapText(text, location, angle, type, LineBreak.Auto, null, null, 0.0, 0);
					result.add(mp);
				}

				return result;
			}
		});

		edits.centerEdits = getProperty("centerEdits", new Function0<ConcurrentHashMap<Integer, CenterEdit>>()
		{
			public ConcurrentHashMap<Integer, CenterEdit> apply()
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
			}
		});

		edits.regionEdits = getProperty("regionEdits", new Function0<ConcurrentHashMap<Integer, RegionEdit>>()
		{
			public ConcurrentHashMap<Integer, RegionEdit> apply()
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
			}
		});

		edits.edgeEdits = getProperty("edgeEdits", new Function0<List<EdgeEdit>>()
		{
			public List<EdgeEdit> apply()
			{
				String str = props.getProperty("edgeEdits");
				if (str == null || str.isEmpty())
					return new ArrayList<>();
				JSONArray array = (JSONArray) JSONValue.parse(str);
				List<EdgeEdit> result = new ArrayList<>();
				for (Object obj : array)
				{
					JSONObject jsonObj = (JSONObject) obj;
					int riverLevel = (int) (long) jsonObj.get("riverLevel");
					int index = (int) (long) jsonObj.get("index");
					result.add(new EdgeEdit(index, riverLevel));
				}

				return result;
			}
		});

		edits.hasIconEdits = getProperty("hasIconEdits", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				String value = props.getProperty("hasIconEdits");
				if (value == null)
					return false; // default value
				return parseBoolean(value);
			}
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
			return Color.create(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
					Integer.parseInt(parts[3]));
		}
		throw new IllegalArgumentException("Unable to parse color from string: " + str);
	}

	private static <T> T getProperty(String propName, Function0<T> getter)
	{
		try
		{
			return getter.apply();
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
