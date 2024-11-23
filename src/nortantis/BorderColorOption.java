package nortantis;

public enum BorderColorOption
{
	Ocean_color, Choose_color;

	public String toString()
	{
		return name().replace("_", " ");
	}
}
