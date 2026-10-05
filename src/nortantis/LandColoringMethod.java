package nortantis;

import nortantis.swing.translation.Translation;

public enum LandColoringMethod
{
	SingleColor, ColorPoliticalRegions;

	@Override
	public String toString()
	{
		return Translation.get("LandColoringMethod." + name());
	}
}
