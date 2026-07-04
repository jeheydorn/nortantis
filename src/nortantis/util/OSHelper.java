package nortantis.util;

import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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

	/**
	 * The font family used for decorative, hand-lettered-looking headline text (e.g. the map canvas's welcome message). Different OSes
	 * ship different fonts, so the family is chosen per-OS; callers are still responsible for falling back to a font that can display the
	 * text (e.g. via Font.canDisplayUpTo), since none of these fonts support non-Latin scripts.
	 */
	public static String getDecorativeFontFamilyName()
	{
		return isLinux() ? "Z003" : isMac() ? "Apple Chancery" : "Gabriola";
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

	public static void openFileExplorerTo(File folder)
	{
		Desktop desktop = Desktop.getDesktop();
		if (desktop.isSupported(Desktop.Action.OPEN))
		{
			try
			{
				desktop.open(folder);
			}
			catch (IOException ex)
			{
				Logger.printError("Error while trying to open folder '" + folder + " in your system's file explorer.", ex);
			}
		}
		else
		{
			JOptionPane.showMessageDialog(null, Translation.get("osHelper.unableToOpenFolder", folder), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
		}
	}
}
