package nortantis.util;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.System.out;

public class Helper
{
	public static <I, R> List<R> map(List<I> items, Function<I, R> fun)
	{
		List<R> result = new ArrayList<R>();
		for (I item : items)
			result.add(fun.apply(item));
		return result;
	}

	/**
	 * Applies the given function to each item in the given list and returns only those for which the function returned true.
	 */
	public static <T> List<T> filter(List<T> list, Predicate<T> fun)
	{
		List<T> result = new ArrayList<>();
		for (T item : list)
			if (fun.test(item))
				result.add(item);
		return result;
	}

	@SuppressWarnings("unused")
	public static <K, V extends Comparable<V>> K argmin(Map<K, V> map)
	{
		Map.Entry<K, V> minEntry = null;

		for (Map.Entry<K, V> entry : map.entrySet())
		{
			if (minEntry == null || entry.getValue().compareTo(minEntry.getValue()) < 0)
			{
				minEntry = entry;
			}
		}
		return minEntry.getKey();
	}

	public static <K, V extends Comparable<V>> V minElement(Map<K, V> map)
	{
		Map.Entry<K, V> minEntry = null;

		for (Map.Entry<K, V> entry : map.entrySet())
		{
			if (minEntry == null || entry.getValue().compareTo(minEntry.getValue()) < 0)
			{
				minEntry = entry;
			}
		}
		return minEntry.getValue();
	}

	public static <K, V extends Comparable<V>> K argmax(Map<K, V> map)
	{
		Map.Entry<K, V> maxEntry = null;

		for (Map.Entry<K, V> entry : map.entrySet())
		{
			if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0)
			{
				maxEntry = entry;
			}
		}
		return maxEntry.getKey();
	}

	@SuppressWarnings("unused")
	public static <K, V extends Comparable<V>> V maxElement(Map<K, V> map)
	{
		Map.Entry<K, V> maxEntry = null;

		for (Map.Entry<K, V> entry : map.entrySet())
		{
			if (entry.getValue() == null)
			{
				continue;
			}

			if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0)
			{
				maxEntry = entry;
			}
		}
		return maxEntry == null ? null : maxEntry.getValue();
	}

	public static <K, V> K argmax(Map<K, V> map, Comparator<V> comparator)
	{
		Map.Entry<K, V> maxEntry = null;

		for (Map.Entry<K, V> entry : map.entrySet())
		{
			if (maxEntry == null || comparator.compare(entry.getValue(), maxEntry.getValue()) > 0)
			{
				maxEntry = entry;
			}
		}
		return maxEntry.getKey();
	}

	public static <K, V> V maxElement(Map<K, V> map, Comparator<V> comparator)
	{
		Map.Entry<K, V> maxEntry = null;

		for (Map.Entry<K, V> entry : map.entrySet())
		{
			if (maxEntry == null || comparator.compare(entry.getValue(), maxEntry.getValue()) > 0)
			{
				maxEntry = entry;
			}
		}
		return maxEntry.getValue();
	}

	public static <T> T minItem(Collection<T> list, Comparator<T> comparator)
	{
		T minItem = null;

		for (T item : list)
		{
			if (minItem == null || comparator.compare(item, minItem) < 0)
			{
				minItem = item;
			}
		}
		return minItem;
	}

	public static <T> T maxItem(Collection<T> list, Comparator<T> comparator)
	{
		T maxItem = null;

		for (T item : list)
		{
			if (maxItem == null || comparator.compare(item, maxItem) > 0)
			{
				maxItem = item;
			}
		}
		return maxItem;
	}

	public static <T extends Comparable<? super T>> T maxItem(Collection<T> list)
	{
		T maxItem = null;

		for (T item : list)
		{
			if (maxItem == null || maxItem.compareTo(item) < 0)
			{
				maxItem = item;
			}
		}
		return maxItem;
	}

	/**
	 * Creates a deep copy of an object using serialization.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Serializable> T deepCopy(T toCopy)
	{
		if (toCopy == null)
		{
			return null;
		}

		byte[] storedObjectArray = serializableToByteArray(toCopy);

		Object toReturn = null;
		try (ByteArrayInputStream istream = new ByteArrayInputStream(storedObjectArray))
		{
			ObjectInputStream p;
			p = new ObjectInputStream(new BufferedInputStream(istream));
			toReturn = p.readObject();
			p.close();
		}
		catch (IOException | ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
		return (T) toReturn;
	}

	private static <T extends Serializable> byte[] serializableToByteArray(T object)
	{
		ByteArrayOutputStream ostream = new ByteArrayOutputStream();
		byte[] storedObjectArray;
		{
			try (ObjectOutputStream p = new ObjectOutputStream(new BufferedOutputStream(ostream)))
			{
				p.writeObject(object);
				p.flush();
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
			storedObjectArray = ostream.toByteArray();
		}
		return storedObjectArray;
	}

	public static float[] array2DTo1D(float[][] input)
	{
		if (input == null)
		{
			return null;
		}
		if (input.length == 0)
		{
			return new float[0];
		}
		float[] result = new float[input.length * input[0].length];

		for (int r = 0; r < input.length; r++)
		{
			for (int c = 0; c < input[0].length; c++)
			{
				result[r * input[0].length + c] = input[r][c];
			}
		}
		return result;
	}

	public static float[][] array1DTo2D(float[] input, int rows, int cols)
	{
		if (input == null)
		{
			return null;
		}
		if (input.length != rows * cols)
		{
			throw new IllegalArgumentException("Invalid input array length");
		}
		float[][] result = new float[rows][cols];

		for (int r = 0; r < rows; r++)
		{
			for (int c = 0; c < cols; c++)
			{
				result[r][c] = input[r * cols + c];
			}
		}
		return result;
	}

	public static void copyArray2DTo1D(float[] array1D, float[][] array2D)
	{
		if (array2D == null)
		{
			return;
		}
		if (array2D.length == 0)
		{
			return;
		}

		if (array1D.length != array2D.length * array2D[0].length)
		{
			throw new IllegalArgumentException("Invalid input array2D length");
		}

		for (int r = 0; r < array2D.length; r++)
		{
			for (int c = 0; c < array2D[0].length; c++)
			{
				array1D[r * array2D[0].length + c] = array2D[r][c];
			}
		}
	}

	public static <T> Set<T> getElementsNotInIntersection(Set<T> set1, Set<T> set2)
	{
		Set<T> result = new HashSet<>(set1);
		// Union of both sets
		result.addAll(set2);

		Set<T> intersection = new HashSet<>(set1);
		// Intersection of both sets
		intersection.retainAll(set2);

		// Remove elements in the intersection
		result.removeAll(intersection);
		return result;
	}

	public static double linearCombo(double weight, double value1, double value2)
	{
		return (weight * value1) + ((1.0 - weight) * value2);
	}

	/**
	 * Returns the absolute value of the given integer, safe from overflow. Unlike Math.abs, this handles Integer.MIN_VALUE correctly by
	 * masking off the sign bit.
	 */
	public static int safeAbs(int value)
	{
		return Math.abs(value) & 0x7FFFFFFF;
	}

	/**
	 * Returns the absolute value of the given long, safe from overflow. Unlike Math.abs, this handles Long.MIN_VALUE correctly by masking
	 * off the sign bit.
	 */
	public static long safeAbs(long value)
	{
		return Math.abs(value) & 0x7FFFFFFFFFFFFFFFL;
	}

	public static int linearComboBase255(int weightFrom0To255, int value1From0To255, int value2From0To255)
	{
		return ((weightFrom0To255 * value1From0To255) + ((255 - weightFrom0To255) * value2From0To255)) / 255;
	}

	public static float clamp(float value, float min, float max)
	{
		return Math.min(Math.max(value, min), max);
	}

	/**
	 * Mixes the bits of the given value so that inputs that differ only slightly produce unrelated results. Use this when deriving a random
	 * seed from a base seed plus a sequential value such as a Center index.
	 * <p>
	 * {@link Random} is a linear congruential generator whose first few outputs vary only slightly between nearby seeds: seeds n and n + 1
	 * give first {@code nextDouble()} values that differ by about 0.0001. Code that consumes just the first value or two - such as deciding
	 * whether to place one more item - therefore makes the same decision across long runs of consecutive seeds, which shows up as visible
	 * banding wherever those sequential values correspond to position on the map.
	 * </p>
	 * This is the SplitMix64 finalizer.
	 */
	public static long mixSeed(long value)
	{
		long result = value + 0x9E3779B97F4A7C15L;
		result = (result ^ (result >>> 30)) * 0xBF58476D1CE4E5B9L;
		result = (result ^ (result >>> 27)) * 0x94D049BB133111EBL;
		return result ^ (result >>> 31);
	}
}
