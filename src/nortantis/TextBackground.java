package nortantis;

import nortantis.platform.Color;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.util.Objects;

/**
 * What is drawn behind a piece of text: one effect, plus how much to fade the map behind it. Effects keep their own settings, except where they share them, so
 * switching to another effect and back restores what the user had.
 *
 * <p>
 * Sizes and widths are levels that are multiplied by the font's height when drawing, so an effect looks the same at every resolution.
 */
@SuppressWarnings("serial")
public class TextBackground implements Serializable
{
	public static final double defaultFadeBehind = 1.0;
	public static final int maxGlowSize = 30;
	public static final int defaultGlowSize = 8;
	public static final int maxOutlineWidth = 30;
	public static final int defaultOutlineWidth = 2;
	public static final int maxShapeLineWidth = 10;
	public static final int defaultShapeLineWidth = 4;
	public static final int maxShapeJitter = 10;
	public static final int defaultShapeJitter = 4;
	/**
	 * Rarely used in practice: new random maps get their background color from their theme, and maps saved before text had styles get
	 * theirs from the bold background color they stored.
	 */
	public static final Color defaultColor = Color.create(244, 232, 204, 255);
	public static final Color defaultShapeFillColor = Color.create(238, 224, 189, 255);
	public static final Color defaultShapeLineColor = Color.create(64, 46, 30, 255);

	public TextBackgroundEffect effect;
	/**
	 * How much to fade out icons, rivers, roads, and coastlines behind the text. Only used when {@link TextBackgroundEffect#allowsFadeBehind()}.
	 */
	public double fadeBehind;

	/** The color of Glow, Outline, and Bold background. */
	public Color color;
	/** From 1 to {@link #maxGlowSize}. */
	public int glowSize;
	/** From 1 to {@link #maxOutlineWidth}. */
	public int outlineWidth;

	/** The fill color of Box, Scroll, and Banner. */
	public Color shapeFillColor;
	public Color shapeLineColor;
	/** From 0 to {@link #maxShapeLineWidth}. */
	public int shapeLineWidth;
	/** How far the outline of a shape wanders, from 0 to {@link #maxShapeJitter}. */
	public int shapeJitter;

	public TextBackground(TextBackgroundEffect effect, double fadeBehind, Color color, int glowSize, int outlineWidth, Color shapeFillColor, Color shapeLineColor, int shapeLineWidth,
			int shapeJitter)
	{
		this.effect = effect;
		this.fadeBehind = fadeBehind;
		this.color = color;
		this.glowSize = glowSize;
		this.outlineWidth = outlineWidth;
		this.shapeFillColor = shapeFillColor;
		this.shapeLineColor = shapeLineColor;
		this.shapeLineWidth = shapeLineWidth;
		this.shapeJitter = shapeJitter;
	}

	/**
	 * No effect, the default fade behind, and the default settings for every effect.
	 */
	public static TextBackground createDefault()
	{
		return new TextBackground(TextBackgroundEffect.None, defaultFadeBehind, defaultColor, defaultGlowSize, defaultOutlineWidth, defaultShapeFillColor, defaultShapeLineColor,
				defaultShapeLineWidth, defaultShapeJitter);
	}

	public TextBackground copy()
	{
		return new TextBackground(effect, fadeBehind, color, glowSize, outlineWidth, shapeFillColor, shapeLineColor, shapeLineWidth, shapeJitter);
	}

	/**
	 * The fade behind to draw with, which is 0 when the effect doesn't allow it.
	 */
	public double getFadeBehindToDraw()
	{
		return effect.allowsFadeBehind() ? fadeBehind : 0.0;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
		obj.put("effect", effect.name());
		// Stored under "fade", the key saved maps already use for it.
		obj.put("fade", fadeBehind);
		obj.put("color", MapSettings.colorToString(color));
		obj.put("glowSize", glowSize);
		obj.put("outlineWidth", outlineWidth);
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
			result.fadeBehind = ((Number) obj.get("fade")).doubleValue();
		}
		// Bold background's color was stored as boldColor before Glow and Outline shared it.
		result.color = parseColorOrDefault(obj, "color", parseColorOrDefault(obj, "boldColor", result.color));
		if (obj.containsKey("glowSize"))
		{
			result.glowSize = ((Number) obj.get("glowSize")).intValue();
		}
		if (obj.containsKey("outlineWidth"))
		{
			result.outlineWidth = ((Number) obj.get("outlineWidth")).intValue();
		}
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
		return Objects.hash(effect, fadeBehind, color, glowSize, outlineWidth, shapeFillColor, shapeLineColor, shapeLineWidth, shapeJitter);
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
		return effect == other.effect && Double.doubleToLongBits(fadeBehind) == Double.doubleToLongBits(other.fadeBehind) && Objects.equals(color, other.color)
				&& glowSize == other.glowSize && outlineWidth == other.outlineWidth && Objects.equals(shapeFillColor, other.shapeFillColor)
				&& Objects.equals(shapeLineColor, other.shapeLineColor) && shapeLineWidth == other.shapeLineWidth && shapeJitter == other.shapeJitter;
	}

	@Override
	public String toString()
	{
		return "TextBackground [effect=" + effect + ", fadeBehind=" + fadeBehind + "]";
	}
}
