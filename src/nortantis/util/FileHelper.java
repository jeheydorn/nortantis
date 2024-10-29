package nortantis.util;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileHelper
{
	public static String replaceHomeFolderPlaceholder(String absolutePath)
	{
		if (absolutePath == null)
		{
			return absolutePath;
		}
		
		if (absolutePath.startsWith(getHomePlaceholder()))
		{
			String userHome = System.getProperty("user.home");
			if (userHome == null || userHome.isEmpty())
			{
				return absolutePath;
			}
			
			String relativePart = absolutePath.substring(getHomePlaceholder().length());
			return Paths.get(userHome, relativePart).toString();
		}
		else
		{
			return absolutePath;
		}
	}
	
	private static String getHomePlaceholder()
	{
		if (Assets.isWindows())
		{
			return "%HOMEPATH%";
		}
		else
		{
			return "~";
		}
	}
	
	public static String replaceHomeFolderWithPlaceholder(String absolutePath)
	{
		// Get the user's home directory
		String userHome = System.getProperty("user.home");
		if (userHome == null || userHome.isEmpty())
		{
			return absolutePath;
		}

		try
		{
			Path inputPath = Paths.get(absolutePath);
			if (inputPath.startsWith(userHome))
			{
				// Replace the home directory part with the current user's name
				Path relativePart = FileSystems.getDefault().getPath(userHome).relativize(inputPath);
				return getHomePlaceholder() + File.separator + relativePart.toString();
			}
			else
			{
				// If the input path doesn't start with the home directory, return it unchanged
				return absolutePath;
			}
		}
		catch (InvalidPathException ex)
		{
			return absolutePath;
		}
	}
	
	public static boolean isFile(String filePath)
	{
		File file = new File(filePath);
		return file.exists() && !file.isDirectory();
	}
}
