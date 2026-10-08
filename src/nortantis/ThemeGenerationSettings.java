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
	 * and the border color with it, when the theme doesn't color political regions. When it does, the region base variation moves the region
	 * base color instead, and political regions get colors generated from it using the map's own region color ranges. No other color varies.
	 */
	public int oceanHueVariation = 16;
	public int oceanSaturationVariation = 10;
	public int oceanBrightnessVariation = 10;
	public int landHueVariation = 16;
	public int landSaturationVariation = 10;
	public int landBrightnessVariation = 10;
	public int regionBaseHueVariation = 16;
	public int regionBaseSaturationVariation = 10;
	public int regionBaseBrightnessVariation = 10;

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
	/**
	 * How far the border width can move either way from the theme's, unless the chosen border's art pack gives it a width range.
	 */
	public int borderWidthVariation = 100;
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
	 * The kinds of background generated maps choose among. Each of the art pack's background textures is a choice of its own when
	 * backgrounds generated from a texture are allowed, and a fractal or solid color background is as likely as each texture.
	 */
	public Set<BackgroundType> allowedBackgroundTypes = new LinkedHashSet<>(Arrays.asList(BackgroundType.Fractal, BackgroundType.GeneratedFromTexture));
	public Set<LineStyle> allowedLineStyles = new LinkedHashSet<>();

	/*
	 * Text. Only the effect drawn behind title and region text varies, and each effect keeps the settings the theme has for it.
	 */
	/**
	 * Whether the effects drawn behind title and region text are shuffled whenever the theme is used. When false, they are the theme's own.
	 */
	public boolean shuffleTextBackgrounds = false;
	public Set<TextBackgroundEffect> allowedTitleBackgroundEffects = createDefaultAllowedTextBackgroundEffects();
	public Set<TextBackgroundEffect> allowedRegionBackgroundEffects = createDefaultAllowedTextBackgroundEffects();

	private static Set<TextBackgroundEffect> createDefaultAllowedTextBackgroundEffects()
	{
		Set<TextBackgroundEffect> result = new LinkedHashSet<>(Arrays.asList(TextBackgroundEffect.values()));
		result.remove(TextBackgroundEffect.BoldBackground);
		return result;
	}

	public enum BackgroundType
	{
		Fractal, GeneratedFromTexture, SolidColor
	}

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
		obj.put("regionBaseHueVariation", regionBaseHueVariation);
		obj.put("regionBaseSaturationVariation", regionBaseSaturationVariation);
		obj.put("regionBaseBrightnessVariation", regionBaseBrightnessVariation);

		obj.put("allowedOceanWaveTypes", toJsonArray(allowedOceanWaveTypes, Enum::name));
		obj.put("drawOceanWavesProbability", drawOceanWavesProbability);
		obj.put("oceanShadingLevelVariation", oceanShadingLevelVariation);
		obj.put("oceanShadingWithWavesProbability", oceanShadingWithWavesProbability);

		obj.put("coastShadingLevelVariation", coastShadingLevelVariation);
		obj.put("grungeWidthVariation", grungeWidthVariation);
		obj.put("drawBorderProbability", drawBorderProbability);
		obj.put("allowedBorderNames", toJsonArray(allowedBorderNames, name -> name));
		obj.put("borderWidthVariation", borderWidthVariation);
		obj.put("frayedBorderProbability", frayedBorderProbability);
		obj.put("frayedBorderBlurLevelVariation", frayedBorderBlurLevelVariation);
		obj.put("frayedBorderSizeVariation", frayedBorderSizeVariation);

		obj.put("drawRegionBoundariesProbability", drawRegionBoundariesProbability);
		obj.put("allowedRegionBoundaryStrokeTypes", toJsonArray(allowedRegionBoundaryStrokeTypes, Enum::name));
		obj.put("allowedRoadStrokeTypes", toJsonArray(allowedRoadStrokeTypes, Enum::name));
		obj.put("allowedBackgroundTypes", toJsonArray(allowedBackgroundTypes, Enum::name));
		obj.put("allowedLineStyles", toJsonArray(allowedLineStyles, Enum::name));

		obj.put("shuffleTextBackgrounds", shuffleTextBackgrounds);
		obj.put("allowedTitleBackgroundEffects", toJsonArray(allowedTitleBackgroundEffects, Enum::name));
		obj.put("allowedRegionBackgroundEffects", toJsonArray(allowedRegionBackgroundEffects, Enum::name));
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
		result.regionBaseHueVariation = getInt(obj, "regionBaseHueVariation", result.regionBaseHueVariation);
		result.regionBaseSaturationVariation = getInt(obj, "regionBaseSaturationVariation", result.regionBaseSaturationVariation);
		result.regionBaseBrightnessVariation = getInt(obj, "regionBaseBrightnessVariation", result.regionBaseBrightnessVariation);

		result.allowedOceanWaveTypes = getEnumSet(obj, "allowedOceanWaveTypes", OceanWaves.class, result.allowedOceanWaveTypes);
		result.drawOceanWavesProbability = getDouble(obj, "drawOceanWavesProbability", result.drawOceanWavesProbability);
		result.oceanShadingLevelVariation = getInt(obj, "oceanShadingLevelVariation", result.oceanShadingLevelVariation);
		result.oceanShadingWithWavesProbability = getDouble(obj, "oceanShadingWithWavesProbability", result.oceanShadingWithWavesProbability);

		result.coastShadingLevelVariation = getInt(obj, "coastShadingLevelVariation", result.coastShadingLevelVariation);
		result.grungeWidthVariation = getInt(obj, "grungeWidthVariation", result.grungeWidthVariation);
		result.drawBorderProbability = getDouble(obj, "drawBorderProbability", result.drawBorderProbability);
		result.allowedBorderNames = getStringSet(obj, "allowedBorderNames", result.allowedBorderNames);
		result.borderWidthVariation = getInt(obj, "borderWidthVariation", result.borderWidthVariation);
		result.frayedBorderProbability = getDouble(obj, "frayedBorderProbability", result.frayedBorderProbability);
		result.frayedBorderBlurLevelVariation = getInt(obj, "frayedBorderBlurLevelVariation", result.frayedBorderBlurLevelVariation);
		result.frayedBorderSizeVariation = getInt(obj, "frayedBorderSizeVariation", result.frayedBorderSizeVariation);

		result.drawRegionBoundariesProbability = getDouble(obj, "drawRegionBoundariesProbability", result.drawRegionBoundariesProbability);
		result.allowedRegionBoundaryStrokeTypes = getEnumSet(obj, "allowedRegionBoundaryStrokeTypes", StrokeType.class, result.allowedRegionBoundaryStrokeTypes);
		result.allowedRoadStrokeTypes = getEnumSet(obj, "allowedRoadStrokeTypes", StrokeType.class, result.allowedRoadStrokeTypes);
		result.allowedBackgroundTypes = getEnumSet(obj, "allowedBackgroundTypes", BackgroundType.class, result.allowedBackgroundTypes);
		result.allowedLineStyles = getEnumSet(obj, "allowedLineStyles", LineStyle.class, result.allowedLineStyles);

		if (obj.containsKey("shuffleTextBackgrounds"))
		{
			result.shuffleTextBackgrounds = (Boolean) obj.get("shuffleTextBackgrounds");
		}
		result.allowedTitleBackgroundEffects = getEnumSet(obj, "allowedTitleBackgroundEffects", TextBackgroundEffect.class, result.allowedTitleBackgroundEffects);
		result.allowedRegionBackgroundEffects = getEnumSet(obj, "allowedRegionBackgroundEffects", TextBackgroundEffect.class, result.allowedRegionBackgroundEffects);
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
