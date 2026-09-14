package nortantis;

import nortantis.swing.translation.Translation;

public enum LandShape
{
	Continents, Inland_Sea, Scattered, Supercontinent, Coastline, Landlocked;

	public String toString()
	{
		return Translation.get("LandShape." + name());
	}

	public String getDescription()
	{
		return Translation.get("LandShape." + name() + ".description");
	}
}
