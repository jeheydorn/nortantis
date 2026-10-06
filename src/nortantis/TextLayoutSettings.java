package nortantis;

import org.json.simple.JSONObject;

import java.io.Serializable;
import java.util.Objects;

/**
 * How a piece of text is laid out, apart from where it is and its angle: its curvature, the space added between its letters, and how many
 * lines it uses. A map keeps one per kind of text for new text.
 */
@SuppressWarnings("serial")
public class TextLayoutSettings implements Serializable
{
	public double curvature;
	public int spacing;
	public LineBreak lineBreak;

	public TextLayoutSettings(double curvature, int spacing, LineBreak lineBreak)
	{
		this.curvature = curvature;
		this.spacing = spacing;
		this.lineBreak = lineBreak;
	}

	public static TextLayoutSettings createDefault()
	{
		return new TextLayoutSettings(0.0, 0, LineBreak.Auto);
	}

	public TextLayoutSettings copy()
	{
		return new TextLayoutSettings(curvature, spacing, lineBreak);
	}

	/**
	 * Gives the text this layout.
	 */
	public void applyTo(MapText text)
	{
		text.curvature = curvature;
		text.spacing = spacing;
		text.lineBreak = lineBreak;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
		obj.put("curvature", curvature);
		obj.put("spacing", spacing);
		obj.put("lineBreak", lineBreak.name());
		return obj;
	}

	public static TextLayoutSettings fromJson(JSONObject obj)
	{
		TextLayoutSettings result = createDefault();
		if (obj.containsKey("curvature"))
		{
			result.curvature = ((Number) obj.get("curvature")).doubleValue();
		}
		if (obj.containsKey("spacing"))
		{
			result.spacing = ((Number) obj.get("spacing")).intValue();
		}
		if (obj.containsKey("lineBreak"))
		{
			result.lineBreak = LineBreak.valueOf(((String) obj.get("lineBreak")).replace(" ", "_"));
		}
		return result;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(curvature, spacing, lineBreak);
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
		TextLayoutSettings other = (TextLayoutSettings) obj;
		return Double.doubleToLongBits(curvature) == Double.doubleToLongBits(other.curvature) && spacing == other.spacing && lineBreak == other.lineBreak;
	}

	@Override
	public String toString()
	{
		return "TextLayoutSettings [curvature=" + curvature + ", spacing=" + spacing + ", lineBreak=" + lineBreak + "]";
	}
}
