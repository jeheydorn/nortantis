package nortantis.util;

import java.io.Serializable;

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
	public boolean equals(Object other)
	{
		if (!(other instanceof Tuple4))
			return false;

		@SuppressWarnings("unchecked")
		Tuple4<F, S, T, U> otherTuple = (Tuple4<F, S, T, U>) other;

		return f.equals(otherTuple.f) && s.equals(otherTuple.s) && t.equals(otherTuple.t) && u.equals(otherTuple.u);
	}
}