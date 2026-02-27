package nortantis.swing;

import nortantis.swing.translation.Translation;

public enum LookAndFeel
{
	Dark, Light, System;

	@Override
	public String toString()
	{
		return Translation.get("LookAndFeel." + name());
	}
}
