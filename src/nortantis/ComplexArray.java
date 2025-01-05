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
	private float[][] array;
	private final int width;
	private final int height;

	/**
	 * Creates a 2D array of complex numbers
	 */
	public ComplexArray(int width, int height)
	{
		this.width = width;
		this.height = height;
		array = new float[height][width * 2];
	}

	/**
	 * Does complex multiplication of this by other and stores the result into this.
	 */
	public void multiplyInPlace(ComplexArray other)
	{
		// TODO use only 1D array

		assert other.getArrayJTransformsFormat().length == array.length;
		assert other.getArrayJTransformsFormat()[0].length == array[0].length;

		float[] otherArray = Helper.array2DTo1D(other.getArrayJTransformsFormat());
		float[] array1D = Helper.array2DTo1D(array);

		for (int r = 0; r < height; r++)
			for (int c = 0; c < width; c++)
			{
				int index = ((r * width * 2) + c * 2);
				float dataR = array1D[index];
				float dataI = array1D[index + 1];
				float otherR = otherArray[index];
				float otherI = otherArray[index + 1];

				float real = dataR * otherR - dataI * otherI;
				array1D[index] = real;
				float imaginary = dataI * otherR + dataR * otherI;
				array1D[index + 1] = imaginary;
			}
		Helper.copyArray1DTo2D(array, array1D);
	}
	
	public void moveRealToLeftSide()
	{
		// TODO use only 1D array
		float[] array1D = Helper.array2DTo1D(array);
		for (int r = 0; r < height; r++)
		{
			for (int c = 0; c < width; c++)
			{
				array1D[(r * width * 2) + c] = array1D[(r * width * 2) + c * 2];
			}
		}
		Helper.copyArray1DTo2D(array, array1D);
	}

	public void swapQuadrantsOfLeftSideInPlace()
	{
		// TODO use only 1D array
		float[] array1D = Helper.array2DTo1D(array);
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
					index2 = ((r + halfRows) * cols * 2 + (c + halfCols));
				}
				else
				{
					index2 = ((r + halfRows) * cols * 2 + (c - halfCols));
				}
				float temp = array1D[index2];
				array1D[index2] = array1D[index1];
				array1D[index1] = temp;
			}
		}
		Helper.copyArray1DTo2D(array, array1D);
	}

	/**
	 * When the internal array is being prepared for a real forward FFT by JTransforms, JTransforms expects real inputs to all be on the
	 * left. This uses that format.
	 */
	public void setRealInput(int x, int y, float value)
	{
		array[y][x] = value;
	}

	public void setReal(int x, int y, float value)
	{
		array[y][x * 2] = value;
	}

	public float getReal(int x, int y)
	{
		return array[y][x * 2];
	}

	public float getImaginary(int x, int y)
	{
		return array[y][(x * 2) + 1];
	}

	public void setImaginary(int x, int y, float value)
	{
		array[y][(x * 2) + 1] = value;
	}

	public float[][] getArrayJTransformsFormat()
	{
		return array;
	}

	public int getWidth()
	{
		if (array.length == 0)
		{
			return 0;
		}
		return array[0].length / 2;
	}

	public int getHeight()
	{
		return array.length;
	}

}
