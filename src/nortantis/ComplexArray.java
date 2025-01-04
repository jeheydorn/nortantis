package nortantis;

public class ComplexArray
{
	private final float[] array;
	private final int width;
	private final int height;

	/**
	 * Creates a 2D array of complex numbers
	 */
	public ComplexArray(int width, int height)
	{
		this.width = width;
		this.height = height;
		array = new float[height * width * 2];
	}

	/**
	 * Does complex multiplication of this by other and stores the result into this.
	 */
	public void multiplyInPlace(ComplexArray other)
	{
		assert other.height == height;
		assert other.width == width;
		float[] otherArray = other.getArrayJTransformsFormat();

		for (int r = 0; r < height; r++)
			for (int c = 0; c < width; c++)
			{
				int index = (r * width + c * 2);
				float dataR = array[index];
				float dataI = array[index + 1];
				float otherR = otherArray[index];
				float otherI = otherArray[index + 1];

				float real = dataR * otherR - dataI * otherI;
				array[index] = real;
				float imaginary = dataI * otherR + dataR * otherI;
				array[index + 1] = imaginary;
			}
	}
	
	public void moveRealToLeftSide()
	{
		int rows = height;
		int cols = width;
		for (int r = 0; r < rows; r++)
		{
			for (int c = 0; c < cols / 2; c++)
			{
				array[r * cols + c] = array[r * cols + c * 2];
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
				int index1 = (r * cols + c);
				int index2;
				if (c < halfCols)
				{
					index2 = ((r + halfRows) * cols + (c + halfCols));
				}
				else
				{
					index2 = ((r + halfRows) * cols + (c - halfCols));
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
		array[y * width + x] = value;
	}

	public void setReal(int x, int y, float value)
	{
		array[(y * width) + (x * 2)] = value;
	}

	public float getReal(int x, int y)
	{
		return array[(y * width) + (x * 2)];
	}

	public float getImaginary(int x, int y)
	{
		return array[(y * width) + ((x * 2) + 1)];
	}

	public void setImaginary(int x, int y, float value)
	{
		array[(y * width) + ((x * 2) + 1)] = value;
	}

	public float[] getArrayJTransformsFormat()
	{
		return array;
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
