package nortantis.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import nortantis.ImageCache;
import nortantis.util.Tuple2;

public class ImageCacheTest
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
	}

	@Test
	public void testParseBaseNameAndWidth_withWidth()
	{
		String fileName = "large castle w2.png";
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(fileName);
		assertEquals("large castle", result.getFirst());
		assertEquals(2, (int) result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withWidthAndUnderscore()
	{
		String fileName = "large_castle_width=10.png";
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(fileName);
		assertEquals("large_castle", result.getFirst());
		assertEquals(10, (int) result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withoutWidth()
	{
		String fileName = "large castle.png";
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(fileName);
		assertEquals("large castle", result.getFirst());
		assertNull(result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withDifferentExtension()
	{
		String fileName = "large castle w5.jpg";
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(fileName);
		assertEquals("large castle", result.getFirst());
		assertEquals(5, (int) result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withNoExtension()
	{
		String fileName = "large castle w3";
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(fileName);
		assertEquals("large castle", result.getFirst());
		assertEquals(3, (int) result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withDoubleSpaces()
	{
		String fileName = "large castle  w100.png";
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(fileName);
		assertEquals("large castle", result.getFirst());
		assertEquals(100, (int) result.getSecond());
	}
	
	@Test
	public void testParseBaseNameAndWidth_withTrailingSpace()
	{
		String fileName = "large castle w100 .png";
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(fileName);
		assertEquals("large castle", result.getFirst());
		assertEquals(100, (int) result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withNullInput()
	{
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth(null);
		assertNull(result.getFirst());
		assertNull(result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withEmptyString()
	{
		Tuple2<String, Integer> result = ImageCache.parseBaseNameAndWidth("");
		assertNull(result.getFirst());
		assertNull(result.getSecond());
	}
}
