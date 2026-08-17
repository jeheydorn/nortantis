package nortantis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ComplexArray}'s shifting of the left side, which convolution relies on to put its result where the image it came from was.
 */
public class ComplexArrayTest
{
	/**
	 * The shift has to be a circular rotation by half the height and half the width, rounded down. Swapping opposite quadrants gives the same
	 * answer when both dimensions are even but not when either is odd, and odd dimensions do occur, because the padded sizes convolution picks
	 * are only required to factor into 2s, 3s and 5s - 135 and 243 are both valid choices.
	 */
	@Test
	public void shiftOfLeftSideIsARotationByHalfTheSize()
	{
		int[] sizesToTest = { 1, 2, 3, 4, 5, 7, 8, 9, 15, 16 };
		for (int width : sizesToTest)
		{
			for (int height : sizesToTest)
			{
				ComplexArray data = new ComplexArray(width, height);
				for (int y = 0; y < height; y++)
				{
					for (int x = 0; x < width; x++)
					{
						data.setRealInput(x, y, y * width + x);
					}
				}

				data.swapQuadrantsOfLeftSideInPlace();

				float[][] shifted = data.getArrayJTransformsFormat();
				for (int y = 0; y < height; y++)
				{
					for (int x = 0; x < width; x++)
					{
						int sourceY = (y + height / 2) % height;
						int sourceX = (x + width / 2) % width;
						assertEquals(sourceY * width + sourceX, shifted[y][x], 0f,
								"At " + x + ", " + y + " of a " + width + " by " + height + " array");
					}
				}
			}
		}
	}
}
