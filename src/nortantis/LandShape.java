package nortantis;

public enum LandShape
{
	Continents, Inland_Sea, Scattered;

	public String toString()
	{
		return name().replace("_", " ");
	}
}
