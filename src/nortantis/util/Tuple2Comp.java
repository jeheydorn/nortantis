package nortantis.util;

/**
 * A tuple of 2 items that implement comparable.

 * @param <F>
 * @param <S>
 */
public class Tuple2Comp<F extends Comparable<F>, S extends Comparable<S>> implements Comparable<Tuple2Comp<F, S>>
{
	private F f;
	private S s;
	
	public Tuple2Comp(F f, S l)
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
	public int compareTo(Tuple2Comp<F, S> other)
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
		if (!(other instanceof Tuple2Comp))
			return false;
		
		@SuppressWarnings("unchecked")
		Tuple2Comp<F, S> otherPair = (Tuple2Comp<F, S>)other;
		return f.equals(otherPair.f) && s.equals(otherPair.s);
	}
	
}
