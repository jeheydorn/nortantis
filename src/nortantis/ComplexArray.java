package nortantis;

import nortantis.platform.Image;
import nortantis.platform.ImageType;

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
		assert height == other.height;
		assert width == other.width;
		
		float[] otherArray = other.array;

		for (int r = 0; r < height; r++)
			for (int c = 0; c < width; c++)
			{
				int index = ((r * width * 2) + c * 2);
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
		for (int r = 0; r < height; r++)
		{
			for (int c = 0; c < width; c++)
			{
				array[(r * width * 2) + c] = array[(r * width * 2) + c * 2];
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
					index2 = ((r + halfRows) * cols * 2 + (c + halfCols));
				}
				else
				{
					index2 = ((r + halfRows) * cols * 2 + (c - halfCols));
				}
				float temp = array[index2];
				array[index2] = array[index1];
				array[index1] = temp;
			}
		}
	}
	
	/*
	 * Scales values in the given array such that the minimum is targetMin, and the maximum is targetMax.
	 */
	public void setContrast(float targetMin, float targetMax)
	{
		setContrast(targetMin, targetMax, 0, height, 0, rowSize);
	}
	
	public void setContrast(float targetMin, float targetMax, int rowStart, int rows, int colStart, int cols)
	{
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				float value = array[r * rowSize + c];
				if (value < min)
					min = value;
				if (value > max)
					max = value;
			}
		}

		float range = max - min;
		float targetRange = targetMax - targetMin;

		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				float value = array[r * rowSize + c];
				array[r * rowSize + c] = (((value - min) / (range))) * (targetRange) + targetMin;
			}
		}
	}
	
	public void scale(float scale, int rowStart, int rows, int colStart, int cols)
	{
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				// Make sure the value is above 0. In theory this shouldn't
				// happen if the kernel is positive, but very small
				// values below zero can happen I believe due to rounding error.
				float value = Math.max(0f, array[r * rowSize + c] * scale);
				if (value < 0f)
				{
					value = 0f;
				}
				else if (value > 1f)
				{
					value = 1f;
				}

				array[r * rowSize + c] = value;
			}
		}
	}

	public Image toImage(int rowStart, int rows, int colStart, int cols, ImageType imageType)
	{
		Image image = Image.create(cols, rows, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(imageType);
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				int value = Math.min(maxPixelValue, (int) (array[r * rowSize + c] * maxPixelValue));
				image.setGrayLevel(c - colStart, r - rowStart, value);
			}
		}
		return image;
	}
	
	private int getIndex(int y, int x)
	{
		return y * rowSize + x;
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
		array[getIndex(y, x * 2)] = value;
	}

	public float getReal(int x, int y)
	{
		return array[getIndex(y, x * 2)];
	}

	public float getImaginary(int x, int y)
	{
		return array[getIndex(y, (x * 2) + 1)];
	}

	public void setImaginary(int x, int y, float value)
	{
		array[getIndex(y, (x * 2) + 1)] = value;
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
