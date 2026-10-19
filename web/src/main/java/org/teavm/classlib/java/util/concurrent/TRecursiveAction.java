package org.teavm.classlib.java.util.concurrent;

/**
 * Stands in for java.util.concurrent.RecursiveAction when compiling to JavaScript.
 */
public abstract class TRecursiveAction extends TForkJoinTask<Void>
{
	protected abstract void compute();

	@Override
	protected final boolean exec()
	{
		compute();
		return true;
	}

	@Override
	public final Void getRawResult()
	{
		return null;
	}
}
