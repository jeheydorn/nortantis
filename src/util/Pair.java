package util;

import java.io.Serializable;

@SuppressWarnings("serial")
public class Pair<F extends Comparable<F>, S extends Comparable<S>> implements Comparable<Pair<F, S>>, Serializable
{
	private F f;
	private S s;
	
	public Pair(F f, S l)
	{
		this.f = f;
		this.s = l;
	}
	
	public F getFirst()
	{
		return f;
	}
	
	public S getSecond()
	{
		return s;
	}
	
	@Override
	public String toString()
	{
		return "(" + f.toString() + ", " + s.toString() + ")";
	}

	@Override
	public int compareTo(Pair<F, S> other)
	{
		int c1 = f.compareTo(other.f);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;
		
		int c2 = s.compareTo(other.s);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;
	
		return 0;
	}
	
	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof Pair))
			return false;
		
		@SuppressWarnings("unchecked")
		Pair<F, S> otherPair = (Pair<F, S>)other;
		return f.equals(otherPair.f) && s.equals(otherPair.s);
	}
	
}
