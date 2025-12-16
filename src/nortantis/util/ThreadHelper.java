package nortantis.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
	 * Processes a list of jobs in parallel using a shared thread pool.
	 * 
	 * @param jobs
	 * @param useFixedThreadPool
	 *            Whether to use the thread pool with a limited number of threads vs the one that grows as needed. Warning: Never submit a
	 *            job that will submit more jobs to the fixed thread pool, as that can lead to a deadlock.
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
			threadPool = cachedThreadPool;
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
		} finally
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
	 * Processes a list of jobs in parallel that return results. Warning: Never submit a job that will submit more jobs using the methods in
	 * this class, as that can lead to a deadlock.
	 * 
	 * @param <T>
	 *            Result type
	 * @param jobs
	 * @param useFixedThreadPool
	 *            Whether to use the thread pool with a limited number of threads vs the one that grows as needed. Warning: Never submit a
	 *            job that will submit more jobs to the fixed thread pool, as that can lead to a deadlock.
	 * @return
	 */
	public <T> List<T> processInParallelAndGetResult(List<Callable<T>> jobs, boolean useFixedThreadPool)
	{
		List<Future<T>> futures = new ArrayList<>();
		List<T> results = new ArrayList<>();
		ExecutorService threadPool;
		if (useFixedThreadPool)
		{
			threadPool = Executors.newFixedThreadPool(threadCount);
		}
		else
		{
			threadPool = cachedThreadPool;
		}

		try
		{
			for (Callable<T> job : jobs)
			{
				futures.add(threadPool.submit(job));
			}

			for (int i : new Range(jobs.size()))
			{
				try
				{
					T result = futures.get(i).get();
					results.add(result);
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
		} finally
		{
			if (useFixedThreadPool)
			{
				threadPool.shutdown();
			}
		}

		return results;
	}

	/**
	 * Processes rows of data in parallel. Warning: Never submit a job that will submit more jobs using the methods in this class, as that
	 * can lead to a deadlock.
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
		return cachedThreadPool.submit(job);
	}

	public Future<?> submit(Runnable job)
	{
		return cachedThreadPool.submit(job);
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
