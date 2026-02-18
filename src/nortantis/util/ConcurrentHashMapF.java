package nortantis.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A concurrent hash map with a factory method for creating new values. If getOrCreate(key) is called and the key is not mapped to a value
 * in the map, then a new mapping is added with that key mapped to a new instance of the value's class.
 *
 * @param <K>
 * @param <V>
 */
@SuppressWarnings("serial")
public class ConcurrentHashMapF<K, V> extends ConcurrentHashMap<K, V>
{

	public ConcurrentHashMapF()
	{
		super();
	}

	public ConcurrentHashMapF(ConcurrentHashMapF<K, V> other)
	{
		super(other);
	}

	/**
	 * If the given key is mapped to a value in this map, then that value is returned. If not, then create() is called to make a new value,
	 * then that value is mapped to key and returned.
	 */
	public V getOrCreate(K key, Supplier<V> createFun)
	{
		V value = get(key);
		if (value == null)
		{
			value = createFun.get();
			put(key, value);
		}
		return value;
	}

	/**
	 * If the given key is mapped to a value in this map, then that value is returned. If not, then create() is called to make a new value,
	 * then that value is mapped to key and returned.
	 * 
	 * The key is locked to ensure to insure multiple calls to this function for the same key process sequentially, rather than call
	 * createFun multiple times and duplicate work.
	 */
	public V getOrCreateWithLock(K key, Supplier<V> createFun)
	{
		// Lock on the key while we're working so multiple simultaneous calls don't repeat work
		synchronized (key)
		{
			V value = get(key);
			if (value == null)
			{
				value = createFun.get();
				put(key, value);
			}
			return value;
		}
	}

}
