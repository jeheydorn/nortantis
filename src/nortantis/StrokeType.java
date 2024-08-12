package nortantis;

public enum StrokeType
{
	Solid, Dashes, Short_Dashes, Long_and_Short_Dashes;

	@Override
	public String toString()
	{
		return name().replace('_', ' ');
	}
}
