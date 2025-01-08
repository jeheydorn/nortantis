package nortantis;

import static java.lang.System.out;

import java.io.IOException;
import java.util.Random;

import org.jtransforms.fft.FloatFFT_2D;

import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.ImageHelper;
import nortantis.util.ThreadHelper;

public class FractalBGGenerator
{
	/**
	 * Generates a fractal image using the tutorial at http://paulbourke.net/fractals/noise/, under "Frequency Synthesis of Landscapes (and
	 * clouds)". The results look the same as Gimp's plasma clouds.
	 * 
	 * @param p
	 *            The power in the fractal exponent. This controls how smooth the result is.
	 * @param contrast
	 *            The contrast of the resulting image. 1 means full. Less than one will scale the pixel values in the result about the
	 *            central value.
	 * @throws IOException
	 */
	public static Image generate(Random rand, float p, int width, int height, float contrast, boolean isLowMemoryMode)
	{
		// JTransforms is faster when the input is padded out to a power of two in each dimension, but doing so also takes much more memory.
		int cols = isLowMemoryMode ? width : ImageHelper.getPowerOf2EqualOrLargerThan(width);
		int rows = isLowMemoryMode ? height : ImageHelper.getPowerOf2EqualOrLargerThan(height);
		// For some reason this algorithm only works for creating a square result.
		if (cols < rows)
			cols = rows;
		else if (rows < cols)
			rows = cols;

		// Generate white noise and convert the input to the format required by JTransforms.
		ComplexArray data = new ComplexArray(cols, rows);

		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);
		{
			for (int r = 0; r < rows; r++)
				for (int c = 0; c < cols; c++)
				{
					data.setRealInput(c, r, rand.nextFloat());
				}

			// Do the forward FFT.
			fft.realForwardFull(data.getArrayJTransformsFormat());
		}

		Stopwatch sw = new Stopwatch("Filter in frequency domain.");
		final int rowsFinal = rows;
		final int colsFinal = cols;
		// Multiply by 1/(f^p) in the frequency domain.
		ThreadHelper.getInstance().processRowsInParallel(0, rows, (r) ->
		{
			for (int c = 0; c < colsFinal; c++)
			{
				float dataR = data.getReal(c, r);
				float dataI = data.getImaginary(c, r);

				float rF = Math.min(r, rowsFinal - r);
				float cF = Math.min(c, colsFinal - c);
				float f = (float) Math.sqrt(rF * rF + cF * cF);
				float real;
				float imaginary;
				if (f == 0f)
				{
					real = 0f;
					imaginary = 0f;
				}
				else
				{
					float scale = (float) (1.0 / (Math.pow(f, p)));
					real = dataR * scale;
					imaginary = dataI * scale;
				}
				data.setReal(c, r, real);
				data.setImaginary(c, r, imaginary);
			}
		});
		
		sw.printElapsedTime();

		// Do the inverse DFT on the product.
		fft.complexInverse(data.getArrayJTransformsFormat(), true);
		Stopwatch sw2 = new Stopwatch("moveRealToLeftSide");
		data.moveRealToLeftSide();
		sw2.printElapsedTime();
		Stopwatch sw3 = new Stopwatch("swapQuadrantsOfLeftSideInPlace");
		data.swapQuadrantsOfLeftSideInPlace();
		sw3.printElapsedTime();

		Stopwatch sw4 = new Stopwatch("setContrast");
		data.setContrast(0.5f - contrast / 2f, 0.5f + contrast / 2f);
		sw4.printElapsedTime();

		Stopwatch sw5 = new Stopwatch("toImage");
		Image result = data.toImage(0, height, 0, width, ImageType.Grayscale8Bit);
		sw5.printElapsedTime();
		return result;

	}

	public static void main(String[] args) throws IOException
	{
		Stopwatch sw = new Stopwatch("generate fractal image");

		// Tell drawing code to use AWT.
		PlatformFactory.setInstance(new AwtFactory());

		final int size = 4096 * 1;
		Image background = generate(new Random(), 1.3f, size, size, 0.75f, false);

		sw.printElapsedTime();

		ImageHelper.openImageInSystemDefaultEditor(background, "cloud");
		System.out.println("Done.");
	}

}
