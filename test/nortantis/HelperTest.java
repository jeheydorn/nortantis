package nortantis;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

import nortantis.util.Helper;

public class HelperTest
{
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

	@Test
	public void testMaxItemWithMultipleElements()
	{
		List<Integer> input = Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6);
		assertEquals(Integer.valueOf(9), Helper.maxItem(input));
	}

	@Test
	public void testMaxItemWithSingleElement()
	{
		List<Integer> input = Arrays.asList(42);
		assertEquals(Integer.valueOf(42), Helper.maxItem(input));
	}

	@Test
	public void testMaxItemWithEmptyList()
	{
		List<Integer> input = Collections.emptyList();
		assertNull(Helper.maxItem(input));
	}

	@Test
	public void testMaxItemWithStrings()
	{
		List<String> input = Arrays.asList("apple", "zebra", "banana");
		assertEquals("zebra", Helper.maxItem(input));
	}

	@Test
	public void testMaxItemWithNegativeNumbers()
	{
		List<Integer> input = Arrays.asList(-5, -1, -10, -3);
		assertEquals(Integer.valueOf(-1), Helper.maxItem(input));
	}

	/**
	 * Sub-maps derive each redistributed center's random seed from a base seed plus the center index, then use the first nextDouble() of
	 * that seed's Random to decide whether to add one more tree. Without mixing, consecutive seeds give nearly identical first values, so
	 * that decision stays constant across long runs of center indices. Center indices are sorted by y, so those runs are horizontal strips
	 * of the map, and the result is visible tree banding.
	 */
	@Test
	public void testMixSeedDecorrelatesConsecutiveSeeds()
	{
		final int seedCount = 20000;
		final double threshold = 0.5;
		long baseSeed = 804888644L;

		int transitions = 0;
		boolean previous = new Random(Helper.mixSeed(baseSeed)).nextDouble() < threshold;
		for (int i = 1; i < seedCount; i++)
		{
			boolean current = new Random(Helper.mixSeed(baseSeed + i)).nextDouble() < threshold;
			if (current != previous)
			{
				transitions++;
				previous = current;
			}
		}

		// Independent seeds flip sides of the threshold about half the time, giving close to seedCount / 2 transitions. Unmixed consecutive
		// seeds give around 25, so the bound is loose enough to be robust while still failing by two orders of magnitude if the mixing is
		// removed.
		assertTrue(transitions > seedCount / 4, "Expected consecutive mixed seeds to behave independently, but got only " + transitions
				+ " threshold crossings across " + seedCount + " seeds.");
	}

	@Test
	public void testMixSeedIsDeterministic()
	{
		assertEquals(Helper.mixSeed(12345L), Helper.mixSeed(12345L));
		assertNotEquals(Helper.mixSeed(12345L), Helper.mixSeed(12346L));
	}
}
