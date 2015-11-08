package nortantis;

import hoten.voronoi.Center;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A set of Center, designed be extra fast by using a bit set internally.
 * 
 * TODO I tested this and found it isn't faster than HashSet. Consider removing it.
 * @author joseph
 *
 */
public class CenterSet implements Set<Center>
{
	private BitSet bitSet;
	private List<Center> centers;
	
	/**
	 * Creates an empty set of centers.
	 * @param allCenters All Centers that could ever be part of this set.
	 */
	public CenterSet(List<Center> allCenters)
	{
		bitSet = new BitSet(allCenters.size());
		this.centers = allCenters;
	}

	/**
	 * Creates a set of centers containing all centers in toAdd.
	 * @param allCenters All Centers that could ever be part of this set.
	 */
	public CenterSet(List<Center> allCenters, Collection<Center> toAdd)
	{
		this(allCenters);
		addAll(toAdd);
	}

	@Override
	public int size()
	{
		return bitSet.cardinality();
	}

	@Override
	public boolean isEmpty()
	{
		return bitSet.isEmpty();
	}

	@Override
	public boolean contains(Object o)
	{
		return bitSet.get(((Center)o).index);
	}

	@Override
	public Iterator<Center> iterator()
	{
		return new CenterIterator();
	}
	
	private class CenterIterator implements Iterator<Center>
	{
		int i;
		
		public CenterIterator()
		{
			findNext();
		}
		
		@Override
		public boolean hasNext()
		{
			return i < bitSet.size();
		}

		@Override
		public Center next()
		{
			Center result = centers.get(i);
			i++;
			findNext();
			return result;
		}
		
		private void findNext()
		{
			for (;i < bitSet.size(); i++)
			{
				if (bitSet.get(i))
					break;
			}			
		}
	}

	@Override
	public Object[] toArray()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T[] toArray(T[] a)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean add(Center e)
	{
		boolean result = !bitSet.get(e.index);
		bitSet.set(e.index);
		return result;
	}

	@Override
	public boolean remove(Object o)
	{
		boolean result = bitSet.get(((Center)o).index);
		bitSet.clear(((Center)o).index);
		return result;
	}

	@Override
	public boolean containsAll(Collection<?> c)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection<? extends Center> c)
	{
		boolean changed = false;
		for (Center center : c)
		{
			if (!bitSet.get(center.index))
				changed = true;
			bitSet.set(center.index);
		}
		return changed;
	}

	@Override
	public boolean retainAll(Collection<?> c)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c)
	{
		boolean changed = false;
		for (Object item : c)
		{
			changed |= remove(item);
		}
		return changed;
	}

	@Override
	public void clear()
	{
		bitSet.clear();
	}

}
