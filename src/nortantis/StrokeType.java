package nortantis;

import nortantis.swing.translation.Translation;

public enum StrokeType
{
	Solid, Dashes, Rounded_Dashes, Dots;

	@Override
	public String toString()
	{
		return Translation.get("StrokeType." + name());
	}
}
