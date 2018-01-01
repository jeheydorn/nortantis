package nortantis;

import static java.lang.System.out;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import org.jtransforms.fft.FloatFFT_2D;

import util.ImageHelper;

public class FractalBGGenerator
{
	/**
	 * Generates a fractal image using the tutorial at http://paulbourke.net/fractals/noise/,
	 * under "Frequency Synthesis of Landscapes (and clouds)". The results look the same as
	 * Gimp's plasma clouds.
	 * @param p The power in the fractal exponent. This controls how smooth the result is.
	 * @param constrast The contrast of the resulting image. 1 means full. Less than one will
	 * scale the pixel values in the result about the central value.
	 * @throws IOException 
	 */
	public static BufferedImage generate(Random rand, float p, int width, int height, float contrast)
	{
		int cols = ImageHelper.getPowerOf2EqualOrLargerThan(width);
		int rows = ImageHelper.getPowerOf2EqualOrLargerThan(height);
		// For some reason this algorithm only works for creating a square result.
		if (cols < rows)
			cols = rows;
		else if (rows < cols)
			rows = cols;

		// Generate white noise and convert the input to the format required by JTransforms.
		float[][] data = new float[rows][2 * cols];
		
		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);
		{
			for (int r = 0; r < rows; r++)
				for (int c = 0; c < cols; c++)
				{
					data[r][c] = rand.nextFloat();
				}
	

			// Do the forward FFT.
			fft.realForwardFull(data);
		}
								
		// Multiply by 1/(f^p) in the frequency domain.
		for (int r = 0; r < rows; r++)
			for (int c = 0; c < cols; c++)
			{
				float dataR = data[r][c*2];
				float dataI = data[r][c*2 + 1];
				
				float rF = Math.min(r, rows - r);
				float cF = Math.min(c, cols - c);
				float f = (float)Math.sqrt(rF * rF + cF * cF);
				float real;
				float imaginary;
				if (f == 0f)
				{
					real = 0f;
					imaginary = 0f;
				}
				else
				{
					float scale = (float)(1.0/(Math.pow(f, p)));
					real = dataR * scale;
					imaginary = dataI * scale;
				}
				data[r][c*2] = real;
				data[r][c*2 + 1] = imaginary;
			}
		
		//ImageIO.write(ImageHelper.arrayToImage(data), "png", new File("frequencies.png"));
		
//		 Do the inverse DFT on the product.
		fft.complexInverse(data, true);
		ImageHelper.moveRealToLeftSide(data);
		ImageHelper.swapQuadrantsOfLeftSideInPlace(data);
		
		
		ImageHelper.setContrast(data, 0.5f - contrast/2f, 0.5f + contrast/2f);
				
		BufferedImage result = ImageHelper.arrayToImage(data, 0, height,
				0, width);
		return result;

	}


	public static void main(String[] args) throws IOException
	{		
		long startTime = System.currentTimeMillis();
		
		BufferedImage background = generate(new Random(), 1.3f, 4096, 4096, 0.75f);
		
		out.println("Time to generate (in seconds): " + (System.currentTimeMillis() - startTime)/1000.0);
		
		ImageIO.write(background, "png", new File("cloud.png"));
		System.out.println("Done.");
		System.exit(0);
	}

}
