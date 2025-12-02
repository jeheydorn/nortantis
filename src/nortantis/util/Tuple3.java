package nortantis.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * A 2-tuple of objects which don't have to be comparable. For a comparable version of this, see Pair.
 * 
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class Tuple3<F, S, T> implements Serializable
{
	private F f;
	private S s;
	private T t;

	public Tuple3(F f, S l, T t)
	{
		this.f = f;
		this.s = l;
		this.t = t;
	}

	public F getFirst()
	{
		return f;
	}

	public S getSecond()
	{
		return s;
	}

	public T getThird()
	{
		return t;
	}

	@Override
	public String toString()
	{
		return "(" + f + ", " + s + ", " + t + ")";
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(f, s, t);
	}

	@SuppressWarnings("rawtypes")
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
		Tuple3 other = (Tuple3) obj;
		return Objects.equals(f, other.f) && Objects.equals(s, other.s) && Objects.equals(t, other.t);
	}


}
