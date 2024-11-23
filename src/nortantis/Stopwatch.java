package nortantis;

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

	public Stopwatch()
	{
		startTime = System.currentTimeMillis();
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
	}

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
}
