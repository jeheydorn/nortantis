package nortantis;

import nortantis.swing.translation.Translation;

public enum TextType
{
	Title, Region, Mountain_range, Other_mountains, City, Lake, River, Road;

	public String toString()
	{
		return Translation.get("TextType." + name());
	}
}
