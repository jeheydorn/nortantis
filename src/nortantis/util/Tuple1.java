package nortantis.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * A 1-tuple. Useful when you need to wrap an object with a pointer.
 * 
 * @param <F>
 */
@SuppressWarnings("serial")
public class Tuple1<F> implements Serializable
{
	private F f;

	public Tuple1(F f)
	{
		this.f = f;
	}

	public Tuple1()
	{
	}

	public F get()
	{
		return f;
	}

	public void set(F f)
	{
		this.f = f;
	}

	@Override
	public String toString()
	{
		return "(" + f + ")";
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(f);
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
		Tuple1 other = (Tuple1) obj;
		return Objects.equals(f, other.f);
	}
}
