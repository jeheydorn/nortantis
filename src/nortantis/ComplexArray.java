package nortantis;

import org.junit.Assert;

import nortantis.util.Helper;

/**
 * Stores a 2D array of complex numbers in JTransform's format.
 * 
 * *Input methods assume the internal array is being prepared for a real forward FFT by JTransforms. *Output methods assume the internal
 * array has been through a forward FFT by JTransforms.
 */
public class ComplexArray
{
	private float[] array;
	private final int width;
	private final int height;
	private final int rowSize;

	/**
	 * Creates a 2D array of complex numbers
	 */
	public ComplexArray(int width, int height)
	{
		this.width = width;
		this.height = height;
		rowSize = width * 2;
		array = new float[height * rowSize];
	}

	/**
	 * Does complex multiplication of this by other and stores the result into this.
	 */
	public void multiplyInPlace(ComplexArray other)
	{
		assert other.height == height;
		assert other.width == width;

		for (int r = 0; r < height; r++)
			for (int c = 0; c < width; c++)
			{
				int index = ((r * rowSize) + c * 2);
				float dataR = array[index];
				float dataI = array[index + 1];
				float otherR = other.array[index];
				float otherI = other.array[index + 1];

				float real = dataR * otherR - dataI * otherI;
				array[index] = real;
				float imaginary = dataI * otherR + dataR * otherI;
				array[index + 1] = imaginary;
			}
	}

	public void moveRealToLeftSide()
	{
		for (int r = 0; r < height; r++)
		{
			for (int c = 0; c < width; c++)
			{
				array[(r * rowSize) + c] = array[(r * rowSize) + c * 2];
			}
		}
	}

	public void swapQuadrantsOfLeftSideInPlace()
	{
		int rows = height;
		int cols = width;
		int halfRows = rows / 2;
		int halfCols = cols / 2;
		for (int r = 0; r < halfRows; r++)
		{
			for (int c = 0; c < cols; c++)
			{
				int index1 = (r * cols * 2 + c);
				int index2;
				if (c < halfCols)
				{
					index2 = ((r + halfRows) * rowSize + (c + halfCols));
				}
				else
				{
					index2 = ((r + halfRows) * rowSize + (c - halfCols));
				}
				float temp = array[index2];
				array[index2] = array[index1];
				array[index1] = temp;
			}
		}
	}

	/**
	 * When the internal array is being prepared for a real forward FFT by JTransforms, JTransforms expects real inputs to all be on the
	 * left. This uses that format.
	 */
	public void setRealInput(int x, int y, float value)
	{
		array[y * rowSize + x] = value;
		
		//array[y * width * 2 + x] = value;
		float[][] array2D = Helper.array1DTo2D(array, height, width * 2);
		System.out.println("x=" + x + ", y=" + y + ". 2D value: " + array2D[y][x] + ", 1D value: " + array[y * rowSize + x] + ". Index: " + y * rowSize + x);
		Assert.assertEquals(array2D[y][x], value, 0.0);

	}

	public void setReal(int x, int y, float value)
	{
		array[(y * rowSize) + (x * 2)] = value;
	}

	public float getReal(int x, int y)
	{
		return array[(y * rowSize) + (x * 2)];
	}

	public float getImaginary(int x, int y)
	{
		return array[(y * rowSize) + ((x * 2) + 1)];
	}

	public void setImaginary(int x, int y, float value)
	{
		array[(y * rowSize) + ((x * 2) + 1)] = value;
	}

	public float[][] getArrayJTransformsFormat()
	{
		// TODO change back to 1 dimensional array
		return Helper.array1DTo2D(array, height, rowSize);
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}
}
