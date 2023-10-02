package nortantis.util;

public class AssetsPath
{
	/**
	 * This flag is set by hand to tell assets to look for files in the install folder for the system rather than in a relative folder.
	 */
	private static boolean isInstalled = false;
	
	private static String installPath;
	
	static 
	{
		String OS = System.getProperty("os.name").toUpperCase();
		if (OS.contains("WIN"))
		{
			if (isInstalled)
			{
				installPath = "C:\\Program Files\\Nortantis\\app\\assets";
				System.out.println("Using assets folder from installation at: " + installPath 
						+ ". If you are seeing this message while running from source, then change AssetsPath.isInstalled to false.");
			}
			else
			{
				installPath = "assets";
			}
		}
		else if (OS.contains("MAC"))
		{
			// Installers are not supported for Mac.
			installPath = "assets";
		}
		else if (OS.contains("NUX"))
		{
			// Installers are not supported for Linux
			installPath = "assets";
		}
	}
	
	public static synchronized String getInstallPath()
	{
		return installPath;
	}
	
	public static synchronized void setInstallPath(String path)
	{
		installPath = path;
	}
}
