package nortantis.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import nortantis.ImageCache;
import nortantis.ImageCache.WhichDimension;
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
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large castle", result.getFirst());
		assertEquals(2, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withHeight()
	{
		String fileName = "large castle h2.png";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Height);
		assertEquals("large castle", result.getFirst());
		assertEquals(2, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withHeightSearchingByWidth()
	{
		String fileName = "large castle h2.png";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large castle h2", result.getFirst());
		assertNull(result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withWidthAndUnderscore()
	{
		String fileName = "large_castle_width=10.png";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large_castle", result.getFirst());
		assertEquals(10, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withHeightAndUnderscore()
	{
		String fileName = "large_castle_height=10.png";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Height);
		assertEquals("large_castle", result.getFirst());
		assertEquals(10, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withoutWidth()
	{
		String fileName = "large castle.png";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large castle", result.getFirst());
		assertNull(result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withDifferentExtension()
	{
		String fileName = "large castle w5.jpg";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large castle", result.getFirst());
		assertEquals(5, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withNoExtension()
	{
		String fileName = "large castle w3";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large castle", result.getFirst());
		assertEquals(3, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withDoubleSpaces()
	{
		String fileName = "large castle  w100.png";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large castle", result.getFirst());
		assertEquals(100, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withTrailingSpace()
	{
		String fileName = "large castle w100 .png";
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(fileName, WhichDimension.Width);
		assertEquals("large castle", result.getFirst());
		assertEquals(100, (double) result.getSecond(), 0);
	}

	@Test
	public void testParseBaseNameAndWidth_withNullInput()
	{
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize(null, WhichDimension.Width);
		assertNull(result.getFirst());
		assertNull(result.getSecond());
	}

	@Test
	public void testParseBaseNameAndWidth_withEmptyString()
	{
		Tuple2<String, Double> result = ImageCache.parseBaseNameAndSize("", WhichDimension.Height);
		assertNull(result.getFirst());
		assertNull(result.getSecond());
	}
}
