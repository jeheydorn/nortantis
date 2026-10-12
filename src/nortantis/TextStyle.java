package nortantis;

import nortantis.platform.Color;
import nortantis.platform.Font;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * How a piece of text looks, apart from its layout: its font (family, bold/italic, and size), color, and background. A map keeps one per
 * kind of text, which text of that kind follows except for the parts it has its own values for (see {@link TextStyleOverrides}).
 */
@SuppressWarnings("serial")
public class TextStyle implements Serializable
{
	public Font font;
	public Color color;
	public TextBackground background;

	public TextStyle(Font font, Color color, TextBackground background)
	{
		this.font = font;
		this.color = color;
		this.background = background;
	}

	public TextStyle copy()
	{
		return new TextStyle(font, color, background.copy());
	}

	/**
	 * Returns the font with the given family and bold/italic, keeping this style's size.
	 */
	public Font withFamilyAndStyleOf(Font familyAndStyle)
	{
		return Font.create(familyAndStyle.getName(), familyAndStyle.getStyle(), font.getSize());
	}

	/**
	 * Copies the given parts from another style. Background includes fade behind.
	 */
	public void copyPartsFrom(TextStyle source, Set<TextStylePart> parts)
	{
		if (parts.contains(TextStylePart.Font) && parts.contains(TextStylePart.Size))
		{
			font = source.font;
		}
		else if (parts.contains(TextStylePart.Font))
		{
			font = withFamilyAndStyleOf(source.font);
		}
		else if (parts.contains(TextStylePart.Size))
		{
			font = font.deriveFont(font.getStyle(), source.font.getSize());
		}

		if (parts.contains(TextStylePart.Color))
		{
			color = source.color;
		}

		if (parts.contains(TextStylePart.Background))
		{
			background = source.background.copy();
		}
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
		obj.put("font", MapSettings.fontToString(font));
		obj.put("color", MapSettings.colorToString(color));
		obj.put("background", background.toJson());
		return obj;
	}

	public static TextStyle fromJson(JSONObject obj)
	{
		Font font = MapSettings.parseFont((String) obj.get("font"));
		Color color = MapSettings.parseColor((String) obj.get("color"));
		TextBackground background = TextBackground.fromJson((JSONObject) obj.get("background"));
		return new TextStyle(font, color, background);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(font, color, background);
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
		TextStyle other = (TextStyle) obj;
		return Objects.equals(font, other.font) && Objects.equals(color, other.color) && Objects.equals(background, other.background);
	}

	@Override
	public String toString()
	{
		return "TextStyle [font=" + font + ", color=" + color + ", background=" + background + "]";
	}
}
