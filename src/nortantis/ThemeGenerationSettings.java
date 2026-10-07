package nortantis;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.util.Helper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

/**
 * How a theme varies: for each setting that varies, how far it can move from the theme's own value, a set of allowed choices, or a
 * probability. A map carries these so that Randomize Theme can vary its look, and so that new random maps made from it, when it is in an art
 * pack's themes folder, vary the same way.
 *
 * <p>
 * An empty allowed set allows every choice.
 */
@SuppressWarnings("serial")
public class ThemeGenerationSettings implements Serializable
{
	/**
	 * The art pack this theme's art comes from, or null if it has never been chosen. Separate from {@link MapSettings#artPack}, which is
	 * which art pack the Icons tool starts on.
	 */
	public String artPack;

	/*
	 * How far colors vary between generated maps. Hue is in degrees, and saturation and brightness in percent. Each is the width of the
	 * range a color can move across, so a color moves at most half of it either way.
	 *
	 * The ocean variation moves the ocean color, and the ocean's wave and shading colors with it. The land variation moves the land color,
	 * and the border color with it, when the theme doesn't color political regions. Political regions get colors generated from the region
	 * base color using the map's own region color ranges instead. No other color varies.
	 */
	public int oceanHueVariation = 16;
	public int oceanSaturationVariation = 10;
	public int oceanBrightnessVariation = 10;
	public int landHueVariation = 16;
	public int landSaturationVariation = 10;
	public int landBrightnessVariation = 10;

	/*
	 * Ocean.
	 */
	public Set<OceanWaves> allowedOceanWaveTypes = new LinkedHashSet<>(
			Arrays.asList(OceanWaves.ConcentricWaves, OceanWaves.WavyLines, OceanWaves.Hatching, OceanWaves.Ripples));
	public double drawOceanWavesProbability = 0.8;
	public int oceanShadingLevelVariation = 20;
	/**
	 * The probability of ocean shading on a map that also has ocean waves. A map without waves always gets shading. Shading and waves
	 * together render slowly, so this is 0 by default.
	 */
	public double oceanShadingWithWavesProbability = 0.0;

	/*
	 * Land edges, grunge, and border.
	 */
	public int coastShadingLevelVariation = 17;
	public int grungeWidthVariation = 700;
	public double drawBorderProbability = 0.75;
	public Set<String> allowedBorderNames = new LinkedHashSet<>();
	public double frayedBorderProbability = 0.5;
	public int frayedBorderBlurLevelVariation = 75;
	public int frayedBorderSizeVariation = 3;

	/*
	 * Regions, roads, and background.
	 */
	public double drawRegionBoundariesProbability = 0.75;
	public Set<StrokeType> allowedRegionBoundaryStrokeTypes = new LinkedHashSet<>();
	public Set<StrokeType> allowedRoadStrokeTypes = new LinkedHashSet<>();
	/**
	 * Whether a fractal background is one of the backgrounds generated maps choose among, as likely as each background texture.
	 */
	public boolean allowFractalBackground = true;
	public Set<String> allowedBackgroundTextureNames = new LinkedHashSet<>();
	public Set<LineStyle> allowedLineStyles = new LinkedHashSet<>();

	/**
	 * The rules that reproduce how new random maps were generated before themes existed.
	 */
	public static ThemeGenerationSettings createDefault()
	{
		return new ThemeGenerationSettings();
	}

	public ThemeGenerationSettings copy()
	{
		return Helper.deepCopy(this);
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
		obj.put("artPack", artPack);
		obj.put("oceanHueVariation", oceanHueVariation);
		obj.put("oceanSaturationVariation", oceanSaturationVariation);
		obj.put("oceanBrightnessVariation", oceanBrightnessVariation);
		obj.put("landHueVariation", landHueVariation);
		obj.put("landSaturationVariation", landSaturationVariation);
		obj.put("landBrightnessVariation", landBrightnessVariation);

		obj.put("allowedOceanWaveTypes", toJsonArray(allowedOceanWaveTypes, Enum::name));
		obj.put("drawOceanWavesProbability", drawOceanWavesProbability);
		obj.put("oceanShadingLevelVariation", oceanShadingLevelVariation);
		obj.put("oceanShadingWithWavesProbability", oceanShadingWithWavesProbability);

		obj.put("coastShadingLevelVariation", coastShadingLevelVariation);
		obj.put("grungeWidthVariation", grungeWidthVariation);
		obj.put("drawBorderProbability", drawBorderProbability);
		obj.put("allowedBorderNames", toJsonArray(allowedBorderNames, name -> name));
		obj.put("frayedBorderProbability", frayedBorderProbability);
		obj.put("frayedBorderBlurLevelVariation", frayedBorderBlurLevelVariation);
		obj.put("frayedBorderSizeVariation", frayedBorderSizeVariation);

		obj.put("drawRegionBoundariesProbability", drawRegionBoundariesProbability);
		obj.put("allowedRegionBoundaryStrokeTypes", toJsonArray(allowedRegionBoundaryStrokeTypes, Enum::name));
		obj.put("allowedRoadStrokeTypes", toJsonArray(allowedRoadStrokeTypes, Enum::name));
		obj.put("allowFractalBackground", allowFractalBackground);
		obj.put("allowedBackgroundTextureNames", toJsonArray(allowedBackgroundTextureNames, name -> name));
		obj.put("allowedLineStyles", toJsonArray(allowedLineStyles, Enum::name));
		return obj;
	}

	/**
	 * Reads what {@link #toJson()} wrote. Settings missing from the JSON keep their defaults, so a file written by an older version loads.
	 */
	public static ThemeGenerationSettings fromJson(JSONObject obj)
	{
		ThemeGenerationSettings result = createDefault();
		if (obj == null)
		{
			return result;
		}

		result.artPack = (String) obj.get("artPack");
		result.oceanHueVariation = getInt(obj, "oceanHueVariation", result.oceanHueVariation);
		result.oceanSaturationVariation = getInt(obj, "oceanSaturationVariation", result.oceanSaturationVariation);
		result.oceanBrightnessVariation = getInt(obj, "oceanBrightnessVariation", result.oceanBrightnessVariation);
		result.landHueVariation = getInt(obj, "landHueVariation", result.landHueVariation);
		result.landSaturationVariation = getInt(obj, "landSaturationVariation", result.landSaturationVariation);
		result.landBrightnessVariation = getInt(obj, "landBrightnessVariation", result.landBrightnessVariation);

		result.allowedOceanWaveTypes = getEnumSet(obj, "allowedOceanWaveTypes", OceanWaves.class, result.allowedOceanWaveTypes);
		result.drawOceanWavesProbability = getDouble(obj, "drawOceanWavesProbability", result.drawOceanWavesProbability);
		result.oceanShadingLevelVariation = getInt(obj, "oceanShadingLevelVariation", result.oceanShadingLevelVariation);
		result.oceanShadingWithWavesProbability = getDouble(obj, "oceanShadingWithWavesProbability", result.oceanShadingWithWavesProbability);

		result.coastShadingLevelVariation = getInt(obj, "coastShadingLevelVariation", result.coastShadingLevelVariation);
		result.grungeWidthVariation = getInt(obj, "grungeWidthVariation", result.grungeWidthVariation);
		result.drawBorderProbability = getDouble(obj, "drawBorderProbability", result.drawBorderProbability);
		result.allowedBorderNames = getStringSet(obj, "allowedBorderNames", result.allowedBorderNames);
		result.frayedBorderProbability = getDouble(obj, "frayedBorderProbability", result.frayedBorderProbability);
		result.frayedBorderBlurLevelVariation = getInt(obj, "frayedBorderBlurLevelVariation", result.frayedBorderBlurLevelVariation);
		result.frayedBorderSizeVariation = getInt(obj, "frayedBorderSizeVariation", result.frayedBorderSizeVariation);

		result.drawRegionBoundariesProbability = getDouble(obj, "drawRegionBoundariesProbability", result.drawRegionBoundariesProbability);
		result.allowedRegionBoundaryStrokeTypes = getEnumSet(obj, "allowedRegionBoundaryStrokeTypes", StrokeType.class, result.allowedRegionBoundaryStrokeTypes);
		result.allowedRoadStrokeTypes = getEnumSet(obj, "allowedRoadStrokeTypes", StrokeType.class, result.allowedRoadStrokeTypes);
		if (obj.containsKey("allowFractalBackground"))
		{
			result.allowFractalBackground = (Boolean) obj.get("allowFractalBackground");
		}
		result.allowedBackgroundTextureNames = getStringSet(obj, "allowedBackgroundTextureNames", result.allowedBackgroundTextureNames);
		result.allowedLineStyles = getEnumSet(obj, "allowedLineStyles", LineStyle.class, result.allowedLineStyles);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static <T> JSONArray toJsonArray(Collection<T> values, Function<T, String> toString)
	{
		JSONArray array = new JSONArray();
		for (T value : values)
		{
			array.add(toString.apply(value));
		}
		return array;
	}

	private static int getInt(JSONObject obj, String key, int defaultValue)
	{
		return obj.containsKey(key) ? ((Number) obj.get(key)).intValue() : defaultValue;
	}

	private static double getDouble(JSONObject obj, String key, double defaultValue)
	{
		return obj.containsKey(key) ? ((Number) obj.get(key)).doubleValue() : defaultValue;
	}

	private static Set<String> getStringSet(JSONObject obj, String key, Set<String> defaultValue)
	{
		if (!obj.containsKey(key))
		{
			return defaultValue;
		}
		Set<String> result = new LinkedHashSet<>();
		for (Object value : (JSONArray) obj.get(key))
		{
			result.add((String) value);
		}
		return result;
	}

	private static <E extends Enum<E>> Set<E> getEnumSet(JSONObject obj, String key, Class<E> enumType, Set<E> defaultValue)
	{
		if (!obj.containsKey(key))
		{
			return defaultValue;
		}
		Set<E> result = new LinkedHashSet<>();
		for (Object value : (JSONArray) obj.get(key))
		{
			try
			{
				result.add(Enum.valueOf(enumType, (String) value));
			}
			catch (IllegalArgumentException e)
			{
				// A choice this version doesn't know, from a theme made in a newer version, is left out.
			}
		}
		return result;
	}

	@Override
	public int hashCode()
	{
		return toJson().hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null || getClass() != obj.getClass())
		{
			return false;
		}
		return toJson().equals(((ThemeGenerationSettings) obj).toJson());
	}
}
