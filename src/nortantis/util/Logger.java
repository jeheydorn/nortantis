package nortantis.util;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

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

	public static void println(final String message)
	{
		println(message, false);
	}
	
	public static void println(final String message, boolean runInCurrentThread)
	{
		if (getInstance().target != null && getInstance().target.isReadyForLogging())
		{
			if (runInCurrentThread)
			{
				getInstance().appendToTarget(message + "\n");
			}
			else
			{
				try
				{
					SwingUtilities.invokeAndWait(new Runnable()
					{
						@Override
						public void run()
						{
							getInstance().appendToTarget(message + "\n");
						}
					});
				}
				catch (InvocationTargetException | InterruptedException e)
				{
					e.printStackTrace();
				}
			}
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
			try
			{

				SwingUtilities.invokeAndWait(new Runnable()
				{
					@Override
					public void run()
					{
						getInstance().clearTarget();
					}
				});
			}
			catch (InvocationTargetException | InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private synchronized void clearTarget()
	{
		getInstance().target.clearLoggerMessages();
	}

	
}
