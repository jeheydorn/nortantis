package nortantis.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JOptionPane;

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
			JOptionPane.showMessageDialog(null, "Unable to open the folder '" + folder + "'. Opening folders is not supported on your system.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}
