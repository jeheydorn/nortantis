package nortantis;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;


public class UserPreferences
{
	private final String userPrefsFileName = "user preferences";
	
	public String lastLoadedSettingsFile = "";
	public String lastEditorTool = "";
	public String zoomLevel = "";
	public boolean hideMapChangesWarning;
	public boolean hideAspectRatioWarning;
	public boolean hideHeightMapWithEditsWarning;
	
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
			if (Files.exists(Paths.get(userPrefsFileName)))
			{
				props.load(new FileInputStream(userPrefsFileName));
				
				if (props.containsKey("lastLoadedSettingsFile"))
					lastLoadedSettingsFile = props.getProperty("lastLoadedSettingsFile");
				if (props.containsKey("lastEditTool"))
					lastEditorTool = props.getProperty("lastEditTool");
				if (props.containsKey("zoomLevel"))
					zoomLevel = props.getProperty("zoomLevel");
				if (props.containsKey("hideMapChangesWarning"))
					hideMapChangesWarning = Boolean.parseBoolean(props.getProperty("hideMapChangesWarning"));
				if (props.containsKey("hideAspectRatioWarning"))
					hideAspectRatioWarning = Boolean.parseBoolean(props.getProperty("hideAspectRatioWarning"));
				if (props.containsKey("hideHeightMapWithEditsWarning"))
					hideHeightMapWithEditsWarning = Boolean.parseBoolean(props.getProperty("hideHeightMapWithEditsWarning"));
			}
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void save()
	{
		Properties props = new Properties();
		props.setProperty("lastLoadedSettingsFile", lastLoadedSettingsFile);
		props.setProperty("lastEditTool", lastEditorTool);
		props.setProperty("zoomLevel", zoomLevel);
		props.setProperty("hideMapChangesWarning", hideMapChangesWarning + "");
		props.setProperty("hideAspectRatioWarning", hideAspectRatioWarning + "");
		props.setProperty("hideHeightMapWithEditsWarning", hideHeightMapWithEditsWarning + "");
		
		try
		{
			props.store(new PrintWriter(userPrefsFileName.toString()), "");
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
