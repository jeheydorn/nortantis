package nortantis.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * Like Pair except the equals function considers 2 of these objects equal if the order of first and 2nd have been swapped.
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

	/**
	 * Not generated code.
	 */
	@Override
	public int hashCode()
	{
		return Objects.hash(f) + Objects.hash(s);
	}

	/**
	 * Not generated code.
	 */
	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof OrderlessPair))
			return false;

		@SuppressWarnings("unchecked")
		OrderlessPair<T> otherPair = (OrderlessPair<T>) other;
		if (Objects.equals(f, otherPair.f) && Objects.equals(s, otherPair.s))
		{
			return true;
		}
		if (Objects.equals(f, otherPair.s) && Objects.equals(s, otherPair.f))
		{
			// Opposite order is considered equal
			return true;
		}
		return false;
	}

}
