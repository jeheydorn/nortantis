package nortantis.platform.skia;

import nortantis.platform.Color;

import java.util.Objects;

public class SkiaColor extends Color
{
	private final int argb;

	public SkiaColor(int rgb, boolean hasAlpha)
	{
		if (hasAlpha)
		{
			this.argb = rgb;
		}
		else
		{
			this.argb = 0xFF000000 | (rgb & 0xFFFFFF);
		}
	}

	public SkiaColor(int red, int green, int blue)
	{
		this(red, green, blue, 255);
	}

	public SkiaColor(float red, float green, float blue)
	{
		this((int) (red * 255 + 0.5), (int) (green * 255 + 0.5), (int) (blue * 255 + 0.5), 255);
	}

	public SkiaColor(int red, int green, int blue, int alpha)
	{
		this.argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
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
		return (argb >> 24) & 0xFF;
	}

	@Override
	public float[] getHSB()
	{
		return rgbToHSB(getRed(), getGreen(), getBlue());
	}

	/**
	 * Pure Java RGB-to-HSB conversion equivalent to java.awt.Color.RGBtoHSB.
	 */
	static float[] rgbToHSB(int r, int g, int b)
	{
		float[] hsb = new float[3];
		int cmax = Math.max(r, Math.max(g, b));
		int cmin = Math.min(r, Math.min(g, b));
		float brightness = cmax / 255.0f;
		float saturation;
		float hue;
		if (cmax != 0)
		{
			saturation = (float) (cmax - cmin) / (float) cmax;
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
			float redc = (float) (cmax - r) / (float) (cmax - cmin);
			float greenc = (float) (cmax - g) / (float) (cmax - cmin);
			float bluec = (float) (cmax - b) / (float) (cmax - cmin);
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
		hsb[0] = hue;
		hsb[1] = saturation;
		hsb[2] = brightness;
		return hsb;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SkiaColor skiaColor = (SkiaColor) o;
		return argb == skiaColor.argb;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(argb);
	}

	@Override
	public String toString()
	{
		return String.format("SkiaColor[red=" + getRed() + ", green=" + getGreen() + ", blue=" + getBlue() + ", alpha=" + getAlpha() + "]");
	}
}
