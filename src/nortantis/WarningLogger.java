package nortantis;

import java.util.List;

public interface WarningLogger
{
	public void addWarningMessage(String message);

	public List<String> getWarningMessages();
}
