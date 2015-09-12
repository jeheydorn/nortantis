package nortantis;

/**
 * For finding how long code takes to run.
 * @author joseph
 *
 */
public class StopWatch
{
	long startTime;
	
	public StopWatch()
	{
		startTime = System.currentTimeMillis();
	}
	
	public double getElapsedSeconds()
	{
		return (System.currentTimeMillis() - startTime) / 1000.0;
	}
}
