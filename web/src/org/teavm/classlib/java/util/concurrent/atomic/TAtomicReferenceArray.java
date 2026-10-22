package org.teavm.classlib.java.util.concurrent.atomic;

/**
 * Stands in for java.util.concurrent.atomic.AtomicReferenceArray when compiling to JavaScript, where only one thread runs.
 */
public class TAtomicReferenceArray<E>
{
	private final Object[] array;

	public TAtomicReferenceArray(int length)
	{
		array = new Object[length];
	}

	public int length()
	{
		return array.length;
	}

	@SuppressWarnings("unchecked")
	public E get(int i)
	{
		return (E) array[i];
	}

	public void set(int i, E newValue)
	{
		array[i] = newValue;
	}

	public boolean compareAndSet(int i, E expectedValue, E newValue)
	{
		if (array[i] != expectedValue)
		{
			return false;
		}
		array[i] = newValue;
		return true;
	}
}
