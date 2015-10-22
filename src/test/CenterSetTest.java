package test;

import static org.junit.Assert.*;
import hoten.voronoi.Center;

import java.util.*;

import nortantis.CenterSet;

import org.junit.Test;

import util.Helper;
import util.Range;

public class CenterSetTest
{

	@Test
	public void testAddRemove()
	{
		CenterSet set = createTestData(0);
		assertEquals(0, set.size());
		
		// add
		Center c0 = new Center();
		c0.index = 0;
		set.add(c0);
		assertEquals(1, set.size());
		Center c1 = new Center();
		c1.index = 1;
		set.add(c1);
		assertEquals(2, set.size());
		
		// remove
		Center c2 = new Center();
		c2.index = 2;
		assertFalse(set.remove(c2));
		assertEquals(2, set.size());
		
		assertTrue(set.remove(c0));
		assertEquals(1, set.size());
		assertFalse(set.remove(c0));
		assertEquals(1, set.size());
		
		assertFalse(set.isEmpty());
		assertTrue(set.contains(c1));
		assertTrue(set.remove(c1));
		assertFalse(set.contains(c1));
		assertEquals(0, set.size());
		assertTrue(set.isEmpty());
	}
	
	@Test
	public void testConstructor()
	{
		assertEquals(10, createTestData(10).size());
		//assertEquals(0, createTestData(0).size());
		//assertEquals(1, createTestData(1).size());
		//assertEquals(100, createTestData(100).size());
	}
	
	@Test
	public void iteratorTest()
	{
		{
			CenterSet testData = createTestData(10);
			assertEquals(10, Helper.iteratorToList(testData.iterator()).size());
		}

		{
			CenterSet testData = createTestData(0);
			assertEquals(0, Helper.iteratorToList(testData.iterator()).size());
		}

		{
			CenterSet testData = createTestData(1);
			assertEquals(1, Helper.iteratorToList(testData.iterator()).size());
		}

		{
			CenterSet testData = createTestData(2);
			assertEquals(2, Helper.iteratorToList(testData.iterator()).size());
		}

		{
			CenterSet testData = createTestData(6);
			Center c0 = new Center();
			c0.index = 0;
			testData.remove(c0);
			assertEquals(5, Helper.iteratorToList(testData.iterator()).size());
			
			Center c5 = new Center();
			c5.index = 5;
			assertTrue(testData.remove(c5));
			assertEquals(4, Helper.iteratorToList(testData.iterator()).size());
			
			while (!testData.isEmpty())
			{
				assertTrue(testData.remove(testData.iterator().next()));
			}
			assertEquals(0, Helper.iteratorToList(testData.iterator()).size());
			assertFalse(testData.iterator().hasNext());
		}
}
	
	private CenterSet createTestData(int size)
	{
		List<Center> centers = new ArrayList<>();
		for (int i : new Range(size))
		{
			Center c = new Center();
			c.index = i;
			centers.add(c);
		}
		CenterSet set = new CenterSet(centers);
		set.addAll(centers);
		assertEquals(size, set.size());
		return set;
	}

}
