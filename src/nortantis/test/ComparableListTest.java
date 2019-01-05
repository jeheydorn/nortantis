package nortantis.test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import nortantis.util.ComparableList;
import nortantis.util.Range;

public class ComparableListTest
{

	@Test
	public void test()
	{
		ComparableList<Character> list1 = new ComparableList<>(Arrays.asList('a', 'b', 'c'));
		ComparableList<Character> list2 = new ComparableList<>(Arrays.asList('a', 'b', 'c'));
		ComparableList<Character> list3 = new ComparableList<>(Arrays.asList('a', 'b', 'd'));
		ComparableList<Character> list4 = new ComparableList<>(Arrays.asList('a', 'b'));
		ComparableList<Character> list5 = new ComparableList<>(Arrays.asList('b', 'c'));
		
		Set<ComparableList<Character>> set = new TreeSet<>();
		set.add(list5);
		set.add(list1);
		set.add(list2);
		set.add(list3);
		set.add(list4);
		
		System.out.println(set);
		List<ComparableList<Character>> setList = new ArrayList<>(set);
		assertEquals(setList.get(0), list4);
		assertEquals(setList.get(1), list1);
		assertEquals(setList.get(2), list3);
		assertEquals(setList.get(3), list5);
	}

	@Test
	@SuppressWarnings("unused")
	public void testRandom()
	{
		List<Character> letters = Arrays.asList('a', 'b', 'c', 'd');
		Random r = new Random();
		Set<String> stringSet = new TreeSet<>();
		Set<ComparableList<Character>> compSet = new TreeSet<>();
		
		for ( int n : new Range(100))
		{
			ComparableList<Character> randLetters = new ComparableList<>();
			for (int i : new Range(r.nextInt(10)))
			{
				randLetters.add(letters.get(r.nextInt(letters.size())));
			}
			compSet.add(randLetters);
			
			// Convert the random characters into a string.
			String str = "";
			for (Character c : randLetters)
				str += c;
			stringSet.add(str);
		}
		
		assertEquals(stringSet.size(), compSet.size());
		
		Iterator<String> strIter = stringSet.iterator();
		for (ComparableList<Character> l : compSet)
		{
			String str = "";
			for (Character c : l)
				str += c;
			assertEquals(strIter.next(), str);
			
		}
			
			
	}
}
