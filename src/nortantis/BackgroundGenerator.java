package nortantis;

import static java.lang.System.out;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import org.jtransforms.fft.FloatFFT_2D;

import util.ImageHelper;
import util.Range;

public class BackgroundGenerator
{	
	// Apply fractal filtering to a snippet
	public static float[][] applyFractalLowPassFiltering(BufferedImage snippet)
	{
		int imageScale = 1;
		float contrast = 0.75f;
		float p = 1.2f; 
		//BufferedImage snippet = ImageHelper.convertToGrayscale(ImageIO.read(new File(snippetFileName)));
		int resultWidth = snippet.getWidth() * imageScale;
		int resultHeight = snippet.getHeight() * imageScale;

		
		Random rand = new Random();
		float[][] data = new float[resultWidth][2 * resultHeight];

		FloatFFT_2D fft = new FloatFFT_2D(resultHeight, resultWidth);
		{
			Raster raster = snippet.getRaster();
			for (int r = 0; r < snippet.getHeight(); r++)
				for (int c = 0; c < snippet.getWidth(); c++)
				{
					float grayLevel = raster.getSample(c, r, 0);
					grayLevel /= 255f;
					data[r][c] = grayLevel;
				}
	
			// Do the forward FFT.
			fft.realForwardFull(data);
		}

								
		// Multiply by 1/(f^p) in the frequency domain.
		for (int r = 0; r < resultWidth; r++)
			for (int c = 0; c < resultHeight; c++)
			{
				float dataR = data[r][c*2];
				float dataI = data[r][c*2 + 1];
				
				float rF = Math.min(r, resultWidth - r);
				float cF = Math.min(c, resultHeight - c);
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
				
//		 Do the inverse DFT on the product.
		fft.complexInverse(data, true);
		ImageHelper.moveRealToLeftSide(data);
		//ImageHelper.swapQuadrantsOfLeftSideInPlace(data);
		
		
		ImageHelper.setContrast(data, 0.5f - contrast/2f, 0.5f + contrast/2f);
				
		float[][] result = ImageHelper.getLefHalf(data);
		return result;
	}
	
	private static void generateUsingRandomPhaseNoise() throws IOException
	{
		int targetSize = 1024;	
		float alpha = 0.5f;
		float targetSizeSquared = (float)(targetSize*targetSize);
		String textureFileName = "valcia_snippet.png";
		BufferedImage originalTexture = ImageHelper.read(textureFileName);
		BufferedImage texture = ImageHelper.convertToGrayscale(originalTexture);
		float textureArea = texture.getHeight() * texture.getHeight();
		float[][] kernel = new float[targetSize][targetSize];
		float mean = ImageHelper.calcMeanOfGrayscaleImage(texture)/255f;
		Raster raster = texture.getRaster();
		float varianceScaler = (float)Math.sqrt(targetSizeSquared / textureArea);
		int alphaRows = (int)(alpha * texture.getHeight());
		int alphaCols = (int)(alpha * texture.getWidth());
		Random rand = new Random();
		
		for (int r = 0; r < targetSize; r++)
		{
			for (int c = 0; c < targetSize; c++)
			{
				int textureR = r - (targetSize - texture.getHeight())/2;
				int textureC = c - (targetSize - texture.getWidth())/2;
				if (textureR >= 0 && textureR < texture.getHeight() && textureC >= 0 && textureC < texture.getWidth())
				{
					float level = raster.getSample(textureC, textureR, 0)/255f;
					
					float ar = calcSmoothParamether(textureR, alphaRows, alpha, texture.getHeight());
					float ac = calcSmoothParamether(textureC, alphaCols, alpha, texture.getWidth());
					
					kernel[r][c] = mean + varianceScaler * (level - mean) * ar * ac;
				}
				else
				{
					kernel[r][c] = mean;
				}
			}
		}
		
		//ImageHelper.write(ImageHelper.arrayToImage(kernel), "textureKernel.png");
		BufferedImage randomImage = ImageHelper.arrayToImage(ImageHelper.genWhiteNoise(rand, kernel.length, kernel[0].length));
		BufferedImage grayImage = ImageHelper.convolveGrayscale(randomImage, kernel, true);
		
		BufferedImage result = ImageHelper.matchHistogram(grayImage, originalTexture);
		ImageHelper.write(result, "result.png");
		
	}
	
	private static float calcSmoothParamether(int textureR, int alphaPixels, float alpha, int imageLength)
	{
		if (textureR <= alphaPixels/2)
		{
			return calcSmoothingFunction(alpha, ((float)textureR) / imageLength);
		}
		else if (textureR >= (imageLength - alphaPixels/2))
		{
			return calcSmoothingFunction(alpha, ((float)(textureR - (imageLength - alphaPixels))) / imageLength);
		}
		
		return 1f;

	}
	
	private static float calcSmoothingFunction(float alpha, float t)
	{
		float x = (2 * t / alpha) - 1;
		// The number 0.367879 is the value of the smoothing function at alpha/2, which is its maximum. 
		// I multiply by 1/0.367879 to make the range of the smoothing function [0,1].
		return (float)Math.exp(-1 / (1 - (x * x))) * (1f / 0.367879f);		
	}
	
	/***
	 * Finds the periodic component of an image. Based on periodic_component_color.
	 * @param rand
	 * @param texture
	 * @return
	 */
	private static float[][] calcPeriodicComponent(Random rand, BufferedImage texture)
	{
		float mean = ImageHelper.calcMeanOfGrayscaleImage(texture);
		ComplexArray randomPhasesWithPoisson = generateRandomPhasesWithPoissonComplexFilter(rand, texture.getWidth(), texture.getHeight());
		float[][] laplacian = calcDiscreteLaplacian(ImageHelper.imageToArray(texture));
		return null; // TODO
	}
	
	/**
	 * Based on random_phase_with_poisson_complex_filter in random_phase_noise_lib.c.
	 */
	private static ComplexArray generateRandomPhasesWithPoissonComplexFilter(Random rand, int width, int height)
	{
		boolean isXEven = width % 2 == 0;
		boolean isYEven = height % 2 == 0;
		int halfWidth = width / 2;
		int halfHeight = height / 2;
		float[][] poissonComplexFilter = createPoissonComplexFilter(width, height);
		ComplexArray result = new ComplexArray(width, height);
		
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
	            // Case 1: (x,y) is its own symmetric: (x,y)
	            // corresponds to a real coefficient of the DFT.
	            // A random sign is drawn (except for (0,0)
	            // where we impose sign=1. to conserve the
	            // original mean)
	            if (((x == 0) || (isXEven && (x == halfWidth)))
	                && ((y == 0) || (isYEven && (y == halfHeight)))) 
	            {
	                float sign = (((rand.nextFloat() < 0.5f)
	                         || ((x == 0) && (y == 0))) ? 1.0f : -1.0f);
	                result.setReal(x, y, sign * poissonComplexFilter[y][x]);
	                result.setImaginary(x, y, 0f);
	            }
	            // Case 2: Both (x,y) and its symmetric are in
	            // the same half-domain, and y > height/2.
	            // Then the random phase of this symmetric
	            // point has already been drawn.
	            else if (((x == 0) || (isXEven && x == halfWidth))
	                     && y > halfHeight) 
	            {
	                // Copy the symmetric point
	                int ySymmetric = height - y;
	                result.setRealInput(x, y, result.getReal(x, ySymmetric));
	                result.setImaginary(x, y, result.getImaginary(x, ySymmetric));
	            }
	            else 
	            {
	                // Draw a random phase
	                float theta = (2f * rand.nextFloat() - 1f) * (float)Math.PI;
	                result.setRealInput(x, y, (float) Math.cos(theta) * poissonComplexFilter[y][x]);
	                result.setImaginary(x, y, (float) Math.sin(theta) * poissonComplexFilter[y][x]);
	            }
			}
		}
		
		return result;
	}

	/**
	 * Based on poisson_complex_filter in random_phase_noise_lib.c.
	 */
	private static float[][] createPoissonComplexFilter(int width, int height)
	{
		float[] cosX = new float[width/2 + 1];
		for (int x : new Range(cosX.length))
		{
			cosX[x] = (float) Math.cos(2.0f * ((float) x) * Math.PI / ((float) width)); 
		}
		
		float[] cosYMinTwo = new float[height];
		for (int y : new Range(cosYMinTwo.length))
		{
			cosYMinTwo[y] = (float)Math.cos(2.0f * ((float) y) * Math.PI / ((float) height)) - 2f;
		}
		
		float halfInverseArea = 0.5f / ((float)(width * height));
		float[][] result = new float[width][height];
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				result[y][x] = halfInverseArea / (cosX[x] + cosYMinTwo[y]);
			}
		}
		
		return result;
	}
	
	/**
	 * Calculate the discrete laplacian of an image. Points off the image are defined as 0.
	 */
	private static float[][] calcDiscreteLaplacian(float[][] image)
	{
		int height = image.length;
		int width = image[0].length;
		float[][] result = new float[image.length][image[0].length];
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				result[y][x] += (x > 0) ? image[y][x - 1] : 0f;
				result[y][x] += (x < width - 1) ? image[y][x + 1] : 0f;
				result[y][x] += (y > 0) ? image[y - 1][x] : 0f;
				result[y][x] += (y < height - 1) ? image[y + 1][x] : 0f;
				result[y][x] -= 4 * (image[y][x]);
			}
		}
		
		return result;
	}

	public static void main(String[] args) throws IOException
	{		
		long startTime = System.currentTimeMillis();
		
		generateUsingRandomPhaseNoise();
		
		out.println("Total time (in seconds): " + (System.currentTimeMillis() - startTime)/1000.0);
		System.out.println("Done.");
		System.exit(0);
	}

}
