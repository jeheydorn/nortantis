package nortantis;

import nortantis.swing.translation.Translation;

public enum LandShape
{
	Continents, Inland_Sea, Scattered;

	public String toString()
	{
		return Translation.get("LandShape." + name());
	}
}
