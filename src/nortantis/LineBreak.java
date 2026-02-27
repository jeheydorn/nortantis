package nortantis;

import nortantis.swing.translation.Translation;

public enum LineBreak
{
	Auto, One_line, Two_lines;

	public String toString()
	{
		return Translation.get("LineBreak." + name());
	}
}
