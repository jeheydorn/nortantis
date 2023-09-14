package nortantis.util;

public class AssetsPath
{
	private static String installPath = "assets";
	private static String overridablePath = "assets";
	
	public static String getInstallPath()
	{
		return installPath;
	}
	
	public static String getOverridablePath()
	{
		return overridablePath;
	}
	
	public static void setInstallPath(String path)
	{
		installPath = path;
	}


	public static void setOverridablePath(String path)
	{
		overridablePath = path;
	}
}
