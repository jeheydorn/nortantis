package nortantis.util;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import nortantis.swing.MainWindow;

public class Logger
{

	public static void println()
	{
		if (MainWindow.isRunning())
		{
			try
			{
				SwingUtilities.invokeAndWait(new Runnable()
				{
					@Override
					public void run()
					{
						MainWindow.getConsoleOutputTextArea().append("\n");
					}
				});
			}
			catch (InvocationTargetException | InterruptedException e)
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
		if (MainWindow.isRunning())
		{
			try
			{
				SwingUtilities.invokeAndWait(new Runnable()
				{
					@Override
					public void run()
					{
						MainWindow.getConsoleOutputTextArea().append(message + "\n");
					}
				});
			}
			catch (InvocationTargetException | InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			System.out.println(message);
		}
	}

	public static void clear()
	{
		if (MainWindow.isRunning())
		{
			try
			{

				SwingUtilities.invokeAndWait(new Runnable()
				{
					@Override
					public void run()
					{
						MainWindow.getConsoleOutputTextArea().setText("");
					}
				});
			}
			catch (InvocationTargetException | InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
}
