package nortantis.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * A 2-tuple of objects which don't have to be comparable. For a comparable version of this, see Tuple2Comp.
 * 
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
		Tuple2 other = (Tuple2) obj;
		return Objects.equals(f, other.f) && Objects.equals(s, other.s);
	}

}
