package nortantis.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A map from keys to lists of values.
 * @author jeheydorn
 *
 * @param <K>
 * @param <V>
 */
public class ListMap <K, V> implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private Map<K, List<V>> map;
	
	public ListMap()
	{
		map = new TreeMap<K, List<V>>();
	}

	public ListMap(Comparator<K> comparator)
	{
		map = new TreeMap<K, List<V>>(comparator);
	}
	
	public void add(K key, V value)
	{
		List<V> valueList = map.get(key);
		if (valueList == null)
		{
			valueList = new ArrayList<>();
			map.put(key, valueList);
		}
		valueList.add(value);
	}
	
	public void addAll(K key, Collection<V> values)
	{
		List<V> valueList = map.get(key);
		if (valueList == null)
		{
			valueList = new ArrayList<>();
			map.put(key, valueList);
		}
		valueList.addAll(values);
	}
	
	public void put(K key, List<V> values)
	{
		map.put(key, values);
	}
	
	public List<V> get(K key)
	{
		return map.get(key);
	}
	
	public Set<Map.Entry<K, List<V>>> entrySet()
	{
		return map.entrySet();
	}
	
	public Set<K> keySet()
	{
		return map.keySet();
	}
	
	public boolean isEmpty()
	{
		return map.keySet().isEmpty();
	}
	
	public int size()
	{
		return map.keySet().size();
	}
	
	public boolean containsKey(K key)
	{
		return map.containsKey(key);
	}
	
	public Collection<List<V>> values()
	{
		return map.values();
	}
	
	@Override
	public String toString()
	{
		return map.toString();
	}
	
}
