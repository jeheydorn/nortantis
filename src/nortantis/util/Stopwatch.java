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
		// Left at the time the stopwatch first started, so that time before then is not counted as time since a check.
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
		if (isRunning)
		{
			elapsed += (System.currentTimeMillis() - startTime);
		}
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

	/**
	 * Prints how long it has been since the last call to this method, or since this Stopwatch was created if this is the first call.
	 */
	@SuppressWarnings("unused")
	public void printTimeSinceLastCheck()
	{
		long currentTime = System.currentTimeMillis();
		double secondsSinceLastCheck = (currentTime - lastCheckTime) / 1000.0;
		lastCheckTime = currentTime;
		if (name != null && !name.isEmpty())
		{
			System.out.println("Time since last check to " + name + " (in seconds): " + secondsSinceLastCheck);
		}
		else
		{
			System.out.println("Time since last check (in seconds): " + secondsSinceLastCheck);
		}
	}
}
