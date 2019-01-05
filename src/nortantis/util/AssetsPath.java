package nortantis.util;

public class AssetsPath
{
	private static String assetsPath = "assets";
	public static String get()
	{
		return assetsPath;
	}
	public static void set(String path)
	{
		assetsPath = path;
	}
}
