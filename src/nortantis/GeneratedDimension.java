package nortantis;

import nortantis.swing.translation.Translation;

/**
 * The allowed map dimensions for generated backgrounds.
 */
public enum GeneratedDimension
{
	Square(4096, 4096), Sixteen_by_9(4096, 2304), Golden_Ratio(4096, 2531), Any(0, 0);

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
		if (height == 0)
		{
			return 0;
		}
		return (double) width / height;
	}

	@Override
	public String toString()
	{
		if (this == Any)
		{
			return displayName();
		}
		return displayName() + " (" + width + " \u00d7 " + height + ")";
	}

	public static GeneratedDimension fromDimensions(int w, int h)
	{
		for (GeneratedDimension d : values())
		{
			if (d == Any)
			{
				continue;
			}
			if (d.width == w && d.height == h)
			{
				return d;
			}
		}
		return Any;
	}

	/**
	 * Returns all preset dimensions — all values except {@link #Any}.
	 */
	public static GeneratedDimension[] presets()
	{
		GeneratedDimension[] all = values();
		GeneratedDimension[] result = new GeneratedDimension[all.length - 1];
		int j = 0;
		for (GeneratedDimension d : all)
		{
			if (d != Any)
			{
				result[j++] = d;
			}
		}
		return result;
	}
}
