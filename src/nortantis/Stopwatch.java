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

	public Stopwatch()
	{
		startTime = System.currentTimeMillis();
	}

	public Stopwatch(String name)
	{
		startTime = System.currentTimeMillis();
		this.name = name;
	}

	public double getElapsedSeconds()
	{
		return (System.currentTimeMillis() - startTime) / 1000.0;
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
