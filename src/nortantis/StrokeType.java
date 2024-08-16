package nortantis;

public enum StrokeType
{
	Solid, Dashes, Rounded_Dashes, Dots;

	@Override
	public String toString()
	{
		return name().replace('_', ' ');
	}
}
