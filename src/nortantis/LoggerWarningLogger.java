package nortantis;

import nortantis.util.Logger;

import java.util.ArrayList;
import java.util.List;

public class LoggerWarningLogger implements WarningLogger
{

	@Override
	public void addWarningMessage(String message)
	{
		Logger.println(message);
	}

	@Override
	public List<String> getWarningMessages()
	{
		return new ArrayList<>();
	}

}
