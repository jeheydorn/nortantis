package nortantis.util.platform.awt;

import nortantis.util.platform.Color;

public class AwtColor extends Color
{
	java.awt.Color color;
	
	public AwtColor(int rgb)
	{
		color = new java.awt.Color(rgb);
	}
	
	public AwtColor(int red, int green, int blue)
	{
		color = new java.awt.Color(red, green, blue);
	}
	
	public AwtColor(int red, int green, int blue, int alpha)
	{
		color = new java.awt.Color(red, green, blue, alpha);
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
}
