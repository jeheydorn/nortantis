package nortantis;

import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The parts of a piece of text's style that it has its own values for. A part that is null follows the style of the text's type.
 */
@SuppressWarnings("serial")
public class TextStyleOverrides implements Serializable
{
	/** Set together with {@link #fontStyle}. */
	public String fontFamily;
	public FontStyle fontStyle;
	public Float size;
	public Color color;
	public TextBackground background;

	public TextStyleOverrides()
	{
	}

	public TextStyleOverrides copy()
	{
		TextStyleOverrides result = new TextStyleOverrides();
		result.fontFamily = fontFamily;
		result.fontStyle = fontStyle;
		result.size = size;
		result.color = color;
		result.background = background == null ? null : background.copy();
		return result;
	}

	public boolean isEmpty()
	{
		return getParts().isEmpty();
	}

	public boolean has(TextStylePart part)
	{
		return switch (part)
		{
			case Font -> fontFamily != null;
			case Size -> size != null;
			case Color -> color != null;
			case Background -> background != null;
		};
	}

	public Set<TextStylePart> getParts()
	{
		Set<TextStylePart> result = EnumSet.noneOf(TextStylePart.class);
		for (TextStylePart part : TextStylePart.values())
		{
			if (has(part))
			{
				result.add(part);
			}
		}
		return result;
	}

	/**
	 * The style of text of the given type's style with these overrides. When there are none, this is the type's style itself, so the result
	 * must not be modified.
	 */
	public TextStyle resolve(TextStyle typeStyle)
	{
		if (isEmpty())
		{
			return typeStyle;
		}
		Font font = typeStyle.font;
		if (fontFamily != null || size != null)
		{
			font = Font.create(fontFamily != null ? fontFamily : font.getName(), fontFamily != null ? fontStyle : font.getStyle(),
					size != null ? size : font.getSize());
		}
		return new TextStyle(font, color != null ? color : typeStyle.color, background != null ? background : typeStyle.background);
	}

	/**
	 * Gives these overrides the given part's value from a style.
	 */
	public void set(TextStylePart part, TextStyle source)
	{
		switch (part)
		{
			case Font ->
			{
				fontFamily = source.font.getName();
				fontStyle = source.font.getStyle();
			}
			case Size -> size = source.font.getSize();
			case Color -> color = source.color;
			case Background -> background = source.background.copy();
		}
	}

	public void clear(TextStylePart part)
	{
		switch (part)
		{
			case Font ->
			{
				fontFamily = null;
				fontStyle = null;
			}
			case Size -> size = null;
			case Color -> color = null;
			case Background -> background = null;
		}
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
		if (fontFamily != null)
		{
			obj.put("fontFamily", fontFamily);
			obj.put("fontStyle", fontStyle.value);
		}
		if (size != null)
		{
			obj.put("size", size);
		}
		if (color != null)
		{
			obj.put("color", MapSettings.colorToString(color));
		}
		if (background != null)
		{
			obj.put("background", background.toJson());
		}
		return obj;
	}

	public static TextStyleOverrides fromJson(JSONObject obj)
	{
		TextStyleOverrides result = new TextStyleOverrides();
		if (obj.containsKey("fontFamily"))
		{
			result.fontFamily = (String) obj.get("fontFamily");
			result.fontStyle = FontStyle.fromNumber(((Number) obj.get("fontStyle")).intValue());
		}
		if (obj.containsKey("size"))
		{
			result.size = ((Number) obj.get("size")).floatValue();
		}
		if (obj.containsKey("color"))
		{
			result.color = MapSettings.parseColor((String) obj.get("color"));
		}
		if (obj.containsKey("background"))
		{
			result.background = TextBackground.fromJson((JSONObject) obj.get("background"));
		}
		return result;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(fontFamily, fontStyle, size, color, background);
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
		TextStyleOverrides other = (TextStyleOverrides) obj;
		return Objects.equals(fontFamily, other.fontFamily) && fontStyle == other.fontStyle && Objects.equals(size, other.size) && Objects.equals(color, other.color)
				&& Objects.equals(background, other.background);
	}

	@Override
	public String toString()
	{
		return "TextStyleOverrides [fontFamily=" + fontFamily + ", fontStyle=" + fontStyle + ", size=" + size + ", color=" + color + ", background=" + background + "]";
	}
}
