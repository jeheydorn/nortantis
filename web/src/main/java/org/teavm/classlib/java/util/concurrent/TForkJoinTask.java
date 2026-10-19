package org.teavm.classlib.java.util.concurrent;

/**
 * Stands in for java.util.concurrent.ForkJoinTask when compiling to JavaScript. Tasks run on the calling thread when they're invoked.
 */
public abstract class TForkJoinTask<V>
{
	protected abstract boolean exec();

	public final V invoke()
	{
		exec();
		return getRawResult();
	}

	public final V join()
	{
		return getRawResult();
	}

	public abstract V getRawResult();

	public static void invokeAll(TForkJoinTask<?> first, TForkJoinTask<?> second)
	{
		first.invoke();
		second.invoke();
	}

	public static boolean inForkJoinPool()
	{
		return false;
	}

	public static TForkJoinPool getPool()
	{
		return null;
	}
}
