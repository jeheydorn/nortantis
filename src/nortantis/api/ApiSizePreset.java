package nortantis.api;

public enum ApiSizePreset
{
	small(1024, 1024),
	medium(2048, 2048),
	large(4096, 4096),
	wide_small(1920, 1080),
	wide_medium(2560, 1440),
	wide_large(4096, 2304),
	golden(4096, 2531);

	public final int width;
	public final int height;

	ApiSizePreset(int width, int height)
	{
		this.width = width;
		this.height = height;
	}

	public static ApiSizePreset fromString(String name)
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
