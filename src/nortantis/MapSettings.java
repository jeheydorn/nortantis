package nortantis;

import hoten.geom.Point;

import java.awt.Color;
import java.awt.Font;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import util.Function0;
import util.Helper;

/**
 * For parsing and storing map settings.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapSettings implements Serializable
{
	long randomSeed;
	/**
	 *  A scalar multiplied by the map height and width to get the final resolution.
	 */
	double resolution;
	int landBlur;
	int oceanEffects;
	boolean addWavesToOcean;
	int worldSize;
	Color riverColor;
	Color landBlurColor;
	Color oceanEffectsColor;
	Color coastlineColor;
	double centerLandToWaterProbability;
	double edgeLandToWaterProbability;
	boolean frayedBorder;
	Color frayedBorderColor;
	int frayedBorderBlurLevel;
	public int grungeWidth;
	
	/**
	 * This settings actually mans fractal generated as opposed to generated from texture.
	 */
	boolean generateBackground;
	boolean generateBackgroundFromTexture;
	boolean colorizeOcean; // For backgrounds generated from a texture.
	boolean colorizeLand; // For backgrounds generated from a texture.
	String backgroundTextureImage;
	long backgroundRandomSeed;
	Color oceanColor;
	Color landColor;
	int generatedWidth;
	int generatedHeight;
	float fractalPower;
	String landBackgroundImage;
	String oceanBackgroundImage;
	int hueRange;
	int saturationRange;
	int brightnessRange;
	
	boolean drawText;
	long textRandomSeed;
	Set<String> books;
	Font titleFont;
	Font regionFont;
	Font mountainRangeFont;
	Font otherMountainsFont;
	Font riverFont;
	Color boldBackgroundColor;
	Color textColor;
	MapEdits edits;
	boolean drawBoldBackground;
	boolean drawRegionColors;
	long regionsRandomSeed;
	
	public MapSettings()
	{
		edits = new MapEdits();
	}
	
	public Properties toPropertiesFile()
	{
		Properties result = new Properties();
		result.setProperty("randomSeed", randomSeed + "");
		result.setProperty("resolution", resolution + "");
		result.setProperty("landBlur", landBlur + "");
		result.setProperty("oceanEffects", oceanEffects + "");
		result.setProperty("addWavesToOcean", addWavesToOcean + "");
		result.setProperty("worldSize", worldSize + "");
		result.setProperty("riverColor", colorToString(riverColor));
		result.setProperty("landBlurColor", colorToString(landBlurColor));
		result.setProperty("oceanEffectsColor", colorToString(oceanEffectsColor));
		result.setProperty("coastlineColor", colorToString(coastlineColor));
		result.setProperty("edgeLandToWaterProbability", edgeLandToWaterProbability + "");
		result.setProperty("centerLandToWaterProbability", centerLandToWaterProbability + "");
		result.setProperty("frayedBorder", frayedBorder + "");
		result.setProperty("frayedBorderColor", colorToString(frayedBorderColor));
		result.setProperty("frayedBorderBlurLevel", frayedBorderBlurLevel + "");
		result.setProperty("grungeWidth", grungeWidth + "");

		// Background image settings.
		result.setProperty("backgroundRandomSeed", backgroundRandomSeed + "");
		result.setProperty("generateBackground", generateBackground + "");
		result.setProperty("backgroundTextureImage", backgroundTextureImage);
		result.setProperty("generateBackgroundFromTexture", generateBackgroundFromTexture + "");
		result.setProperty("colorizeOcean", colorizeOcean + "");
		result.setProperty("colorizeLand", colorizeLand + "");
		result.setProperty("oceanColor", colorToString(oceanColor));
		result.setProperty("landColor", colorToString(landColor));
		result.setProperty("generatedWidth", generatedWidth + "");
		result.setProperty("generatedHeight", generatedHeight + "");
		result.setProperty("fractalPower", fractalPower + "");
		result.setProperty("landBackgroundImage", landBackgroundImage);
		result.setProperty("oceanBackgroundImage", oceanBackgroundImage);
		
		// Region settings
		result.setProperty("drawRegionColors", drawRegionColors + "");
		result.setProperty("regionsRandomSeed", regionsRandomSeed + "");
		result.setProperty("hueRange", hueRange + "");
		result.setProperty("saturationRange", saturationRange + "");
		result.setProperty("brightnessRange", brightnessRange + "");
		

		result.setProperty("drawText", drawText + "");
		result.setProperty("textRandomSeed", textRandomSeed + "");
		result.setProperty("books", Helper.toStringWithSeparator(books, "\t"));
		result.setProperty("titleFont", fontToString(titleFont));
		result.setProperty("regionFont", fontToString(regionFont));
		result.setProperty("mountainRangeFont", fontToString(mountainRangeFont));
		result.setProperty("otherMountainsFont", fontToString(otherMountainsFont));
		result.setProperty("riverFont", fontToString(riverFont));
		result.setProperty("boldBackgroundColor", colorToString(boldBackgroundColor));
		result.setProperty("drawBoldBackground", drawBoldBackground + "");
		result.setProperty("textColor", colorToString(textColor));
		
		// User edits.
		result.setProperty("editedText", editedTextToString());
		
		return result;
	}
	
	/**
	 * Stores editedText as a '\n' delimited list of pairs, each delimited by ',';
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String editedTextToString()
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
		String json = list.toJSONString();
		return json;

	}
	
	private String colorToString(Color c)
	{
		return c.getRed() + "," + c.getGreen() + "," + c.getBlue();
	}
	
	private String fontToString(Font font)
	{
		return font.getFontName() + "\t" + font.getStyle() + "\t" + font.getSize();
	}
		
	public MapSettings(String propertiesFilename)
	{
		final Properties props = new Properties();
		try
		{
			props.load(new FileInputStream(propertiesFilename));
		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		// Load parameters from the properties file.
		
		randomSeed = getProperty("randomSeed", new Function0<Long>()
		{
			public Long apply()
			{
				return (long)(Long.parseLong(props.getProperty("randomSeed")));
			}
		});
		resolution = getProperty("resolution", new Function0<Double>()
		{
			public Double apply()
			{
				return Double.parseDouble(props.getProperty("resolution"));
			}
		});
		landBlur = getProperty("landBlur", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int)(Integer.parseInt(props.getProperty("landBlur")));
			}
		});
		oceanEffects = getProperty("oceanEffects", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int)(Integer.parseInt(props.getProperty("oceanEffects")));
			}
		});
		worldSize = getProperty("worldSize", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int)Integer.parseInt(props.getProperty("worldSize"));
			}
		});
		riverColor = getProperty("riverColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("riverColor"));
			}
		});
		landBlurColor = getProperty("landBlurColor", new Function0<Color>()
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
		addWavesToOcean = getProperty("addWavesToOcean", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				return parseBoolean(props.getProperty("addWavesToOcean"));
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
				return (int)(Integer.parseInt(props.getProperty("frayedBorderBlurLevel")));
			}
		});
		grungeWidth = getProperty("grungeWidth", new Function0<Integer>()
		{
			public Integer apply()
			{
				String str = props.getProperty("grungeWidth");
				return str == null ? 0 : (int)(Integer.parseInt(str));
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
				if (result == null)
					result = ".";
				return result;
			}
		});
		backgroundRandomSeed = getProperty("backgroundRandomSeed", new Function0<Long>()
		{
			public Long apply()
			{
				return (long)(Long.parseLong(props.getProperty("backgroundRandomSeed")));
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
				return (int)(Integer.parseInt(props.getProperty("generatedWidth")));
			}
		});
		generatedHeight = getProperty("generatedHeight", new Function0<Integer>()
		{
			public Integer apply()
			{
				return (int)(Integer.parseInt(props.getProperty("generatedHeight")));
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
			return str == null ? 0 : (long)Long.parseLong(str);			
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
				return (long)(Long.parseLong(props.getProperty("textRandomSeed")));
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
				return  parseBoolean(value);
			}
		});
		textColor = getProperty("textColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("textColor"));
			}
		});
		
		edits = new MapEdits();
		// hiddenTextIds is a comma seperated list.
				
		edits.text = getProperty("editedText", new Function0<List<MapText>>()
		{
	
			@Override
			public List<MapText> apply()
			{
				String str = props.getProperty("editedText");
				if (str == null || str.isEmpty())
					return new ArrayList<>();
				JSONArray array = (JSONArray) JSONValue.parse(str);
				List<MapText> result = new ArrayList<>();
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
	
		});
	}
	
	private static boolean parseBoolean(String str)
	{
		if (str == null)
			throw new NullPointerException();
		if (!(str.equals("true") || str.equals("false")))
			throw new IllegalArgumentException();
		return (boolean)Boolean.parseBoolean(str);		
	}
	
	private static Color parseColor(String str)
	{
		if (str == null)
			throw new NullPointerException("A color is null.");
		String[] parts = str.split(",");
		if (parts.length != 3)
			throw new IllegalArgumentException("Unable to parse color from string: " + str);
		return new Color(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
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
		catch(NumberFormatException e)
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
		// They don't have the font in their system. Return a font that looks good in Windows.
//		Logger.println("Cannot find font: \"" + parts[0] + "\". A default font will be used instead.");
//		return new Font("Gabriola", Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
	}
	
	@Override
	public boolean equals(Object other)
	{
		MapSettings o = (MapSettings)other;
		return toPropertiesFile().equals(o.toPropertiesFile());
	}
	

}
