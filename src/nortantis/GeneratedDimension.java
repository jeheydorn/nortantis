package nortantis;

import nortantis.swing.translation.Translation;

/**
 * The allowed map dimensions for generated backgrounds.
 */
public enum GeneratedDimension
{
	Square(4096, 4096), Sixteen_by_9(4096, 2304), Golden_Ratio(4096, 2531);

	public final int width;
	public final int height;

	GeneratedDimension(int width, int height)
	{
		this.width = width;
		this.height = height;
	}

	public String displayName()
	{
		return Translation.get("GeneratedDimension." + name());
	}

	public double aspectRatio()
	{
		return (double) width / height;
	}

	@Override
	public String toString()
	{
		return width + " \u00d7 " + height + " (" + displayName() + ")";
	}

	public static GeneratedDimension fromDimensions(int w, int h)
	{
		for (GeneratedDimension d : values())
		{
			if (d.width == w && d.height == h)
			{
				return d;
			}
		}
		return null;
	}
}
