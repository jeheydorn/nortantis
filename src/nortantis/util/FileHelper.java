package nortantis.util;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;

public class FileHelper
{
	public static String replaceHomeFolderPlaceholder(String path)
	{
		if (path == null)
		{
			return path;
		}
		
		if (path.startsWith(getHomePlaceholder()))
		{
			String userHome = System.getProperty("user.home");
			if (userHome == null || userHome.isEmpty())
			{
				return path;
			}
			
			String relativePart = path.substring(getHomePlaceholder().length());
			return Paths.get(userHome, relativePart).toString();
		}
		else
		{
			return path;
		}
	}
	
	private static String getHomePlaceholder()
	{
		if (OSHelper.isWindows())
		{
			return "%HOMEPATH%";
		}
		else
		{
			return "~";
		}
	}
	
	public static String replaceHomeFolderWithPlaceholder(String path)
	{
		// Get the user's home directory
		String userHome = System.getProperty("user.home");
		if (userHome == null || userHome.isEmpty())
		{
			return path;
		}

		try
		{
			Path inputPath = Paths.get(path);
			if (inputPath.startsWith(userHome))
			{
				// Replace the home directory part with the current user's name
				Path relativePart = FileSystems.getDefault().getPath(userHome).relativize(inputPath);
				return getHomePlaceholder() + File.separator + relativePart.toString();
			}
			else
			{
				// If the input path doesn't start with the home directory, return it unchanged
				return path;
			}
		}
		catch (InvalidPathException ex)
		{
			return path;
		}
	}
	
	public static boolean isFile(String filePath)
	{
		File file = new File(filePath);
		return file.exists() && !file.isDirectory();
	}
}
