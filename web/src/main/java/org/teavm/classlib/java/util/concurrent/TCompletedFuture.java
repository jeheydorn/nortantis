package org.teavm.classlib.java.util.concurrent;

/**
 * A future for work that has already run.
 */
class TCompletedFuture<V> implements TFuture<V>
{
	private final V result;
	private final Throwable failure;

	TCompletedFuture(V result, Throwable failure)
	{
		this.result = result;
		this.failure = failure;
	}

	@Override
	public V get() throws TExecutionException
	{
		if (failure != null)
		{
			throw new TExecutionException(failure);
		}
		return result;
	}

	@Override
	public V get(long timeout, TTimeUnit unit) throws TExecutionException
	{
		return get();
	}

	@Override
	public boolean isDone()
	{
		return true;
	}

	@Override
	public boolean isCancelled()
	{
		return false;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning)
	{
		return false;
	}
}
