package nortantis;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;


public class UserPreferences
{
	private final String userPrefsFileName = "user_preferences";
	
	public String lastLoadedSettingsFile = "";
	
	public UserPreferences()
	{
		final Properties props = new Properties();
		try
		{
			if (Files.exists(Paths.get(userPrefsFileName)))
			{
				props.load(new FileInputStream("user_preferences"));
				
				lastLoadedSettingsFile = props.getProperty("lastLoadedSettingsFile");
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
