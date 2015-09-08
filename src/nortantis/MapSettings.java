package nortantis;

import java.awt.Color;
import java.awt.Font;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
	
	boolean generateBackground;
	long backgroundRandomSeed;
	Color oceanColor;
	Color landColor;
	int generatedWidth;
	int generatedHeight;
	float fractalPower;
	String landBackgroundImage;
	String oceanBackgroundImage;
	
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

		// Background image settings.
		result.setProperty("backgroundRandomSeed", backgroundRandomSeed + "");
		result.setProperty("generateBackground", generateBackground + "");
		result.setProperty("oceanColor", colorToString(oceanColor));
		result.setProperty("landColor", colorToString(landColor));
		result.setProperty("generatedWidth", generatedWidth + "");
		result.setProperty("generatedHeight", generatedHeight + "");
		result.setProperty("fractalPower", fractalPower + "");
		result.setProperty("landBackgroundImage", landBackgroundImage);
		result.setProperty("oceanBackgroundImage", oceanBackgroundImage);

		result.setProperty("drawText", drawText + "");
		result.setProperty("textRandomSeed", textRandomSeed + "");
		result.setProperty("books", Helper.toStringWithSeparator(books, "\t"));
		result.setProperty("titleFont", fontToString(titleFont));
		result.setProperty("regionFont", fontToString(regionFont));
		result.setProperty("mountainRangeFont", fontToString(mountainRangeFont));
		result.setProperty("otherMountainsFont", fontToString(otherMountainsFont));
		result.setProperty("riverFont", fontToString(riverFont));
		result.setProperty("boldBackgroundColor", colorToString(boldBackgroundColor));
		result.setProperty("textColor", colorToString(textColor));
		
		// User edits.
		result.setProperty("hiddenTextIds", Helper.toStringWithSeparator(edits.hiddenTextIds, ","));
		result.setProperty("editedText", editedTextToString());
		
		return result;
	}
	
	/**
	 * Stores editedText as a '\n' delimited list of pairs, each delimited by ',';
	 * @return
	 */
	private String editedTextToString()
	{
		StringBuilder b = new StringBuilder();
		for (Entry<Integer, MapText> entry : edits.editedText.entrySet())
		{
			b.append(entry.getKey());
			b.append(",");
			b.append(entry.getValue().text);
			b.append("<end>");
		}
		return b.toString();
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
		
		// Background image stuff.
		generateBackground = getProperty("generateBackground", new Function0<Boolean>()
		{
			public Boolean apply()
			{
				return parseBoolean(props.getProperty("generateBackground"));
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
		textColor = getProperty("textColor", new Function0<Color>()
		{
			public Color apply()
			{
				return parseColor(props.getProperty("textColor"));
			}
		});
		
		edits = new MapEdits();
		// hiddenTextIds is a comma seperated list.
		edits.hiddenTextIds = getProperty("hiddenTextIds", new Function0<Set<Integer>>()
		{
			@Override
			public Set<Integer> apply()
			{
				String str = props.getProperty("hiddenTextIds");
				Set<Integer> result = new TreeSet<>();
				if (str == null || str.isEmpty())
					return result;
				for (String part : str.split(","))
					result.add(Integer.parseInt(part));
				return result;
			}	
		});
				
		edits.editedText = getProperty("editedText", new Function0<Map<Integer, MapText>>()
		{
	
			@Override
			public Map<Integer, MapText> apply()
			{
				Map<Integer, MapText> result = new TreeMap<>();
				String str = props.getProperty("editedText");
				if (str == null || str.isEmpty())
					return result;
				for (String part : str.split("<end>"))
				{
					if (part.isEmpty())
						continue;
					int i = part.indexOf(',');
					if (i == -1)
						throw new IllegalArgumentException("Unable to read edited text because ',' could not be found.");
					int id = Integer.parseInt(part.substring(0, i));
					String name = part.substring(i+1, part.length());
					result.put(id, new MapText(id, name, null));
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
			throw new RuntimeException("Property \"" + propName + "\" is missing.", e);			
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
