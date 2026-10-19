package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs each task on the calling thread as soon as it's submitted. A browser page runs Java on a single thread, so there is nothing to run
 * tasks in parallel on.
 */
class TCallingThreadExecutorService implements TExecutorService
{
	private boolean isShutdown;

	@Override
	public void execute(Runnable command)
	{
		command.run();
	}

	@Override
	public TFuture<?> submit(Runnable task)
	{
		try
		{
			task.run();
			return new TCompletedFuture<>(null, null);
		}
		catch (RuntimeException | Error e)
		{
			return new TCompletedFuture<>(null, e);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public TFuture submit(TCallable task)
	{
		try
		{
			return new TCompletedFuture<>(task.call(), null);
		}
		catch (Exception | Error e)
		{
			return new TCompletedFuture<>(null, e);
		}
	}

	@Override
	public void shutdown()
	{
		isShutdown = true;
	}

	@Override
	public List<Runnable> shutdownNow()
	{
		isShutdown = true;
		return new ArrayList<>();
	}

	@Override
	public boolean isShutdown()
	{
		return isShutdown;
	}

	@Override
	public boolean isTerminated()
	{
		return isShutdown;
	}

	@Override
	public boolean awaitTermination(long timeout, TTimeUnit unit)
	{
		return true;
	}
}
