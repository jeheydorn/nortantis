package nortantis.util;

import static java.lang.System.out;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
	 * Combines 2 lists of the same length by applying the given function to each pair of items in the 2 lists.
	 */
	public static <I, R> List<R> combineLists(List<I> l1, List<I> l2, Function2<I, R> fun)
	{
		if (l1.size() != l2.size())
			throw new IllegalArgumentException("Lists must be the same size. List 1 size: " + l1.size() + ", list 2 size: " + l2.size());
		List<R> result = new ArrayList<R>();
		for (int i = 0; i < l1.size(); i++)
			result.add(fun.apply(l1.get(i), l2.get(i)));
		return result;
	}

	/**
	 * Applies the given function to each item in the given list and returns only those for which the function returned true.
	 */
	public static <T> List<T> filter(List<T> list, Function<T, Boolean> fun)
	{
		List<T> result = new ArrayList<>();
		for (T item : list)
			if (fun.apply(item))
				result.add(item);
		return result;
	}

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

	public static <K, V extends Comparable<V>> V min(Map<K, V> map)
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

	public static <T> T maxItem(List<T> list, Comparator<T> comparator)
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

	private static DecimalFormat decimalFormat = new DecimalFormat("#.#####");

	public static String formatFloat(float d)
	{
		return decimalFormat.format(d);
	}

	public static void printMultiLine(Collection<?> c)
	{
		for (Object o : c)
		{
			out.println(o);
		}
	}

	public static String toStringWithSeparator(Collection<?> collection, String separator)
	{
		if (collection.isEmpty())
			return "";

		StringBuilder b = new StringBuilder();
		Iterator<?> it = collection.iterator();
		while (true)
		{
			b.append(it.next());
			if (it.hasNext())
			{
				b.append(separator);
			}
			else
			{
				break;
			}
		}
		return b.toString();
	}

	public static void writeToFile(String fileName, String contents)
	{
		try
		{
			File file = new File(fileName);

			if (!file.exists())
			{
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			bw.write(contents);

			bw.close();
		}
		catch (IOException ex)
		{
			throw new RuntimeException("Helper.writeToFile caught error: " + ex.getMessage(), ex);
		}
	}

	public static void createFolder(String folderName)
	{
		File folder = new File(folderName);
		folder.mkdir();
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

	/**
	 * WARNING: This isn't tested.
	 */
	public static <T extends Serializable> boolean areEqual(T object1, T object2)
	{
		byte[] array1 = serializableToByteArray(object1);
		byte[] array2 = serializableToByteArray(object1);
		return Arrays.equals(array1, array2); // I think this line doesn't work.
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

	public static <T> List<T> iteratorToList(Iterator<T> iter)
	{
		ArrayList<T> result = new ArrayList<>();
		while (iter.hasNext())
			result.add(iter.next());
		return result;
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
	
	public static void copyArray1DTo2D(float[][] array2D, float[] array1D)
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
				array2D[r][c] = array1D[r * array2D[0].length + c];
			}
		}
	}

}
