package nortantis.editor;

import nortantis.swing.translation.Translation;

public enum DisplayQuality
{
	Very_Low, Low, Medium, High, Ultra;

	public String toString()
	{
		return Translation.get("DisplayQuality." + name());
	}
}
