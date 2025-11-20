package nortantis;

import java.io.Serializable;
import java.util.Objects;

import org.json.simple.JSONObject;

import nortantis.platform.Color;

@SuppressWarnings("serial")
public class HSBColor implements Serializable
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

	@Override
	public int hashCode()
	{
		return Objects.hash(brightness, hue, saturation, transparency);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		HSBColor other = (HSBColor) obj;
		return brightness == other.brightness && hue == other.hue && saturation == other.saturation && transparency == other.transparency;
	}
}
