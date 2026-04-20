package nortantis.api;

public enum ApiQuality
{
	draft(0.25),
	low(0.5),
	medium(0.75),
	high(1.0);

	public final double resolution;

	ApiQuality(double resolution)
	{
		this.resolution = resolution;
	}

	public static ApiQuality fromString(String name)
	{
		if (name == null || name.isBlank())
			return null;
		try
		{
			return valueOf(name.toLowerCase());
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
