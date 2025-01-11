package nortantis.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class HashCounter<T> implements Serializable, Counter<T>
{
	Map<T, Integer> counts;
	int totalCount;

	private static final long serialVersionUID = 1L;

	public HashCounter()
	{
		counts = new HashMap<>();
		totalCount = 0;
	}

	@Override
	public void incrementCount(T item)
	{
		Integer count = counts.get(item);
		if (count == null)
		{
			counts.put(item, 1);
		}
		else
		{
			counts.put(item, count + 1);
		}
		totalCount++;
	}

	@Override
	public void addCount(T item, int count)
	{
		if (!counts.containsKey(item))
			counts.put(item, count);
		else
			counts.put(item, counts.get(item) + count);
		totalCount += count;
	}

	@Override
	public double getCount(T item)
	{
		return counts.get(item);
	}

	@Override
	public T sample(Random r)
	{
		double uniformSample = r.nextDouble();
		uniformSample *= totalCount;

		double acc = 0;
		Iterator<Map.Entry<T, Integer>> iter = counts.entrySet().iterator();
		while (true)
		{
			Map.Entry<T, Integer> entry = iter.next();
			T item = entry.getKey();
			int count = entry.getValue();
			acc += count;
			if (acc >= uniformSample)
				return item;
		}
	}

	@Override
	public T argmax()
	{
		return Helper.argmax(counts);
	}

	@Override
	public boolean isEmpty()
	{
		return totalCount == 0;
	}
}
