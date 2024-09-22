package nortantis.util;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.net.JarURLConnection;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.io.IOException;

public class AssetsPath
{
	/**
	 * This flag is set by hand to tell assets to look for files in the install folder for the system rather than in a relative folder.
	 */
	public static boolean isInstalled = false;

	private static String installPath;

	static
	{
		if (isMac())
		{
			// Installers are not supported for Mac.
			installPath = "assets";
		}
		else if (isLinux())
		{
			if (isInstalled)
			{
				installPath = "/opt/nortantis/lib/app/assets";
			}
			else
			{
				installPath = "assets";
			}

		}
		else
		{
			// Windows. Note that I'm not checking isWindows() because I've seen
			// it fail to detect Windows, and because something needs to be the
			// default.
			if (isInstalled)
			{
				installPath = "C:\\Program Files\\Nortantis\\app\\assets";
			}
			else
			{
				installPath = "assets";
			}
		}

		if (isInstalled)
		{
			System.out.println("Using assets folder from installation at: " + installPath
					+ ". If you are seeing this message while running from source, then change AssetsPath.isInstalled to false to switch to the assets folder in the source code.");
		}

	}

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

	public static synchronized String getInstallPath()
	{
		return installPath;
	}

	public static synchronized void setInstallPath(String path)
	{
		installPath = path;
	}

	public static List<String> listFilesFromJar(String path, String suffix)
	{
		List<String> result = new ArrayList<>();
		try
		{
			Enumeration<URL> urls = AssetsPath.class.getClassLoader().getResources(path);
			while (urls.hasMoreElements())
			{
				URL dirUrl = urls.nextElement();
				if (dirUrl != null && dirUrl.getProtocol().equals("jar"))
				{
					JarURLConnection jarConnection = (JarURLConnection) dirUrl.openConnection();
					JarFile jarFile = jarConnection.getJarFile();
					Enumeration<JarEntry> entries = jarFile.entries();
					while (entries.hasMoreElements())
					{
						JarEntry entry = entries.nextElement();
						String entryName = entry.getName();
						if (entryName.startsWith(path) && entryName.endsWith(suffix))
						{
							result.add(entryName.substring(path.length() + 1)); // +1 to remove the leading slash
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error reading directory from resources: " + path, e);
		}
		return result;
	}
}
