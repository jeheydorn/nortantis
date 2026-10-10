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
 * An empty allowed set allows every choice. There are no built-in rules: the default rules are the installed Parchment theme's (see
 * {@link ThemeCatalog#getDefaultThemeRules()}).
 */
@SuppressWarnings("serial")
public class ThemeGenerationSettings implements Serializable
{
	/*
	 * How far colors vary between generated maps. Hue is in degrees, and saturation and brightness in percent. Each is the width of the
	 * range a color can move across, so a color moves at most half of it either way.
	 *
	 * The ocean variation moves the ocean color, and the ocean's wave and shading colors with it. The land variation moves the land color,
	 * and the border color with it, when the theme doesn't color political regions. When it does, the region base variation moves the region
	 * base color instead, and political regions get colors generated from it using the map's own region color ranges. No other color varies.
	 */
	public int oceanHueVariation;
	public int oceanSaturationVariation;
	public int oceanBrightnessVariation;
	public int landHueVariation;
	public int landSaturationVariation;
	public int landBrightnessVariation;
	public int regionBaseHueVariation;
	public int regionBaseSaturationVariation;
	public int regionBaseBrightnessVariation;

	/*
	 * Ocean.
	 */
	/**
	 * The ocean wave types themes can choose among. An empty {@link #allowedOceanWaveTypes} allows all of them.
	 */
	public static final List<OceanWaves> oceanWaveTypesToChooseFrom = List.of(OceanWaves.ConcentricWaves, OceanWaves.WavyLines, OceanWaves.Hatching,
			OceanWaves.Ripples, OceanWaves.SincWaves);
	public Set<OceanWaves> allowedOceanWaveTypes;
	public double drawOceanWavesProbability;
	public int oceanShadingLevelVariation;
	/**
	 * The probability of ocean shading on a map that also has ocean waves. A map without waves always gets shading. Shading and waves
	 * together render slowly.
	 */
	public double oceanShadingWithWavesProbability;

	/*
	 * Land edges, grunge, and border.
	 */
	public int coastShadingLevelVariation;
	public int grungeWidthVariation;
	public double drawBorderProbability;
	/**
	 * How far the border width can move either way from the theme's, unless the chosen border's art pack gives it a width range.
	 */
	public int borderWidthVariation;
	public double frayedBorderProbability;
	public int frayedBorderBlurLevelVariation;
	public int frayedBorderSizeVariation;

	/*
	 * Regions, roads, and background.
	 */
	public double drawRegionBoundariesProbability;
	public Set<StrokeType> allowedRegionBoundaryStrokeTypes;
	public Set<StrokeType> allowedRoadStrokeTypes;
	/**
	 * The kinds of background generated maps choose among. Each of the art pack's background textures is a choice of its own when
	 * backgrounds generated from a texture are allowed, and a fractal or solid color background is as likely as each texture.
	 */
	public Set<BackgroundType> allowedBackgroundTypes;
	public Set<LineStyle> allowedLineStyles;

	/*
	 * Text. Only the effect drawn behind title and region text varies, and each effect keeps the settings the theme has for it.
	 */
	/**
	 * Whether the effects drawn behind title and region text are shuffled whenever the theme is used. When false, they are the theme's own.
	 */
	public boolean shuffleTextBackgrounds;
	public Set<TextBackgroundEffect> allowedTitleBackgroundEffects;
	public Set<TextBackgroundEffect> allowedRegionBackgroundEffects;

	public enum BackgroundType
	{
		Fractal, GeneratedFromTexture, SolidColor
	}

	private ThemeGenerationSettings()
	{
	}

	public ThemeGenerationSettings copy()
	{
		return Helper.deepCopy(this);
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
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
	 * Reads what {@link #toJson()} wrote. Every rule is required, since every version that wrote these rules wrote all of them.
	 *
	 * <p>
	 * A rule added after the version that introduced these rules is missing from maps and themes saved before it existed, including themes
	 * in users' art packs. Such a rule needs a default for those files, for example by reading it as optional here or by setting it in a
	 * conversion in {@link MapSettings} for maps from before the version that added it, rather than being required like the others.
	 *
	 * @throws IllegalArgumentException
	 *             If a rule is missing.
	 */
	public static ThemeGenerationSettings fromJson(JSONObject obj)
	{
		ThemeGenerationSettings result = new ThemeGenerationSettings();
		result.oceanHueVariation = getInt(obj, "oceanHueVariation");
		result.oceanSaturationVariation = getInt(obj, "oceanSaturationVariation");
		result.oceanBrightnessVariation = getInt(obj, "oceanBrightnessVariation");
		result.landHueVariation = getInt(obj, "landHueVariation");
		result.landSaturationVariation = getInt(obj, "landSaturationVariation");
		result.landBrightnessVariation = getInt(obj, "landBrightnessVariation");
		result.regionBaseHueVariation = getInt(obj, "regionBaseHueVariation");
		result.regionBaseSaturationVariation = getInt(obj, "regionBaseSaturationVariation");
		result.regionBaseBrightnessVariation = getInt(obj, "regionBaseBrightnessVariation");

		result.allowedOceanWaveTypes = getEnumSet(obj, "allowedOceanWaveTypes", OceanWaves.class);
		result.drawOceanWavesProbability = getDouble(obj, "drawOceanWavesProbability");
		result.oceanShadingLevelVariation = getInt(obj, "oceanShadingLevelVariation");
		result.oceanShadingWithWavesProbability = getDouble(obj, "oceanShadingWithWavesProbability");

		result.coastShadingLevelVariation = getInt(obj, "coastShadingLevelVariation");
		result.grungeWidthVariation = getInt(obj, "grungeWidthVariation");
		result.drawBorderProbability = getDouble(obj, "drawBorderProbability");
		result.borderWidthVariation = getInt(obj, "borderWidthVariation");
		result.frayedBorderProbability = getDouble(obj, "frayedBorderProbability");
		result.frayedBorderBlurLevelVariation = getInt(obj, "frayedBorderBlurLevelVariation");
		result.frayedBorderSizeVariation = getInt(obj, "frayedBorderSizeVariation");

		result.drawRegionBoundariesProbability = getDouble(obj, "drawRegionBoundariesProbability");
		result.allowedRegionBoundaryStrokeTypes = getEnumSet(obj, "allowedRegionBoundaryStrokeTypes", StrokeType.class);
		result.allowedRoadStrokeTypes = getEnumSet(obj, "allowedRoadStrokeTypes", StrokeType.class);
		result.allowedBackgroundTypes = getEnumSet(obj, "allowedBackgroundTypes", BackgroundType.class);
		result.allowedLineStyles = getEnumSet(obj, "allowedLineStyles", LineStyle.class);

		result.shuffleTextBackgrounds = (Boolean) get(obj, "shuffleTextBackgrounds");
		result.allowedTitleBackgroundEffects = getEnumSet(obj, "allowedTitleBackgroundEffects", TextBackgroundEffect.class);
		result.allowedRegionBackgroundEffects = getEnumSet(obj, "allowedRegionBackgroundEffects", TextBackgroundEffect.class);
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

	private static Object get(JSONObject obj, String key)
	{
		if (!obj.containsKey(key))
		{
			throw new IllegalArgumentException("The rules for randomizing the theme have no '" + key + "'.");
		}
		return obj.get(key);
	}

	private static int getInt(JSONObject obj, String key)
	{
		return ((Number) get(obj, key)).intValue();
	}

	private static double getDouble(JSONObject obj, String key)
	{
		return ((Number) get(obj, key)).doubleValue();
	}

	private static <E extends Enum<E>> Set<E> getEnumSet(JSONObject obj, String key, Class<E> enumType)
	{
		Set<E> result = new LinkedHashSet<>();
		for (Object value : (JSONArray) get(obj, key))
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
