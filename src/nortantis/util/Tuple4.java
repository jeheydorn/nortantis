package nortantis.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * A 4-tuple of objects which don't have to be comparable. For a comparable version of this, see Pair.
 * 
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class Tuple4<F, S, T, U> implements Serializable
{
	private F f;
	private S s;
	private T t;
	private U u;

	public Tuple4(F f, S l, T t, U u)
	{
		this.f = f;
		this.s = l;
		this.t = t;
		this.u = u;
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

	public U getFourth()
	{
		return u;
	}

	@Override
	public String toString()
	{
		return "(" + f + ", " + s + ", " + t + ", " + u + ")";
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(f, s, t, u);
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
		Tuple4 other = (Tuple4) obj;
		return Objects.equals(f, other.f) && Objects.equals(s, other.s) && Objects.equals(t, other.t) && Objects.equals(u, other.u);
	}

}