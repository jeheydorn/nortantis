package nortantis.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

	public static String readFile(String path)
	{
		try
		{
			Charset encoding = Charset.defaultCharset();
			byte[] encoded = Files.readAllBytes(Paths.get(path));
			return encoding.decode(ByteBuffer.wrap(encoded)).toString();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static boolean isDirectoryEmpty(String directory)
	{
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(directory)))
		{
			return !dirStream.iterator().hasNext();
		}
		catch (IOException e)
		{
			throw new RuntimeException("Unable to check if directory on disk is empty. Directory: " + directory + ".", e);
		}
	}

	public static void unzip(File zipFile, Path targetDir, boolean skipTopLevelFiles) throws IOException
	{
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath())))
		{
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null)
			{
				Path newPath = targetDir.resolve(entry.getName()).normalize();
				if (entry.isDirectory())
				{
					Files.createDirectories(newPath);
				}
				else
				{
					if (skipTopLevelFiles && entry.getName().indexOf('/') == -1)
					{
						continue;
					}
					Files.createDirectories(newPath.getParent());
					Files.copy(zis, newPath);
				}
				zis.closeEntry();
			}
		}
	}

	public static List<String> getTopLevelSubFolders(Path zipFilePath) throws IOException
	{
		List<String> subFolders = new ArrayList<>();

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFilePath)))
		{
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null)
			{
				String entryName = entry.getName();
				if (entry.isDirectory())
				{
					// Check if it's a top-level directory
					if (entryName.indexOf('/') == entryName.length() - 1)
					{
						subFolders.add(entryName.substring(0, entryName.length() - 1));
					}
				}
				zis.closeEntry();
			}
		}

		return subFolders;
	}
	

	public static void writeToFile(String fileName, String contents)
	{
		try
		{
			File file = new File(fileName);

			if (!file.exists())
			{
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			bw.write(contents);

			bw.close();
		}
		catch (IOException ex)
		{
			throw new RuntimeException("Helper.writeToFile caught error: " + ex.getMessage(), ex);
		}
	}
	
	public static void createFolder(String folderName)
	{
		File folder = new File(folderName);
		folder.mkdir();
	}

}
