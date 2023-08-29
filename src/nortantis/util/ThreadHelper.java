package nortantis.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
