package nortantis.editor;

import nortantis.geom.IntRectangle;
import nortantis.swing.LookAndFeel;
import nortantis.util.FileHelper;
import nortantis.util.Logger;
import nortantis.util.OSHelper;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UserPreferences
{
	private final String userPrefsFileName = "user preferences";

	public DisplayQuality editorImageQuality = DisplayQuality.Low;
	private ArrayDeque<String> recentMapFilePaths = new ArrayDeque<>();
	private final int maxRecentMaps = 15;
	public String defaultCustomImagesPath;
	public boolean hideNewMapWithSameThemeRegionColorsMessage;
	public boolean hideGridOverlaySeizureWarning;
	public boolean hideThemeChangedMessage;
	public boolean hideStartupSupportPanel;
	public Set<String> collapsedPanels = new TreeSet<>();
	/**
	 * True when no preferences file existed at launch (a new install, or the user deleted the file to reset preferences). Used to apply
	 * new-install-only defaults without changing existing users' choices.
	 */
	public final boolean isFirstRun;
	public String lastVersionFromCheck;
	public LocalDateTime lastVersionCheckTime;
	public LookAndFeel lookAndFeel = LookAndFeel.Dark;
	public int toolsPanelWidth;
	public int themePanelWidth;
	public String language;
	/**
	 * The bounds of the main window the last time it was closed while not maximized, or null if they are unknown.
	 */
	public IntRectangle windowBounds;
	public boolean isWindowMaximized;

	/**
	 * Messages describing preferences that failed to load. Used to warn the user that some of their preferences may have been reset.
	 */
	private final List<String> loadErrorMessages = new ArrayList<>();

	/**
	 * Where the previous (likely corrupted) preferences file was backed up to when a load error occurred, or null if no backup was made.
	 */
	private Path corruptedFileBackupPath;

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
		Path filePath = Paths.get(getSavePath().toString(), userPrefsFileName);

		isFirstRun = !Files.exists(filePath);

		// A missing file is expected (first launch, or the user deliberately deleted it to reset their preferences), so it is not an error.
		if (isFirstRun)
		{
			return;
		}

		try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8))
		{
			props.load(reader);
		}
		catch (Exception e)
		{
			String message = "The user preferences file could not be read and may be corrupted: " + filePath;
			Logger.printError(message, e);
			loadErrorMessages.add(message);
		}

		// Each preference is parsed independently so that one corrupted value cannot prevent the rest from loading.
		if (props.containsKey("editorImageQuality"))
		{
			tryLoad(props, "editorImageQuality", () ->
			{
				String quality = props.getProperty("editorImageQuality").replace("Very High", "Ultra").replace(" ", "_");
				editorImageQuality = DisplayQuality.valueOf(quality);
			});
		}

		if (props.containsKey("recentMapFilePaths"))
		{
			tryLoad(props, "recentMapFilePaths", () ->
			{
				String[] filePaths = props.getProperty("recentMapFilePaths").split("\t");
				for (String path : filePaths)
				{
					if (!path.isBlank() && new File(path).exists())
					{
						recentMapFilePaths.add(path);
					}
				}
			});
		}
		if (props.containsKey("defaultCustomImagesPath"))
		{
			tryLoad(props, "defaultCustomImagesPath", () -> defaultCustomImagesPath = FileHelper.replaceHomeFolderWithPlaceholder(props.getProperty("defaultCustomImagesPath")));
		}

		// I used the wrong name when creating this property, but changing it now would make the popup show up for existing users,
		// and there is no functional problem with just leaving it.
		if (props.containsKey("showNewMapWithSameThemeRegionColorsMessage"))
		{
			tryLoad(props, "showNewMapWithSameThemeRegionColorsMessage", () ->
			{
				String value = props.getProperty("showNewMapWithSameThemeRegionColorsMessage");
				hideNewMapWithSameThemeRegionColorsMessage = Boolean.parseBoolean(value);
			});
		}

		if (props.containsKey("collapsedPanels"))
		{
			tryLoad(props, "collapsedPanels", () ->
			{
				String[] panelNames = props.getProperty("collapsedPanels").split("\t");
				collapsedPanels = new TreeSet<>();
				collapsedPanels.addAll(Arrays.asList(panelNames));
			});
		}

		if (props.containsKey("lastVersionFromCheck"))
		{
			tryLoad(props, "lastVersionFromCheck", () -> lastVersionFromCheck = props.getProperty("lastVersionFromCheck"));
		}
		if (props.containsKey("lastVersionCheckTime"))
		{
			tryLoad(props, "lastVersionCheckTime", () -> lastVersionCheckTime = LocalDateTime.parse(props.getProperty("lastVersionCheckTime"), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		}

		if (props.containsKey("lookAndFeel") && !StringUtils.isEmpty(props.getProperty("lookAndFeel")))
		{
			tryLoad(props, "lookAndFeel", () -> lookAndFeel = LookAndFeel.valueOf(props.getProperty("lookAndFeel")));
		}

		if (props.containsKey("toolsPanelWidth"))
		{
			tryLoad(props, "toolsPanelWidth", () -> toolsPanelWidth = Integer.parseInt(props.getProperty("toolsPanelWidth")));
		}

		if (props.containsKey("themePanelWidth"))
		{
			tryLoad(props, "themePanelWidth", () -> themePanelWidth = Integer.parseInt(props.getProperty("themePanelWidth")));
		}

		if (props.containsKey("hideGridOverlaySeizureWarning"))
		{
			tryLoad(props, "hideGridOverlaySeizureWarning", () -> hideGridOverlaySeizureWarning = Boolean.parseBoolean(props.getProperty("hideGridOverlaySeizureWarning")));
		}

		if (props.containsKey("language"))
		{
			tryLoad(props, "language", () -> language = props.getProperty("language"));
		}

		if (props.containsKey("hideThemeChangedMessage"))
		{
			tryLoad(props, "hideThemeChangedMessage", () -> hideThemeChangedMessage = Boolean.parseBoolean(props.getProperty("hideThemeChangedMessage")));
		}

		if (props.containsKey("hideStartupSupportPanel"))
		{
			tryLoad(props, "hideStartupSupportPanel", () -> hideStartupSupportPanel = Boolean.parseBoolean(props.getProperty("hideStartupSupportPanel")));
		}

		if (props.containsKey("windowBounds") && !StringUtils.isEmpty(props.getProperty("windowBounds")))
		{
			tryLoad(props, "windowBounds", () -> windowBounds = parseWindowBounds(props.getProperty("windowBounds")));
		}

		if (props.containsKey("isWindowMaximized"))
		{
			tryLoad(props, "isWindowMaximized", () -> isWindowMaximized = Boolean.parseBoolean(props.getProperty("isWindowMaximized")));
		}

		// If anything failed to load, preserve a copy of the original file so the user can recover values that we are about to overwrite
		// the
		// next time preferences are saved.
		if (!loadErrorMessages.isEmpty())
		{
			backUpCorruptedFile(filePath);
		}
	}

	private static IntRectangle parseWindowBounds(String value)
	{
		String[] pieces = value.split(",");
		if (pieces.length != 4)
		{
			throw new IllegalArgumentException("Expected 4 comma separated numbers but found " + pieces.length + ".");
		}
		return new IntRectangle(Integer.parseInt(pieces[0].trim()), Integer.parseInt(pieces[1].trim()), Integer.parseInt(pieces[2].trim()), Integer.parseInt(pieces[3].trim()));
	}

	private static String formatWindowBounds(IntRectangle bounds)
	{
		return bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
	}

	/**
	 * Runs a single preference's load logic, recording (rather than propagating) any error so that a single corrupted value cannot prevent
	 * the rest of the preferences from loading.
	 */
	private void tryLoad(Properties props, String preferenceName, Runnable loadAction)
	{
		try
		{
			loadAction.run();
		}
		catch (Exception e)
		{
			String message = createLoadErrorMessage(preferenceName, props.getProperty(preferenceName));
			Logger.printError(message, e);
			loadErrorMessages.add(message);
		}
	}

	/**
	 * Creates a single, consistent message describing a preference that failed to load, including the value that could not be parsed. Used
	 * both for logging and for warning the user so the two stay in sync.
	 */
	private static String createLoadErrorMessage(String preferenceName, String badValue)
	{
		return "The user preference '" + preferenceName + "' could not be loaded from the value '" + badValue + "' and may have been reset to its default.";
	}

	private void backUpCorruptedFile(Path filePath)
	{
		try
		{
			Path backupPath = Paths.get(getSavePath().toString(), userPrefsFileName + ".corrupted");
			Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
			corruptedFileBackupPath = backupPath;
		}
		catch (Exception e)
		{
			Logger.printError("Error while trying to back up the corrupted user preferences file:", e);
		}
	}

	/**
	 * @return A message describing any problem encountered while loading preferences, suitable for showing to the user, or null if the
	 *         preferences loaded without error.
	 */
	public String getLoadErrorMessage()
	{
		if (loadErrorMessages.isEmpty())
		{
			return null;
		}

		StringBuilder message = new StringBuilder();
		message.append("Some of your user preferences could not be loaded and may have been reset to their defaults.\n\n");
		for (String loadErrorMessage : loadErrorMessages)
		{
			message.append(loadErrorMessage).append("\n");
		}
		if (corruptedFileBackupPath != null)
		{
			message.append("\nA copy of the previous preferences file was saved to:\n").append(corruptedFileBackupPath).append("\n");
		}
		return message.toString();
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
		props.setProperty("editorImageQuality", editorImageQuality.name().replace("_", " "));
		props.setProperty("recentMapFilePaths", String.join("\t", recentMapFilePaths));
		props.setProperty("defaultCustomImagesPath", defaultCustomImagesPath == null ? "" : defaultCustomImagesPath);
		props.setProperty("showNewMapWithSameThemeRegionColorsMessage", hideNewMapWithSameThemeRegionColorsMessage + "");
		props.setProperty("hideGridOverlaySeizureWarning", hideGridOverlaySeizureWarning + "");
		props.setProperty("collapsedPanels", String.join("\t", collapsedPanels));
		props.setProperty("lastVersionFromCheck", lastVersionFromCheck == null ? "" : lastVersionFromCheck);
		props.setProperty("lastVersionCheckTime", (lastVersionCheckTime == null ? LocalDateTime.MIN : lastVersionCheckTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		props.setProperty("lookAndFeel", lookAndFeel.name());
		props.setProperty("toolsPanelWidth", toolsPanelWidth + "");
		props.setProperty("themePanelWidth", themePanelWidth + "");
		props.setProperty("language", language == null ? "" : language);
		props.setProperty("hideThemeChangedMessage", hideThemeChangedMessage + "");
		props.setProperty("hideStartupSupportPanel", hideStartupSupportPanel + "");
		props.setProperty("windowBounds", windowBounds == null ? "" : formatWindowBounds(windowBounds));
		props.setProperty("isWindowMaximized", isWindowMaximized + "");

		try
		{
			Path savePath = getSavePath();
			Files.createDirectories(savePath);
			Path filePath = Paths.get(savePath.toString(), userPrefsFileName);
			// Write as UTF-8 to match the UTF-8 reader in the constructor. Using a PrintWriter here would use the platform-default charset,
			// which can produce bytes the reader rejects, causing the whole file to fail to load on the next launch.
			try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8))
			{
				props.store(writer, "");
			}
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
