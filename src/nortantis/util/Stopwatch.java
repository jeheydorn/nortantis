package nortantis.util;

/**
 * For finding how long code takes to run.
 * 
 * @author joseph
 *
 */
public class Stopwatch
{
	long startTime;
	String name;
	long accumulatedTicks;
	boolean isRunning;
	long lastCheckTime;

	public Stopwatch()
	{
		startTime = System.currentTimeMillis();
		lastCheckTime = startTime;
	}

	public Stopwatch(String name)
	{
		this(name, true);
	}

	public Stopwatch(String name, boolean startTimerNow)
	{
		this.name = name;
		if (startTimerNow)
		{
			startOrContinue();
		}
	}

	public void startOrContinue()
	{
		if (isRunning)
		{
			return;
		}
		isRunning = true;
		startTime = System.currentTimeMillis();
		if (lastCheckTime == 0)
		{
			lastCheckTime = startTime;
		}
	}

	@SuppressWarnings("unused")
	public void pause()
	{
		if (isRunning)
		{
			accumulatedTicks += (System.currentTimeMillis() - startTime);
			isRunning = false;
		}
	}

	public double getElapsedSeconds()
	{
		long elapsed = 0;
		long currentTime = System.currentTimeMillis();
		if (isRunning)
		{
			elapsed += (currentTime - startTime);
		}
		lastCheckTime = currentTime;
		return (accumulatedTicks + elapsed) / 1000.0;
	}

	public String toString()
	{
		if (name != null && !name.isEmpty())
		{
			return "Elapsed time to " + name + " (in seconds): " + getElapsedSeconds();
		}
		return "Elapsed time (in seconds): " + getElapsedSeconds();
	}

	public void printElapsedTime()
	{
		System.out.println(toString());
	}

	public void printTimeSinceLastCheck()
	{
		long currentTime = System.currentTimeMillis();
		long diff = (currentTime - lastCheckTime);
		lastCheckTime = currentTime;
		System.out.println("Time since last check: " + diff / 1000.0);
	}

}
