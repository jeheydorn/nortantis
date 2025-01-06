package nortantis;

import static org.junit.Assert.assertArrayEquals;

import java.util.Objects;

import org.junit.Assert;

import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.util.Helper;

/**
 * Stores a 2D array of complex numbers in JTransform's format.
 * 
 * *Input methods assume the internal array is being prepared for a real forward FFT by JTransforms. *Output methods assume the internal
 * array has been through a forward FFT by JTransforms.
 */
public class ComplexArray
{
	public float[][] array; // TODO set back to private
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
		array = new float[height][width * 2];
	}

	/**
	 * Does complex multiplication of this by other and stores the result into this.
	 */
	public void multiplyInPlace(ComplexArray other)
	{
		// TODO use only 1D array

		assert height == other.height;
		assert width == other.width;
		
		float[] otherArray = Helper.array2DTo1D(other.array);
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
	
	/*
	 * Scales values in the given array such that the minimum is targetMin, and the maximum is targetMax.
	 */
	public void setContrast(float targetMin, float targetMax)
	{
		setContrast(targetMin, targetMax, 0, height, 0, rowSize);
	}
	
	public void setContrast(float targetMin, float targetMax, int rowStart, int rows, int colStart, int cols)
	{
		float[] array1D = Helper.array2DTo1D(array);
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				float value = array1D[r * rowSize + c];
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
				float value = array1D[r * rowSize + c];
				array1D[r * rowSize + c] = (((value - min) / (range))) * (targetRange) + targetMin;
			}
		}
		Helper.copyArray1DTo2D(array, array1D);
	}
	
	public void scale(float scale, int rowStart, int rows, int colStart, int cols)
	{
		float[] array1D = Helper.array2DTo1D(array);
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				// Make sure the value is above 0. In theory this shouldn't
				// happen if the kernel is positive, but very small
				// values below zero can happen I believe due to rounding error.
				float value = Math.max(0f, array1D[r * rowSize + c] * scale);
				if (value < 0f)
				{
					value = 0f;
				}
				else if (value > 1f)
				{
					value = 1f;
				}

				array1D[r * rowSize + c] = value;
			}
		}
		Helper.copyArray1DTo2D(array, array1D);
	}

	public Image toImage(int rowStart, int rows, int colStart, int cols, ImageType imageType)
	{
		float[] array1D = Helper.array2DTo1D(array);
		Image image = Image.create(cols, rows, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(imageType);
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				int value = Math.min(maxPixelValue, (int) (array1D[r * rowSize + c] * maxPixelValue));
				image.setGrayLevel(c - colStart, r - rowStart, value);
			}
		}
		return image;
	}
	

	/**
	 * When the internal array is being prepared for a real forward FFT by JTransforms, JTransforms expects real inputs to all be on the
	 * left. This uses that format.
	 */
	public void setRealInput(int x, int y, float value)
	{
		array[y][x] = value;
//		System.out.println("x=" + x + ", y=" + y);
//		Assert.assertEquals(value, Helper.array2DTo1D(array)[(y * rowSize) + x], 0.0);
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
