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
	 *
	 * This never blocks, but it is not atomic: simultaneous calls for the same key can each run createFun, and the last one to finish
	 * replaces the others' values. That trade is fine when createFun produces a self-contained value, such as a scaled image, because the
	 * only cost is repeating some work. It is not safe when the value is a container that callers then add to, since entries added to a
	 * replaced container are lost. Use {@link java.util.concurrent.ConcurrentHashMap#computeIfAbsent} for those, and whenever createFun is
	 * expensive enough that running it more than once matters.
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

}
