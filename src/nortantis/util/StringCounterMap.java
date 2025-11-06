package nortantis.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Stores a conditional probability distribution, where the conditioned variable is a string.
 * 
 * @author joseph
 */
public class StringCounterMap implements Serializable
{
	private static final long serialVersionUID = 1L;
	private Map<String, Counter<Character>> map;

	public StringCounterMap()
	{
		map = new HashMap<>();
	}

	public int size()
	{
		return map.keySet().size();
	}

	public void incrementCount(String key, Character value)
	{
		Counter<Character> counter = map.get(key);
		if (counter == null)
		{
			counter = new HashCounter<>();
			map.put(key, counter);
		}
		counter.incrementCount(value);
	}

	public double getCount(String key, Character value)
	{
		Counter<Character> counter = map.get(key);
		if (counter == null)
			return 0.0;
		return counter.getCount(value);
	}

	/**
	 * If the given key has been seen, this returns a sample from the possible values, treated as a probability distribution conditioned on
	 * the key. If the key has not been seen, this returns null.
	 */
	public Character sampleConditional(Random r, String key)
	{
		Counter<Character> counter = map.get(key);
		if (counter == null)
		{
			return null;
		}
		return counter.sample(r);
	}
}
