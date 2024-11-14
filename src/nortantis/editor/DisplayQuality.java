package nortantis.editor;

public enum DisplayQuality
{
	Very_Low, Low, Medium, High, Ultra;

	public String toString()
	{
		return name().replace("_", " ");
	}
}
