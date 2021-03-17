package nortantis.util;

import java.io.Serializable;

/**
 * Like Pair except the equals function considers 2 of these objects of equal if the order of first and 2nd have been swapped.
 *
 */
@SuppressWarnings("serial")
public class OrderlessPair<T> implements Serializable
{
	private T f;
	private T s;
	
	public OrderlessPair(T f, T l)
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
		if (!(other instanceof OrderlessPair))
			return false;
		
		@SuppressWarnings("unchecked")
		OrderlessPair<T> otherPair = (OrderlessPair<T>)other;
		if (f.equals(otherPair.f) && s.equals(otherPair.s))
		{
			return true;
		}
		if (f.equals(otherPair.s) && s.equals(otherPair.f))
		{
			// Opposite order is considered equal
			return true;
		}
		return false;
	}
	
}
