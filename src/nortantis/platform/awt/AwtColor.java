package nortantis.platform.awt;

import java.io.Serializable;
import java.util.Objects;

import nortantis.platform.Color;

@SuppressWarnings("serial")
public class AwtColor extends Color implements Serializable
{
	java.awt.Color color;

	public AwtColor(int rgb, boolean hasAlpha)
	{
		color = new java.awt.Color(rgb, hasAlpha);
	}

	public AwtColor(int red, int green, int blue)
	{
		color = new java.awt.Color(red, green, blue);
	}

	public AwtColor(float red, float green, float blue)
	{
		color = new java.awt.Color(red, green, blue);
	}

	public AwtColor(int red, int green, int blue, int alpha)
	{
		color = new java.awt.Color(red, green, blue, alpha);
	}

	public AwtColor(java.awt.Color color)
	{
		this.color = color;
	}

	@Override
	public int getRGB()
	{
		return color.getRGB();
	}

	@Override
	public int getRed()
	{
		return color.getRed();
	}

	@Override
	public int getGreen()
	{
		return color.getGreen();
	}

	@Override
	public int getBlue()
	{
		return color.getBlue();
	}

	@Override
	public int getAlpha()
	{
		return color.getAlpha();
	}

	@Override
	public float[] getHSB()
	{
		float[] hsb = new float[3];
		java.awt.Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
		return hsb;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(color);
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
		AwtColor other = (AwtColor) obj;
		return Objects.equals(color, other.color);
	}

	@Override
	public String toString()
	{
		return "AwtColor [color=" + color + "]";
	}
}
