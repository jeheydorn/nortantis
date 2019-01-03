package nortantis.util;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class Counter <T extends Comparable<T>> implements Serializable
{
	Map<T, Integer> counts;
	int totalCount;
	
	private static final long serialVersionUID = 1L;

	public Counter()
	{
		counts = new TreeMap<>();
		totalCount = 0;
	}
	
	public void incrementCount(T item)
	{
		if (!counts.containsKey(item))
			counts.put(item, 1);
		else
			counts.put(item, counts.get(item) + 1);
		totalCount++;
	}
	
	public void addCount(T item, int count)
	{
		if (!counts.containsKey(item))
			counts.put(item, count);
		else
			counts.put(item, counts.get(item) + count);
		totalCount += count;		
	}
	
	public double getCount(T item)
	{
		return counts.get(item);
	}
	
	public T sample(Random r)
	{
		double uniformSample = r.nextDouble();
		uniformSample *= totalCount;

		double acc = 0;
		Iterator<Map.Entry<T, Integer>> iter = counts.entrySet().iterator();
		while(true)
		{
			Map.Entry<T, Integer> entry = iter.next();
			T item = entry.getKey();
			int count = entry.getValue();
			acc += count;
			if (acc >= uniformSample)
				return item;
		}
	}	
}
