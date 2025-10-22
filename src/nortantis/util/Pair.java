package nortantis.util;

import java.io.Serializable;
import java.util.Objects;

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
	public int hashCode()
	{
		return Objects.hash(f, s);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		@SuppressWarnings("rawtypes")
		Pair other = (Pair) obj;
		return Objects.equals(f, other.f) && Objects.equals(s, other.s);
	}

}
