package nortantis;

import nortantis.platform.Color;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.util.Objects;

/**
 * What is drawn behind a piece of text: one effect, plus background fade. Each family of effects (see {@link TextBackgroundEffect}) keeps its
 * own settings, so switching to an effect in another family and back restores what the user had.
 *
 * <p>
 * Sizes and widths are levels that are multiplied by the font's height when drawing, so an effect looks the same at every resolution.
 */
@SuppressWarnings("serial")
public class TextBackground implements Serializable
{
	public static final double defaultFade = 1.0;
	public static final int maxHaloSize = 30;
	public static final int defaultHaloSize = 8;
	public static final int maxShapeLineWidth = 10;
	public static final int defaultShapeLineWidth = 4;
	public static final int maxShapeJitter = 10;
	public static final int defaultShapeJitter = 4;
	public static final Color defaultHaloColor = Color.create(244, 232, 204, 255);
	public static final Color defaultBoldColor = Color.create(244, 226, 194, 255);
	public static final Color defaultShapeFillColor = Color.create(238, 224, 189, 255);
	public static final Color defaultShapeLineColor = Color.create(64, 46, 30, 255);

	public TextBackgroundEffect effect;
	/**
	 * How much to fade out icons, rivers, roads, and coastlines around the text. Only used when {@link TextBackgroundEffect#allowsFade()}.
	 */
	public double fade;

	/** The color of Glow and Outline. */
	public Color haloColor;
	/** Glow's size and Outline's width, from 1 to {@link #maxHaloSize}. */
	public int haloSize;

	public Color boldColor;

	/** The fill color of Box, Scroll, and Banner. */
	public Color shapeFillColor;
	public Color shapeLineColor;
	/** From 0 to {@link #maxShapeLineWidth}. */
	public int shapeLineWidth;
	/** How far the outline of a shape wanders, from 0 to {@link #maxShapeJitter}. */
	public int shapeJitter;

	public TextBackground(TextBackgroundEffect effect, double fade, Color haloColor, int haloSize, Color boldColor, Color shapeFillColor, Color shapeLineColor, int shapeLineWidth,
			int shapeJitter)
	{
		this.effect = effect;
		this.fade = fade;
		this.haloColor = haloColor;
		this.haloSize = haloSize;
		this.boldColor = boldColor;
		this.shapeFillColor = shapeFillColor;
		this.shapeLineColor = shapeLineColor;
		this.shapeLineWidth = shapeLineWidth;
		this.shapeJitter = shapeJitter;
	}

	/**
	 * No effect, the default fade, and the default settings for every effect.
	 */
	public static TextBackground createDefault()
	{
		return new TextBackground(TextBackgroundEffect.None, defaultFade, defaultHaloColor, defaultHaloSize, defaultBoldColor, defaultShapeFillColor, defaultShapeLineColor,
				defaultShapeLineWidth, defaultShapeJitter);
	}

	public TextBackground copy()
	{
		return new TextBackground(effect, fade, haloColor, haloSize, boldColor, shapeFillColor, shapeLineColor, shapeLineWidth, shapeJitter);
	}

	/**
	 * Copies the settings of the given family of effects from another background, leaving the effect and the other families alone.
	 */
	public void copyFamilySettingsFrom(TextBackground other, TextBackgroundEffect family)
	{
		if (family.isHalo())
		{
			haloColor = other.haloColor;
			haloSize = other.haloSize;
		}
		else if (family.isShape())
		{
			shapeFillColor = other.shapeFillColor;
			shapeLineColor = other.shapeLineColor;
			shapeLineWidth = other.shapeLineWidth;
			shapeJitter = other.shapeJitter;
		}
		else if (family == TextBackgroundEffect.BoldBackground)
		{
			boldColor = other.boldColor;
		}
	}

	/**
	 * The fade to draw with, which is 0 when the effect doesn't allow fade.
	 */
	public double getFadeToDraw()
	{
		return effect.allowsFade() ? fade : 0.0;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
		obj.put("effect", effect.name());
		obj.put("fade", fade);
		obj.put("haloColor", MapSettings.colorToString(haloColor));
		obj.put("haloSize", haloSize);
		obj.put("boldColor", MapSettings.colorToString(boldColor));
		obj.put("shapeFillColor", MapSettings.colorToString(shapeFillColor));
		obj.put("shapeLineColor", MapSettings.colorToString(shapeLineColor));
		obj.put("shapeLineWidth", shapeLineWidth);
		obj.put("shapeJitter", shapeJitter);
		return obj;
	}

	public static TextBackground fromJson(JSONObject obj)
	{
		TextBackground result = createDefault();
		if (obj == null)
		{
			return result;
		}
		if (obj.containsKey("effect"))
		{
			result.effect = TextBackgroundEffect.valueOf((String) obj.get("effect"));
		}
		if (obj.containsKey("fade"))
		{
			result.fade = ((Number) obj.get("fade")).doubleValue();
		}
		result.haloColor = parseColorOrDefault(obj, "haloColor", result.haloColor);
		if (obj.containsKey("haloSize"))
		{
			result.haloSize = ((Number) obj.get("haloSize")).intValue();
		}
		result.boldColor = parseColorOrDefault(obj, "boldColor", result.boldColor);
		result.shapeFillColor = parseColorOrDefault(obj, "shapeFillColor", result.shapeFillColor);
		result.shapeLineColor = parseColorOrDefault(obj, "shapeLineColor", result.shapeLineColor);
		if (obj.containsKey("shapeLineWidth"))
		{
			result.shapeLineWidth = ((Number) obj.get("shapeLineWidth")).intValue();
		}
		if (obj.containsKey("shapeJitter"))
		{
			result.shapeJitter = ((Number) obj.get("shapeJitter")).intValue();
		}
		return result;
	}

	private static Color parseColorOrDefault(JSONObject obj, String key, Color defaultValue)
	{
		Color color = obj.containsKey(key) ? MapSettings.parseColor((String) obj.get(key)) : null;
		return color == null ? defaultValue : color;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(effect, fade, haloColor, haloSize, boldColor, shapeFillColor, shapeLineColor, shapeLineWidth, shapeJitter);
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
		TextBackground other = (TextBackground) obj;
		return effect == other.effect && Double.doubleToLongBits(fade) == Double.doubleToLongBits(other.fade) && Objects.equals(haloColor, other.haloColor)
				&& haloSize == other.haloSize && Objects.equals(boldColor, other.boldColor) && Objects.equals(shapeFillColor, other.shapeFillColor)
				&& Objects.equals(shapeLineColor, other.shapeLineColor) && shapeLineWidth == other.shapeLineWidth && shapeJitter == other.shapeJitter;
	}

	@Override
	public String toString()
	{
		return "TextBackground [effect=" + effect + ", fade=" + fade + "]";
	}
}
