package nortantis.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HelperTest
{

	@BeforeAll
	public static void setUpBeforeClass() throws Exception
	{
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception
	{
	}

	@Test
	public void testNullInput()
	{
		assertNull(Helper.array2DTo1D(null));
	}

	@Test
	public void testEmptyArray()
	{
		float[][] input = new float[0][0];
		float[] expected = new float[0];
		assertArrayEquals(expected, Helper.array2DTo1D(input), 0f);
	}

	@Test
	public void testSingleElementArray()
	{
		float[][] input = { { 1.0f } };
		float[] expected = { 1.0f };
		assertArrayEquals(expected, Helper.array2DTo1D(input), 0f);
	}

	@Test
	public void testRegularArray()
	{
		float[][] input = { { 1.0f, 2.0f }, { 3.0f, 4.0f } };
		float[] expected = { 1.0f, 2.0f, 3.0f, 4.0f };
		assertArrayEquals(expected, Helper.array2DTo1D(input), 0f);
	}

	@Test
	public void testRegularArrayLarger()
	{
		float[][] input = { { 1.0f, 2.0f, 3.0f }, { 4.0f, 5.0f, 6f }, { 7f, 8f, 9f } };
		float[] expected = { 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6f, 7f, 8f, 9f };
		assertArrayEquals(expected, Helper.array2DTo1D(input), 0f);
	}

	@Test
	public void testArray1DTo2D()
	{
		float[] input = { 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6f };
		float[][] expected = { { 1.0f, 2.0f, 3.0f }, { 4.0f, 5.0f, 6f } };
		assertArrayEquals(expected, Helper.array1DTo2D(input, 2, 3));
	}

	@Test
	public void testArrayConversion()
	{
		float[][] expected = { { 1.0f, 2.0f, 3.0f }, { 4.0f, 5.0f, 6f }, { 7f, 8f, 9f } };
		float[][] actual = Helper.array1DTo2D(Helper.array2DTo1D(expected), expected.length, expected[0].length);
		assertArrayEquals(expected, actual);
	}

	@Test
	public void testCopyArray2DTo1D()
	{
		float[][] input = { { 1.0f, 2.0f, 3.0f }, { 4.0f, 5.0f, 6f }, { 7f, 8f, 9f } };
		float[] expected = { 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6f, 7f, 8f, 9f };
		float[] actual = new float[input.length * input[0].length];
		Helper.copyArray2DTo1D(actual, input);
		assertArrayEquals(expected, actual, 0f);
	}
}
