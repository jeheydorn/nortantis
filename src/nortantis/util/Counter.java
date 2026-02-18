package nortantis.util;

import java.util.Random;

public interface Counter<T>
{

	void incrementCount(T item);

	void addCount(T item, int count);

	double getCount(T item);

	T sample(Random r);

	T argmax();

	boolean isEmpty();

}