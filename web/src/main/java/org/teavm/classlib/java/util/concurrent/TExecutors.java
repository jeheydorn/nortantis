package org.teavm.classlib.java.util.concurrent;

/**
 * Stands in for java.util.concurrent.Executors when compiling to JavaScript. Every executor runs its tasks on the calling thread.
 */
public class TExecutors
{
	public static TExecutorService newCachedThreadPool()
	{
		return new TCallingThreadExecutorService();
	}

	public static TExecutorService newCachedThreadPool(TThreadFactory threadFactory)
	{
		return new TCallingThreadExecutorService();
	}

	public static TExecutorService newFixedThreadPool(int threadCount)
	{
		return new TCallingThreadExecutorService();
	}

	public static TExecutorService newFixedThreadPool(int threadCount, TThreadFactory threadFactory)
	{
		return new TCallingThreadExecutorService();
	}

	public static TExecutorService newSingleThreadExecutor()
	{
		return new TCallingThreadExecutorService();
	}

	public static TThreadFactory defaultThreadFactory()
	{
		return Thread::new;
	}
}
