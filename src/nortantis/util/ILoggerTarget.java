package nortantis.util;

public interface ILoggerTarget
{
	public void appendLoggerMessage(String message);

	public void clearLoggerMessages();

	public boolean isReadyForLogging();
}
