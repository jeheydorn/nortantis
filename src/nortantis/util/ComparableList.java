package nortantis.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A list which implements comparable. The method for comparing is like that for determining 
 * which of two words are higher in alphabetical order.
 * @author joseph
 *
 */
public class ComparableList<T extends Comparable<T>> extends ArrayList<T> implements Comparable<ComparableList<T>>
{
	private static final long serialVersionUID = 1L;
	
	public ComparableList(List<T> other)
	{
		super(other);
	}

	public ComparableList()
	{
	}

	public ComparableList(int initialCapacity)
	{
		super(initialCapacity);
	}

	@Override
	public int compareTo(ComparableList<T> other)
	{
		int i = 0;
		for (; i < Math.min(size(), other.size()); i++)
		{
			int c = get(i).compareTo(other.get(i));
			if (c < 0)
				return -1;
			if (c > 0)
				return 1;
		}
		
		// So far all elements are the same.
		
		if (size() < other.size())
			return -1;
		else if (other.size() < size())
			return 1;
		
		// The lists are the same length and all elements compare equal.
		return 0;
	}

}
