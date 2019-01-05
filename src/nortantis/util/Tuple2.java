package nortantis.util;

import java.io.Serializable;

/**
 * A 2-tuple of objects which don't have to be comparable. For a comparable version of this, see Pair.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class Tuple2<F, S> implements Serializable
{
	private F f;
	private S s;
	
	public Tuple2(F f, S l)
	{
		this.f = f;
		this.s = l;
	}
	
	public F getFirst()
	{
		return f;
	}

	public void setFirst(F f)
	{
		this.f = f;
	}

	public S getSecond()
	{
		return s;
	}
	
	public void setSecond(S s)
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
		if (!(other instanceof Tuple2))
			return false;
		
		@SuppressWarnings("unchecked")
		Tuple2<F, S> otherPair = (Tuple2<F, S>)other;
		return f.equals(otherPair.f) && s.equals(otherPair.s);
	}
	
}
