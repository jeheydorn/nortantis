package nortantis;

import nortantis.swing.translation.Translation;

import org.apache.commons.lang3.StringUtils;

public enum GridOverlayOffset
{
	zero, quarter, half, threeQuarters;

	public String toString()
	{
		if (this == zero)
		{
			return "0";
		}
		else if (this == quarter)
		{
			return "1/4";
		}
		else if (this == half)
		{
			return "1/2";
		}
		else if (this == threeQuarters)
		{
			return "3/4";
		}
		throw new IllegalArgumentException("Unimplemented GridOverlayPosition.");
	}

	public static GridOverlayOffset parse(String string)
	{
		if (StringUtils.isBlank(string) || string.equals("0"))
		{
			return zero;
		}
		if (string.equals("1/4"))
		{
			return quarter;
		}
		if (string.equals("1/2"))
		{
			return half;
		}
		if (string.equals("3/4"))
		{
			return threeQuarters;
		}
		throw new IllegalArgumentException("Invalid GridOverlayPosition string: " + string);
	}

	public String displayName()
	{
		return Translation.get("GridOverlayOffset." + name());
	}

	public float getScale()
	{
		if (this == GridOverlayOffset.zero)
		{
			return 0f;
		}
		if (this == GridOverlayOffset.quarter)
		{
			return 0.25f;
		}
		if (this == GridOverlayOffset.half)
		{
			return 0.5f;
		}
		if (this == GridOverlayOffset.threeQuarters)
		{
			return 0.75f;
		}

		throw new UnsupportedOperationException("Unimplemented scale: " + this);
	}
}
