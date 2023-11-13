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
	private ExecutorService fixedThreadPool;
	private ExecutorService cachedThreadPool;
	private int threadCount;

	private ThreadHelper()
	{
		threadCount = Runtime.getRuntime().availableProcessors();
		fixedThreadPool = Executors.newFixedThreadPool(threadCount);
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
	 * @param jobs
	 * @param useFixedThreadPool Whether to use the thread pool with a limited number of threads vs the one that grows as needed. Warning: Never submit a job that will
	 * submit more jobs to the fixed thread pool, as that can lead to a deadlock.
	 */
	public void processInParallel(List<Runnable> jobs, boolean useFixedThreadPool)
	{
		List<Future<?>> futures = new ArrayList<Future<?>>();
		for (Runnable job : jobs)
		{
			if (useFixedThreadPool)
			{
				futures.add(fixedThreadPool.submit(job));
			}
			else
			{
				futures.add(cachedThreadPool.submit(job));
			}
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

	/**
	 * Processes a list of jobs in parallel that return results.
	 * Warning: Never submit a job that will submit more jobs using the methods in this class, as that can lead to a deadlock.
	 * @param <T> Result type
	 * @param jobs
	 * @param useFixedThreadPool Whether to use the thread pool with a limited number of threads vs the one that grows as needed. Warning: Never submit a job that will
	 * submit more jobs to the fixed thread pool, as that can lead to a deadlock.
	 * @return
	 */
	public <T> List<T> processInParallelAndGetResult(List<Callable<T>> jobs, boolean useFixedThreadPool)
	{
		List<Future<T>> futures = new ArrayList<>();
		List<T> results = new ArrayList<>();
		for (Callable<T> job : jobs)
		{
			if (useFixedThreadPool)
			{
				futures.add(fixedThreadPool.submit(job));
			}
			else
			{
				futures.add(cachedThreadPool.submit(job));
			}

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

		return results;
	}

	/**
	 * Processes rows of data in parallel.
	 * Warning: Never submit a job that will submit more jobs using the methods in this class, as that can lead to a deadlock.
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

		ThreadHelper.getInstance().processInParallel(tasks, true);
	}

	public <T> Future<T> submit(Callable<T> job, boolean useFixedThreadPool)
	{
		if (useFixedThreadPool)
		{
			return fixedThreadPool.submit(job);
		}
		else
		{
			return cachedThreadPool.submit(job);
		}
	}

	public Future<?> submit(Runnable job, boolean useFixedThreadPool)
	{
		if (useFixedThreadPool)
		{
			return fixedThreadPool.submit(job);
		}
		else
		{
			return cachedThreadPool.submit(job);
		}
	}

	public int getThreadCount()
	{
		return threadCount;
	}

	@Override
	protected void finalize()
	{
		fixedThreadPool.shutdown();
		cachedThreadPool.shutdown();
	}
}
