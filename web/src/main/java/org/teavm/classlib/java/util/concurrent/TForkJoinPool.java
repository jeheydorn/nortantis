package org.teavm.classlib.java.util.concurrent;

/**
 * Stands in for java.util.concurrent.ForkJoinPool when compiling to JavaScript. It runs each task on the calling thread.
 */
public class TForkJoinPool
{
	public TForkJoinPool()
	{
	}

	public TForkJoinPool(int parallelism)
	{
	}

	public int getParallelism()
	{
		return 1;
	}

	public <T> T invoke(TForkJoinTask<T> task)
	{
		return task.invoke();
	}
}
