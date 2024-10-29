package nortantis.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class OSHelper
{
	public static boolean isLinux()
	{
		String OS = System.getProperty("os.name").toUpperCase();
		return OS.contains("NUX");
	}

	public static boolean isWindows()
	{
		String OS = System.getProperty("os.name").toUpperCase();
		return OS.contains("WIN");
	}

	public static boolean isMac()
	{
		String OS = System.getProperty("os.name").toUpperCase();
		return OS.contains("MAC");
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
		return Paths.get(System.getProperty("user.dir"));
	}
}
