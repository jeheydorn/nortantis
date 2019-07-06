package nortantis.util;

import java.util.Iterator;

public class Range implements Iterable<Integer>, Iterator<Integer>
{
	int current;
	int max;
	
	public Range(int max)
	{
		if (max < 0)
			throw new IllegalArgumentException("Max must be at least 0.");
		current = 0;
		this.max = max;
	}
	
	/**
	 * Creates an iterator to iterate from min (inclusive) to max (exclusive).
	 */
	public Range(int min, int max)
	{
		current = min;
		this.max = max;
	}
	
	@Override
	public boolean hasNext()
	{
		return current < max;
	}

	@Override
	public Integer next()
	{
		return current++;
	}

	@Override
	public void remove()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Integer> iterator()
	{
		return this;
	}
}
