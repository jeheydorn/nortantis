package nortantis.util;

import java.io.Serializable;

/**
 * A 2-tuple of objects which are the same type and don't have to be comparable.
 *
 */
@SuppressWarnings("serial")
public class Pair<T> implements Serializable
{
	private T f;
	private T s;
	
	public Pair(T f, T l)
	{
		this.f = f;
		this.s = l;
	}
	
	public T getFirst()
	{
		return f;
	}

	public void setFirst(T f)
	{
		this.f = f;
	}

	public T getSecond()
	{
		return s;
	}
	
	public void setSecond(T s)
	{
		this.s = s;
	}
	
	
	@Override
	public String toString()
	{
		return "(" + f.toString() + ", " + s.toString() + ")";
	}
	
	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof Pair))
			return false;
		
		@SuppressWarnings("unchecked")
		Pair<T> otherPair = (Pair<T>)other;
		return f.equals(otherPair.f) && s.equals(otherPair.s);
	}
	
}
