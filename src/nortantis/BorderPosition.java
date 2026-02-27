package nortantis;

import nortantis.swing.translation.Translation;

public enum BorderPosition
{
	Outside_map, Over_map;

	public String toString()
	{
		return Translation.get("BorderPosition." + name());
	}
}
