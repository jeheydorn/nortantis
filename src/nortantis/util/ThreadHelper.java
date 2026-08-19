package nortantis.util;

import nortantis.platform.ImageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ThreadHelper
{
	private static ThreadHelper instance;
	private ExecutorService cachedThreadPool;
	private final int threadCount;

	private ThreadHelper()
	{
		threadCount = Runtime.getRuntime().availableProcessors();
		cachedThreadPool = Executors.newCachedThreadPool();
	}

	public static ThreadHelper getInstance()
	{
		if (instance == null)
		{
			instance = new ThreadHelper();
		}
		return instance;
	}

	/**
	 * Returns the shared cached thread pool, recreating it if it has somehow been shut down. This class never intentionally shuts down the
	 * cached pool, so a shut-down pool only ever indicates a torn-down state - e.g. where the host process is reused across app restarts
	 * and a stale, finalized executor can leave the pool shut down. Recreating it here keeps map generation from failing with
	 * RejectedExecutionException after the app has been backgrounded and resumed.
	 */
	private synchronized ExecutorService getCachedThreadPool()
	{
		if (cachedThreadPool == null || cachedThreadPool.isShutdown())
		{
			cachedThreadPool = Executors.newCachedThreadPool();
		}
		return cachedThreadPool;
	}

	/**
	 * Processes a list of jobs in parallel, waiting for all of them to finish.
	 *
	 * @param jobs
	 * @param useFixedThreadPool
	 *            Whether to use a pool with a limited number of threads vs the shared one that grows as needed. The limited pool is built
	 *            for this call and shut down when it returns, so a job that parallelizes through this class again gets threads of its own
	 *            rather than waiting on the ones its caller holds. That keeps nesting from running out of threads, but it multiplies them:
	 *            every level deeper creates another pool the size of the processor count. Prefer to parallelize at one level only.
	 */
	public void processInParallel(List<Runnable> jobs, boolean useFixedThreadPool)
	{
		List<Future<?>> futures = new ArrayList<Future<?>>();
		ExecutorService threadPool;
		if (useFixedThreadPool)
		{
			threadPool = Executors.newFixedThreadPool(threadCount);
		}
		else
		{
			threadPool = getCachedThreadPool();
		}

		try
		{
			for (Runnable job : jobs)
			{
				futures.add(threadPool.submit(job));
			}

			for (int i : new Range(jobs.size()))
			{
				try
				{
					futures.get(i).get();
				}
				catch (ExecutionException e)
				{
					throw new RuntimeException(e);
				}
				catch (InterruptedException e)
				{
					throw new RuntimeException(e);
				}
			}
		}
		finally
		{
			if (useFixedThreadPool)
			{
				threadPool.shutdown();
			}
		}
	}

	/**
	 * Processes a list of jobs in the current thread.
	 */
	public void processSerial(List<Runnable> jobs)
	{
		for (Runnable job : jobs)
		{
			job.run();
		}
	}

	/**
	 * Processes rows of data in parallel. A row consumer that parallelizes through this class again gets a pool of its own for every call,
	 * multiplying threads by the processor count at each level, so prefer to parallelize at one level only.
	 * 
	 * @param startRow
	 * @param numRows
	 * @param rowConsumer
	 */
	public void processRowsInParallel(int startRow, int numRows, Consumer<Integer> rowConsumer)
	{
		int numTasks = getThreadCount();
		List<Runnable> tasks = new ArrayList<>(numTasks);
		int rowsPerJob = numRows / numTasks;
		for (int taskNumber : new Range(numTasks))
		{
			tasks.add(() ->
			{
				int endY = taskNumber == numTasks - 1 ? startRow + numRows : startRow + (taskNumber + 1) * rowsPerJob;
				for (int y = startRow + taskNumber * rowsPerJob; y < endY; y++)
				{
					rowConsumer.accept(y);
				}
			});
		}

		if (numRows < ImageHelper.minParallelRowCount)
		{
			ThreadHelper.getInstance().processSerial(tasks);
		}
		else
		{
			ThreadHelper.getInstance().processInParallel(tasks, true);
		}
	}

	public <T> Future<T> submit(Callable<T> job)
	{
		return getCachedThreadPool().submit(job);
	}

	@SuppressWarnings("unused")
	public Future<?> submit(Runnable job)
	{
		return getCachedThreadPool().submit(job);
	}

	public <T> T getResult(Future<T> task)
	{
		if (task == null)
		{
			return null;
		}
		try
		{
			return task.get();
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
		catch (ExecutionException e)
		{
			if (e.getCause() != null && e.getCause() instanceof RuntimeException)
			{
				throw (RuntimeException) e.getCause();
			}
			throw new RuntimeException(e);
		}
	}

	public int getThreadCount()
	{
		return threadCount;
	}
}
