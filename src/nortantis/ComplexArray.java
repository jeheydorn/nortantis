package nortantis;

import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PixelWriter;
import nortantis.util.Tuple2;

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
		assert height == other.height;
		assert width == other.width;

		float[][] otherArray = other.array;

		for (int r = 0; r < height; r++)
			for (int c = 0; c < width; c++)
			{
				int colR = c * 2;
				float dataR = array[r][colR];
				float dataI = array[r][colR + 1];
				float otherR = otherArray[r][colR];
				float otherI = otherArray[r][colR + 1];

				float real = dataR * otherR - dataI * otherI;
				array[r][colR] = real;
				float imaginary = dataI * otherR + dataR * otherI;
				array[r][colR + 1] = imaginary;
			}
	}

	public void moveRealToLeftSide()
	{
		for (int r = 0; r < height; r++)
		{
			for (int c = 0; c < width; c++)
			{
				array[r][c] = array[r][c * 2];
			}
		}
	}

	/**
	 * Moves the origin of the left side from its corner to its middle, which puts a convolution's result where the image it came from was.
	 *
	 * This is a circular rotation by half the height and half the width, rounded down. Rotating is what makes odd widths and heights come out
	 * right: swapping opposite quadrants only lines up when both dimensions are even, and for odd ones it leaves the last row untouched and
	 * writes the middle column twice. For even dimensions the rotation and the swap are the same thing.
	 */
	public void swapQuadrantsOfLeftSideInPlace()
	{
		int halfRows = height / 2;
		int halfCols = width / 2;

		// Rotating rows only needs their references moved, not their contents.
		float[][] rotatedRows = new float[height][];
		for (int r = 0; r < height; r++)
		{
			rotatedRows[r] = array[(r + halfRows) % height];
		}
		array = rotatedRows;

		// Rotating a row left by halfCols is three reversals, which needs no scratch space.
		for (int r = 0; r < height; r++)
		{
			reverseRange(array[r], 0, halfCols);
			reverseRange(array[r], halfCols, width);
			reverseRange(array[r], 0, width);
		}
	}

	private static void reverseRange(float[] values, int start, int end)
	{
		for (int low = start, high = end - 1; low < high; low++, high--)
		{
			float temp = values[low];
			values[low] = values[high];
			values[high] = temp;
		}
	}

	/*
	 * Scales values in the given array such that the minimum is targetMin, and the maximum is targetMax.
	 */
	public void setContrast(float targetMin, float targetMax)
	{
		setContrast(targetMin, targetMax, 0, height, 0, width);
	}

	public void setContrast(float targetMin, float targetMax, int rowStart, int rows, int colStart, int cols)
	{
		Tuple2<Float, Float> contrastTuple = getContrast(rowStart, rows, colStart, cols);
		float min = contrastTuple.getFirst();
		float max = contrastTuple.getSecond();

		float range = max - min;
		float targetRange = targetMax - targetMin;

		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				float value = array[r][c];
				array[r][c] = (((value - min) / (range))) * (targetRange) + targetMin;
			}
		}
	}

	@SuppressWarnings("unused")
	public Tuple2<Float, Float> getContrast()
	{
		return getContrast(0, height, 0, width);
	}

	private Tuple2<Float, Float> getContrast(int rowStart, int rows, int colStart, int cols)
	{
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{
				float value = array[r][c];
				if (value < min)
					min = value;
				if (value > max)
					max = value;
			}
		}
		return new Tuple2<>(min, max);
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
				float value = Math.max(0f, array[r][c] * scale);
				if (value < 0f)
				{
					value = 0f;
				}
				else if (value > 1f)
				{
					value = 1f;
				}

				array[r][c] = value;
			}
		}
	}

	public Image toImage(int rowStart, int rows, int colStart, int cols, ImageType imageType)
	{
		Image image = Image.create(cols, rows, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(imageType);
		try (PixelWriter imagePixels = image.createPixelWriter())
		{
			for (int r = rowStart; r < rowStart + rows; r++)
			{
				for (int c = colStart; c < colStart + cols; c++)
				{
					int value = Math.min(maxPixelValue, (int) (array[r][c] * maxPixelValue));
					imagePixels.setGrayLevel(c - colStart, r - rowStart, value);
				}
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
		return array[y][x * 2 + 1];
	}

	public void setImaginary(int x, int y, float value)
	{
		array[y][x * 2 + 1] = value;
	}

	public float[][] getArrayJTransformsFormat()
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
