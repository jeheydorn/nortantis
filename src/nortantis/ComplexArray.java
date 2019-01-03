package nortantis;

/**
 * Stores a 2D array of complex numbers in JTransform's format.
 * 
 * *Input methods assume the internal array is being prepared for a real forward FFT by JTransforms.
 * *Output methods assume the internal array has been through a forward FFT by JTransforms.
 */
public class ComplexArray 
{
	private float[][] array;
	
	/**
	 * Creates a 2D array of complex numbers
	 */
	public ComplexArray(int width, int height)
	{	
		array = new float[height][width * 2];
	}
	
	/**
	 * Does complex multiplication of this by other and stores the result into this.
	 */
	public void multiplyInPlace(ComplexArray other)
	{
		assert other.getArrayJTransformsFormat().length == array.length;
		assert other.getArrayJTransformsFormat()[0].length == array[0].length;
		float [][] otherArray = other.getArrayJTransformsFormat();
		
		int rows = array.length;
		int cols = array[0].length / 2;
		for (int r = 0; r < rows; r++)
			for (int c = 0; c < cols; c++)
			{
				float dataR = array[r][c*2];
				float dataI = array[r][c*2 + 1];
				float kernelR = otherArray[r][c*2];
				float kernelI = otherArray[r][c*2 + 1];
				
				float real = dataR * kernelR - dataI * kernelI;
				array[r][c*2] = real;
				float imaginary = dataI * kernelR + dataR * kernelI;
				array[r][c*2 + 1] = imaginary;
			}
	}
			
	/**
	 * When the internal array is being prepared for a real forward FFT by JTransforms, JTransforms expects real inputs
	 * to all be on the left. This uses that format.
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
