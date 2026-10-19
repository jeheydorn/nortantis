package nortantis.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class OSHelper
{
	private static Boolean isLinuxCache;

	public static boolean isLinux()
	{
		if (isLinuxCache == null)
		{
			String OS = System.getProperty("os.name").toUpperCase();
			boolean result = OS.contains("NUX");
			isLinuxCache = result;
			return result;
		}
		return isLinuxCache;
	}

	private static Boolean isWindowsCache;

	public static boolean isWindows()
	{
		if (isWindowsCache == null)
		{
			String OS = System.getProperty("os.name").toUpperCase();
			// Return true if either this is Windows or this is a system on which we can't tell (meaning Windows is the default).
			boolean result = OS.contains("WIN") || (!isLinux() && !isMac());
			isWindowsCache = result;
			return result;
		}
		return isWindowsCache;
	}

	private static Boolean isMacCache;

	public static boolean isMac()
	{
		if (isMacCache == null)
		{
			String OS = System.getProperty("os.name").toUpperCase();
			boolean result = OS.contains("MAC");
			isMacCache = result;
			return result;
		}
		return isMacCache;
	}

	public static Path getAppDataPath()
	{
		if (isWindows())
		{
			return Paths.get(System.getenv("APPDATA"), "Nortantis");
		}
		else if (isMac())
		{
			return Paths.get(System.getProperty("user.home"), ".Nortantis");
		}
		else if (isLinux())
		{
			return Paths.get(System.getProperty("user.home"), ".Nortantis");
		}
		return Paths.get(System.getProperty("user.home"), ".Nortantis");
	}
}
