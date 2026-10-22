package org.teavm.classlib.java.util.concurrent;

/**
 * Stands in for java.util.concurrent.Future when compiling to JavaScript. Work submitted to an executor there has already finished by the
 * time it returns, so a future just holds the result.
 */
public interface TFuture<V>
{
	V get() throws TExecutionException;

	V get(long timeout, TTimeUnit unit) throws TExecutionException;

	boolean isDone();

	boolean isCancelled();

	boolean cancel(boolean mayInterruptIfRunning);
}
