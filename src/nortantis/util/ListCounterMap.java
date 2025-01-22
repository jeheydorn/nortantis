package nortantis.util;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Used to store a conditional probability distribution, where the conditioned variables are in a list.
 * 
 * @author joseph
 *
 */
public class ListCounterMap<T> implements Serializable
{
	private static final long serialVersionUID = 1L;
	private Map<List<T>, Counter<T>> map;

	public ListCounterMap()
	{
		map = new TreeMap<>();
	}

	public int size()
	{
		return map.keySet().size();
	}

	public void increamentCount(List<T> key, T value)
	{
		Counter<T> counter = map.get(key);
		if (counter == null)
		{
			counter = new HashCounter<T>();
			map.put(key, counter);
		}
		counter.incrementCount(value);
	}

	public double getCount(List<T> key, T value)
	{
		Counter<T> counter = map.get(key);
		if (counter == null)
			return 0.0;
		return counter.getCount(value);
	}

	/**
	 * If the given key has been seen, this returns a sample from the possible values, treated as a probability distribution conditioned on
	 * the key. If the key has not been seen, this returns null.
	 */
	public T sampleConditional(Random r, List<T> key)
	{
		Counter<T> counter = map.get(key);
		if (counter == null)
		{
			return null;
		}
		return counter.sample(r);
	}

	@Override
	public String toString()
	{
		StringBuilder result = new StringBuilder();
		for (Map.Entry<List<T>, Counter<T>> entry : map.entrySet())
		{
			List<T> key = entry.getKey();
			Counter<T> counter = entry.getValue();

			result.append("key: " + key + "\n");
			result.append("value: " + counter + "\n\n");
		}
		return result.toString();
	}

}
