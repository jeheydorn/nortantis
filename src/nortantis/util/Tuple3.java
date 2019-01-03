package nortantis.util;

import java.io.Serializable;

/**
 * A 2-tuple of objects which don't have to be comparable. For a comparable version of this, see Pair.
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
	public boolean equals(Object other)
	{
		if (!(other instanceof Tuple3))
			return false;
		
		@SuppressWarnings("unchecked")
		Tuple3<F, S, T> otherTuple = (Tuple3<F, S, T>)other;
		return f.equals(otherTuple.f) && s.equals(otherTuple.s) && t.equals(otherTuple.t);
	}
	
}
