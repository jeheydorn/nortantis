package nortantis;

import java.io.Serializable;
import java.util.Objects;

import org.json.simple.JSONObject;

import nortantis.platform.Color;

@SuppressWarnings("serial")
public class HSBColor implements Serializable, Comparable<HSBColor>
{
	public final int hue;
	public final int saturation;
	public final int brightness;
	public final int transparency;

	/***
	 * Creates an HSB representation of a color.
	 * 
	 * @param hue
	 *            Range [0, 360]
	 * @param saturation
	 *            Range [0, 100]
	 * @param brightness
	 *            Range [0, 100]
	 * @param transparency
	 *            Range [0, 100]
	 */
	public HSBColor(int hue, int saturation, int brightness, int transparency)
	{
		super();
		this.hue = hue;
		this.saturation = saturation;
		this.brightness = brightness;
		this.transparency = transparency;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject obj = new JSONObject();
		obj.put("filterHue", hue);
		obj.put("filterSaturation", saturation);
		obj.put("filterBrightness", brightness);
		obj.put("filterTransparency", transparency);
		return obj;
	}

	public static HSBColor fromJson(JSONObject json)
	{
		int hue = ((Long) json.get("filterHue")).intValue();
		int saturation = ((Long) json.get("filterSaturation")).intValue();
		int brightness = ((Long) json.get("filterBrightness")).intValue();
		int transparency = ((Long) json.get("filterTransparency")).intValue();
		return new HSBColor(hue, saturation, brightness, transparency);
	}

	public Color toColor()
	{
		Color base = Color.createFromHSB(hue, saturation, brightness);
		if (transparency == 0)
		{
			return base;
		}
		return Color.create(base.getRed(), base.getGreen(), base.getBlue(), getAlpha());
	}

	public int getAlpha()
	{
		return (int) (((100 - transparency) / 100.0) * 255.0);
	}

	/**
	 * Returns an array in the same format as Color.getHSB() except saturation and brightness can be negative.
	 * 
	 * @return
	 */
	public float[] toArray()
	{
		return new float[] { hue / 360f, saturation / 100f, brightness / 100f };
	}

	public HSBColor copyWithHue(int hue)
	{
		return new HSBColor(hue, this.saturation, this.brightness, this.transparency);
	}

	public HSBColor copyWithSaturation(int saturation)
	{
		return new HSBColor(this.hue, saturation, this.brightness, this.transparency);
	}

	public HSBColor copyWithBrightness(int brightness)
	{
		return new HSBColor(this.hue, this.saturation, brightness, this.transparency);
	}

	public HSBColor copyWithTransparency(int transparency)
	{
		return new HSBColor(this.hue, this.saturation, this.brightness, transparency);
	}

	@Override
	public final boolean equals(Object o)
	{
		if (!(o instanceof HSBColor))
			return false;

		HSBColor hsbColor = (HSBColor) o;
		return hue == hsbColor.hue && saturation == hsbColor.saturation && brightness == hsbColor.brightness
				&& transparency == hsbColor.transparency;
	}

	@Override
	public int hashCode()
	{
		int result = hue;
		result = 31 * result + saturation;
		result = 31 * result + brightness;
		result = 31 * result + transparency;
		return result;
	}

	@Override
	public String toString()
	{
		return "HSBColor [hue=" + hue + ", saturation=" + saturation + ", brightness=" + brightness + ", transparency=" + transparency
				+ "]";
	}

	@Override
	public int compareTo(HSBColor o)
	{
		int result = Integer.compare(this.hue, o.hue);
		if (result == 0)
		{
			result = Integer.compare(this.saturation, o.saturation);
		}
		if (result == 0)
		{
			result = Integer.compare(this.brightness, o.brightness);
		}
		if (result == 0)
		{
			result = Integer.compare(this.transparency, o.transparency);
		}
		return result;
	}

}
