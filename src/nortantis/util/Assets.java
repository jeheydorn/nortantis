package nortantis.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;

public class Assets
{
	private final static String assetsPath = "assets";

	public static synchronized String getAssetsPath()
	{
		return assetsPath;
	}

	// TODO Use this
	public static String getInstalledArtPackName()
	{
		return "nortantis";
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
}
