package org.teavm.classlib.java.util.concurrent;

import java.util.List;

/**
 * Stands in for java.util.concurrent.ExecutorService when compiling to JavaScript.
 */
public interface TExecutorService
{
	void execute(Runnable command);

	TFuture<?> submit(Runnable task);

	@SuppressWarnings("rawtypes")
	TFuture submit(TCallable task);

	void shutdown();

	List<Runnable> shutdownNow();

	boolean isShutdown();

	boolean isTerminated();

	boolean awaitTermination(long timeout, TTimeUnit unit);
}
