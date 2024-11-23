package nortantis.util;

import org.apache.commons.lang3.exception.ExceptionUtils;

import nortantis.platform.PlatformFactory;

public class Logger
{
	private ILoggerTarget target;

	private Logger()
	{
	}

	private static Logger instance;

	private static Logger getInstance()
	{
		if (instance == null)
		{
			instance = new Logger();
		}

		return instance;
	}

	public static void setLoggerTarget(ILoggerTarget target)
	{
		getInstance().target = target;
	}

	public static void println()
	{
		println("");
	}

	public static void printError(String message, Throwable e)
	{
		if (e == null)
		{
			return;
		}

		println(message);
		println(e.getMessage());
		println(ExceptionUtils.getStackTrace(e));
	}

	public static void println(final Object message)
	{
		if (getInstance().target != null && getInstance().target.isReadyForLogging())
		{
			PlatformFactory.getInstance().doInMainUIThreadAsynchronous(() ->
			{
				getInstance().appendToTarget(message + "\n");
			});
		}
		else
		{
			System.out.println(message);
		}
	}

	private synchronized void appendToTarget(String message)
	{
		getInstance().target.appendLoggerMessage(message);
	}

	public static void clear()
	{
		if (getInstance().target != null && getInstance().target.isReadyForLogging())
		{
			PlatformFactory.getInstance().doInMainUIThreadAsynchronous(() ->
			{
				getInstance().clearTarget();
			});
		}
	}

	private synchronized void clearTarget()
	{
		getInstance().target.clearLoggerMessages();
	}

}
