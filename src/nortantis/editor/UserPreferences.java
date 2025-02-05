package nortantis.editor;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import nortantis.swing.LookAndFeel;
import nortantis.util.FileHelper;
import nortantis.util.Logger;
import nortantis.util.OSHelper;

public class UserPreferences
{
	private final String userPrefsFileName = "user preferences";

	public DisplayQuality editorImageQuality = DisplayQuality.Low;
	private ArrayDeque<String> recentMapFilePaths = new ArrayDeque<>();
	private final int maxRecentMaps = 15;
	public String defaultCustomImagesPath;
	public boolean hideNewMapWithSameThemeRegionColorsMessage;
	public Set<String> collapsedPanels = new TreeSet<>();
	public String lastVersionFromCheck;
	public LocalDateTime lastVersionCheckTime;
	public LookAndFeel lookAndFeel = LookAndFeel.Dark;
	public int toolsPanelWidth;
	public int themePanelWidth;

	public static UserPreferences instance;

	public static UserPreferences getInstance()
	{
		if (instance == null)
		{
			instance = new UserPreferences();
		}
		return instance;
	}

	private UserPreferences()
	{
		final Properties props = new Properties();
		try
		{
			Path filePath = Paths.get(getSavePath().toString(), userPrefsFileName);
			if (Files.exists(filePath))
			{
				try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8))
				{
					props.load(reader);
				}

				if (props.containsKey("editorImageQuality"))
				{
					String quality = props.getProperty("editorImageQuality").replace("Very High", "Ultra").replace(" ", "_");
					editorImageQuality = DisplayQuality.valueOf(quality);
				}

				if (props.containsKey("recentMapFilePaths"))
				{
					String[] filePaths = props.getProperty("recentMapFilePaths").split("\t");
					for (String path : filePaths)
					{
						if (new File(path).exists())
						{
							recentMapFilePaths.add(path);
						}
					}
				}
				if (props.containsKey("defaultCustomImagesPath"))
				{
					defaultCustomImagesPath = FileHelper.replaceHomeFolderWithPlaceholder(props.getProperty("defaultCustomImagesPath"));
				}
				if (props.containsKey("showNewMapWithSameThemeRegionColorsMessage"))
				{
					String value = props.getProperty("showNewMapWithSameThemeRegionColorsMessage");
					hideNewMapWithSameThemeRegionColorsMessage = Boolean.parseBoolean(value);
				}

				if (props.containsKey("collapsedPanels"))
				{
					String[] panelNames = props.getProperty("collapsedPanels").split("\t");
					collapsedPanels = new TreeSet<>();
					collapsedPanels.addAll(Arrays.asList(panelNames));
				}

				if (props.containsKey("lastVersionFromCheck"))
				{
					lastVersionFromCheck = props.getProperty("lastVersionFromCheck");
				}
				if (props.containsKey("lastVersionCheckTime"))
				{
					// Convert the string back to LocalDateTime
					lastVersionCheckTime = LocalDateTime.parse(props.getProperty("lastVersionCheckTime"),
							DateTimeFormatter.ISO_LOCAL_DATE_TIME);
				}
				
				if (props.containsKey("lookAndFeel") && !StringUtils.isEmpty(props.getProperty("lookAndFeel")))
				{
					lookAndFeel = LookAndFeel.valueOf(props.getProperty("lookAndFeel"));
				}
				
				if (props.containsKey("toolsPanelWidth"))
				{
					toolsPanelWidth = Integer.parseInt(props.getProperty("toolsPanelWidth"));
				}
				
				if (props.containsKey("themePanelWidth"))
				{
					themePanelWidth = Integer.parseInt(props.getProperty("themePanelWidth"));
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Logger.printError("Error while loading user preferences:", e);
		}
	}

	public void addRecentMapFilePath(String filePath)
	{
		recentMapFilePaths.remove(filePath);
		recentMapFilePaths.addFirst(filePath);
		while (recentMapFilePaths.size() > maxRecentMaps)
		{
			recentMapFilePaths.pollLast();
		}
	}

	public Collection<String> getRecentMapFilePaths()
	{
		return Collections.unmodifiableCollection(recentMapFilePaths);
	}

	public void save()
	{
		Properties props = new Properties();
		props.setProperty("editorImageQuality", editorImageQuality.toString());
		props.setProperty("recentMapFilePaths", String.join("\t", recentMapFilePaths));
		props.setProperty("defaultCustomImagesPath", defaultCustomImagesPath == null ? "" : defaultCustomImagesPath);
		props.setProperty("showNewMapWithSameThemeRegionColorsMessage", hideNewMapWithSameThemeRegionColorsMessage + "");
		props.setProperty("collapsedPanels", String.join("\t", collapsedPanels));
		props.setProperty("lastVersionFromCheck", lastVersionFromCheck == null ? "" : lastVersionFromCheck);
		props.setProperty("lastVersionCheckTime",
				(lastVersionCheckTime == null ? LocalDateTime.MIN : lastVersionCheckTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		props.setProperty("lookAndFeel", lookAndFeel.name());
		props.setProperty("toolsPanelWidth", toolsPanelWidth + "");
		props.setProperty("themePanelWidth", themePanelWidth + "");

		try
		{
			Path savePath = getSavePath();
			Files.createDirectories(savePath);
			props.store(new PrintWriter(Paths.get(savePath.toString(), userPrefsFileName).toString()), "");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Logger.printError("Error while saving user preferences:", e);
		}
	}

	private Path getSavePath()
	{
		return OSHelper.getAppDataPath();
	}
}
