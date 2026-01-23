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
		println(message, true);
		if (e != null)
		{
			println(e.getMessage(), true);
			println(ExceptionUtils.getStackTrace(e), true);
		}
		else
		{
			println("Unable to print the rest of the error message because the exception was null.", true);
		}
	}

	public static void printError(String message)
	{
		println(message, true);
	}


	public static void println(final Object message)
	{
		println(message, false);
	}

	private static void println(final Object message, boolean isError)
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
			if (isError)
			{
				System.err.println(message);
			}
			else
			{
				System.out.println(message);
			}
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
		// TODO put back later
		getInstance().target.clearLoggerMessages();
	}

}
