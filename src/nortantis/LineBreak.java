package nortantis;

public enum LineBreak
{
	Auto, One_line, Two_lines;

	public String toString()
	{
		return name().replace("_", " ");
	}
}
