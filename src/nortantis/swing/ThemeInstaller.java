package nortantis.swing;

import nortantis.MapSettings;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.Logger;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Installs a theme file the user opened, such as by double-clicking it, into their themes folder, where Apply Theme and the new map dialog
 * find it.
 */
class ThemeInstaller
{
	/**
	 * Offers to install the given theme file, and installs it if the user agrees.
	 *
	 * @param parent
	 *            The window to show the dialog over, or null.
	 * @return Whether the theme was installed.
	 */
	static boolean offerToInstall(Component parent, String themeFilePath)
	{
		String themeName = FilenameUtils.getBaseName(themeFilePath);
		try
		{
			MapSettings.readThemeFile(themeFilePath);
		}
		catch (MapSettings.ThemeFromNewerVersionException e)
		{
			SwingHelper.showMessageDialog(parent, Translation.get("theme.fromNewerVersion", e.themeVersion, MapSettings.currentVersion), Translation.get("themeInstaller.title"),
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		catch (Exception e)
		{
			SwingHelper.showMessageDialog(parent, Translation.get("theme.unableToLoad", e.getMessage()), Translation.get("themeInstaller.title"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		String install = Translation.get("themeInstaller.install");
		String cancel = Translation.get("common.cancel");
		int choice = SwingHelper.showOptionDialog(parent,
				Translation.get("themeInstaller.message", themeName, Translation.get("menu.file"), Translation.get("menu.file.theme"), Translation.get("menu.file.theme.apply")),
				Translation.get("themeInstaller.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, new Object[] { install, cancel }, install);
		if (choice != 0)
		{
			return false;
		}

		Path destination = Paths.get(Assets.getUserThemesFolder().toString(), FilenameUtils.getName(themeFilePath));
		if (Files.exists(destination))
		{
			int replace = SwingHelper.showConfirmDialog(parent, Translation.get("themeInstaller.replace", themeName), Translation.get("themeInstaller.title"),
					JOptionPane.YES_NO_OPTION);
			if (replace != JOptionPane.YES_OPTION)
			{
				return false;
			}
		}

		try
		{
			Files.createDirectories(destination.getParent());
			Files.copy(Paths.get(themeFilePath), destination, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			Logger.printError("Unable to install the theme " + themeFilePath, e);
			SwingHelper.showMessageDialog(parent, Translation.get("themeInstaller.failed", e.getMessage()), Translation.get("themeInstaller.title"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		SwingHelper.showMessageDialog(parent, Translation.get("themeInstaller.installed", themeName), Translation.get("themeInstaller.title"), JOptionPane.INFORMATION_MESSAGE);
		return true;
	}

	static boolean isThemeFile(String path)
	{
		return path != null && FilenameUtils.getExtension(path).equalsIgnoreCase(MapSettings.themeFileExtension);
	}
}
