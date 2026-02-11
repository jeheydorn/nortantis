package nortantis.test;

import nortantis.ImageCache;
import nortantis.ImageCache.ParsedFilename;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ImageCacheTest
{
	@Test
	public void testParseWidth()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle w2.png");
		assertEquals("large castle", result.baseName());
		assertEquals(2, result.width(), 0);
		assertNull(result.height());
		assertNull(result.alpha());
	}

	@Test
	public void testParseHeight()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle h2.png");
		assertEquals("large castle", result.baseName());
		assertNull(result.width());
		assertEquals(2, result.height(), 0);
		assertNull(result.alpha());
	}

	@Test
	public void testParseHeightDoesNotMatchWidth()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle h2.png");
		assertNull(result.width());
		assertEquals(2, result.height(), 0);
	}

	@Test
	public void testParseWidthWithUnderscore()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large_castle_width=10.png");
		assertEquals("large_castle", result.baseName());
		assertEquals(10, result.width(), 0);
	}

	@Test
	public void testParseHeightWithUnderscore()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large_castle_height=10.png");
		assertEquals("large_castle", result.baseName());
		assertEquals(10, result.height(), 0);
	}

	@Test
	public void testParseWithoutEncodedParams()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle.png");
		assertEquals("large castle", result.baseName());
		assertNull(result.width());
		assertNull(result.height());
		assertNull(result.alpha());
	}

	@Test
	public void testParseWithDifferentExtension()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle w5.jpg");
		assertEquals("large castle", result.baseName());
		assertEquals(5, result.width(), 0);
	}

	@Test
	public void testParseWithNoExtension()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle w3");
		assertEquals("large castle", result.baseName());
		assertEquals(3, result.width(), 0);
	}

	@Test
	public void testParseWithDoubleSpaces()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle  w100.png");
		assertEquals("large castle", result.baseName());
		assertEquals(100, result.width(), 0);
	}

	@Test
	public void testParseWithTrailingSpace()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("large castle w100 .png");
		assertEquals("large castle", result.baseName());
		assertEquals(100, result.width(), 0);
	}

	@Test
	public void testParseWithNullInput()
	{
		ParsedFilename result = ImageCache.parseFilenameParams(null);
		assertNull(result.baseName());
		assertNull(result.width());
		assertNull(result.height());
		assertNull(result.alpha());
	}

	@Test
	public void testParseWithEmptyString()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("");
		assertNull(result.baseName());
		assertNull(result.width());
		assertNull(result.height());
		assertNull(result.alpha());
	}

	@Test
	public void testParseAlpha()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("castle a128.png");
		assertEquals("castle", result.baseName());
		assertNull(result.width());
		assertEquals(128, result.alpha());
	}

	@Test
	public void testParseAlphaWithLongForm()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("castle_alpha=200.png");
		assertEquals("castle", result.baseName());
		assertEquals(200, result.alpha());
	}

	@Test
	public void testParseWidthAndAlpha()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("castle w20 a50.png");
		assertEquals("castle", result.baseName());
		assertEquals(20, result.width(), 0);
		assertEquals(50, result.alpha());
	}

	@Test
	public void testParseAlphaBeforeWidth()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("castle a50 w20.png");
		assertEquals("castle", result.baseName());
		assertEquals(20, result.width(), 0);
		assertEquals(50, result.alpha());
	}

	@Test
	public void testParseHeightAndAlpha()
	{
		ParsedFilename result = ImageCache.parseFilenameParams("tower h15 a128.png");
		assertEquals("tower", result.baseName());
		assertEquals(15, result.height(), 0);
		assertEquals(128, result.alpha());
	}

	@Test
	public void testParseWidthAndHeightThrows()
	{
		assertThrows(RuntimeException.class, () -> ImageCache.parseFilenameParams("castle w20 h15.png"));
	}

	@Test
	public void testParseAlphaGreaterThan255Throws()
	{
		assertThrows(RuntimeException.class, () -> ImageCache.parseFilenameParams("castle a256.png"));
	}

	@Test
	public void testParseWidthZeroThrows()
	{
		assertThrows(RuntimeException.class, () -> ImageCache.parseFilenameParams("castle w0.png"));
	}

	@Test
	public void testParseHeightZeroThrows()
	{
		assertThrows(RuntimeException.class, () -> ImageCache.parseFilenameParams("castle h0.png"));
	}
}
