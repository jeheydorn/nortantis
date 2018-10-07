package nortantis.util;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import nortantis.RunSwing;

public class Logger
{
	
	public static void println()
	{		
		if (RunSwing.isRunning())
		{
			try
			{
				SwingUtilities.invokeAndWait(new Runnable()
				{
				    @Override
				    public void run()
				    {
				    	RunSwing.getConsoleOutputTextArea().append("\n");
				    }
				});
			} catch (InvocationTargetException | InterruptedException e)
			{
				e.printStackTrace();
			}
			
			
		}
		else
		{
			System.out.println();
		}
	}

	public static void println(final String message)
	{
		if (RunSwing.isRunning())
		{
			try
			{
				SwingUtilities.invokeAndWait(new Runnable()
				{
				    @Override
				    public void run()
				    {
				    	RunSwing.getConsoleOutputTextArea().append(message + "\n");
				    }
				});
			} catch (InvocationTargetException | InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			System.out.println(message);
		}
	}
}
