package nortantis.util;

import java.util.HashMap;
import java.util.function.Supplier;

/**
 * A concurrent hash map with a factory method for creating new values.
 * If getOrCreate(key) is called and the key is not mapped to a value
 * in the map, then a new mapping is added with that key mapped
 * to a new instance of the value's class.
 *
 * @param <K>
 * @param <V>
 */
@SuppressWarnings("serial")
public class HashMapF <K, V> extends HashMap<K, V>
{
	private Supplier<V> defaultCreateFunction;
	
	public HashMapF()
	{
		super();
	}
	
	public HashMapF(Supplier<V> defaultCreateFunction)
	{
		super();
		this.defaultCreateFunction = defaultCreateFunction;
	}
	
	/**
	 * If the given key is mapped to a value in this map, then that
	 * value is returned. If not, then create() is called to make a
	 * new value, then that value is mapped to key and returned.
	 * @param key
	 * @return
	 */
	public V getOrCreate(K key, Supplier<V> createFun)
	{
		V value = get(key);
		if (value == null)
		{
			if (createFun == null && defaultCreateFunction == null)
			{
				throw new IllegalArgumentException("No create function given to getOrCreate.");
			}
			
			value = createFun.get();
			put(key, value);
		}
		return value;
		
	}	
	
	public V getOrCreate(K key)
	{
		return getOrCreate(key, defaultCreateFunction);
	}

}
