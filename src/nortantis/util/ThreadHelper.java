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
	private ExecutorService exService;
	private int threadCount;
	
	private ThreadHelper()
	{
		threadCount = Runtime.getRuntime().availableProcessors();
		exService = Executors.newFixedThreadPool(threadCount);
	}
	
	public static ThreadHelper getInstance()
	{
		if (instance == null)
		{
			instance = new ThreadHelper();
		}
		return instance;
	}
	
	
	public void processInParallel(List<Runnable> jobs)
	{
		List<Future<?>> futures = new ArrayList<Future<?>>();
		for (Runnable job : jobs)
		{
			futures.add(exService.submit(job));
		}

		for (int i : new Range(jobs.size()))
		{
			try
			{
				futures.get(i).get();
			}
			catch(ExecutionException e)
			{
				throw new RuntimeException(e);
			}
			catch(InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
	}
	
	public <T> List<T> processInParallelAndGetResult(List<Callable<T>> jobs)
	{
		List<Future<T>> futures = new ArrayList<>();
		List<T> results = new ArrayList<>();
		for (Callable<T> job : jobs)
		{
			futures.add(exService.submit(job));
		}

		for (int i : new Range(jobs.size()))
		{
			try
			{
				T result = futures.get(i).get();
				results.add(result);
			}
			catch(ExecutionException e)
			{
				throw new RuntimeException(e);
			}
			catch(InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
		
		return results;
	}
	
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
		
		ThreadHelper.getInstance().processInParallel(tasks);
	}
	
	public <T> Future<T> submit(Callable<T> job)
	{
		return exService.submit(job);
	}
	
	public Future<?> submit(Runnable job)
	{
		return exService.submit(job);
	}
	
	public int getThreadCount()
	{
		return threadCount;
	}
	
	@Override
    protected void finalize()
    {
		exService.shutdown();
    }
}
