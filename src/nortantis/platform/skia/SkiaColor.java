package nortantis.platform.skia;

import java.io.Serializable;
import java.util.Objects;
import nortantis.platform.Color;

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
		float[] hsb = new float[3];
		java.awt.Color.RGBtoHSB(getRed(), getGreen(), getBlue(), hsb);
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
		return String.format("SkiaColor[red=" + getRed() + ", green=" + getGreen() + ", blue=" + getBlue() + ", alpha=" + getAlpha() +"]");
	}
}
