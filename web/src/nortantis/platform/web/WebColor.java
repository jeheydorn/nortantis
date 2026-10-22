package nortantis.platform.web;

import nortantis.platform.Color;

/**
 * An immutable color stored as a packed ARGB int.
 */
@SuppressWarnings("serial")
public class WebColor extends Color
{
	final int argb;

	public WebColor(int argb)
	{
		this.argb = argb;
	}

	public WebColor(int red, int green, int blue, int alpha)
	{
		this(((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF));
	}

	@Override
	public int getRGB()
	{
		return argb;
	}

	@Override
	public int getRed()
	{
		return (argb >> 16) & 0xFF;
	}

	@Override
	public int getGreen()
	{
		return (argb >> 8) & 0xFF;
	}

	@Override
	public int getBlue()
	{
		return argb & 0xFF;
	}

	@Override
	public int getAlpha()
	{
		return (argb >>> 24);
	}

	@Override
	public float[] getHSB()
	{
		return rgbToHsb(getRed(), getGreen(), getBlue());
	}

	/**
	 * The same conversion as java.awt.Color.RGBtoHSB.
	 */
	static float[] rgbToHsb(int r, int g, int b)
	{
		float hue;
		float saturation;
		float brightness;
		int cmax = Math.max(Math.max(r, g), b);
		int cmin = Math.min(Math.min(r, g), b);

		brightness = ((float) cmax) / 255.0f;
		if (cmax != 0)
		{
			saturation = ((float) (cmax - cmin)) / ((float) cmax);
		}
		else
		{
			saturation = 0;
		}
		if (saturation == 0)
		{
			hue = 0;
		}
		else
		{
			float redc = ((float) (cmax - r)) / ((float) (cmax - cmin));
			float greenc = ((float) (cmax - g)) / ((float) (cmax - cmin));
			float bluec = ((float) (cmax - b)) / ((float) (cmax - cmin));
			if (r == cmax)
			{
				hue = bluec - greenc;
			}
			else if (g == cmax)
			{
				hue = 2.0f + redc - bluec;
			}
			else
			{
				hue = 4.0f + greenc - redc;
			}
			hue = hue / 6.0f;
			if (hue < 0)
			{
				hue = hue + 1.0f;
			}
		}
		return new float[] { hue, saturation, brightness };
	}

	/**
	 * The same conversion as java.awt.Color.HSBtoRGB.
	 */
	static int hsbToRgb(float hue, float saturation, float brightness)
	{
		int r = 0, g = 0, b = 0;
		if (saturation == 0)
		{
			r = g = b = (int) (brightness * 255.0f + 0.5f);
		}
		else
		{
			float h = (hue - (float) Math.floor(hue)) * 6.0f;
			float f = h - (float) java.lang.Math.floor(h);
			float p = brightness * (1.0f - saturation);
			float q = brightness * (1.0f - saturation * f);
			float t = brightness * (1.0f - (saturation * (1.0f - f)));
			switch ((int) h)
			{
				case 0:
					r = (int) (brightness * 255.0f + 0.5f);
					g = (int) (t * 255.0f + 0.5f);
					b = (int) (p * 255.0f + 0.5f);
					break;
				case 1:
					r = (int) (q * 255.0f + 0.5f);
					g = (int) (brightness * 255.0f + 0.5f);
					b = (int) (p * 255.0f + 0.5f);
					break;
				case 2:
					r = (int) (p * 255.0f + 0.5f);
					g = (int) (brightness * 255.0f + 0.5f);
					b = (int) (t * 255.0f + 0.5f);
					break;
				case 3:
					r = (int) (p * 255.0f + 0.5f);
					g = (int) (q * 255.0f + 0.5f);
					b = (int) (brightness * 255.0f + 0.5f);
					break;
				case 4:
					r = (int) (t * 255.0f + 0.5f);
					g = (int) (p * 255.0f + 0.5f);
					b = (int) (brightness * 255.0f + 0.5f);
					break;
				case 5:
					r = (int) (brightness * 255.0f + 0.5f);
					g = (int) (p * 255.0f + 0.5f);
					b = (int) (q * 255.0f + 0.5f);
					break;
			}
		}
		return 0xff000000 | (r << 16) | (g << 8) | (b << 0);
	}

	@Override
	public int hashCode()
	{
		return argb;
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof WebColor && ((WebColor) obj).argb == argb;
	}

	@Override
	public String toString()
	{
		return "WebColor [r=" + getRed() + ", g=" + getGreen() + ", b=" + getBlue() + ", a=" + getAlpha() + "]";
	}
}
