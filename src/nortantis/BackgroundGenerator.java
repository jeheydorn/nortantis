package nortantis;

import static java.lang.System.out;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.Random;

import nortantis.util.ImageHelper;
import nortantis.util.Range;

public class BackgroundGenerator
{		
	/**
	 * See generateUsingWhiteNoiseConvolution(Random, BufferedImage, int, int, boolean)
	 */
	public static BufferedImage generateUsingWhiteNoiseConvolution(Random rand, BufferedImage texture, int targetRows, int targetCols)
	{
		return generateUsingWhiteNoiseConvolution(rand, texture, targetRows, targetCols, true);
	}
	
	/**
	 * Generates a texture of the specified size which is similar in appearance to the given texture. 
	 * 
	 * To allow generating textures at arbitrary sizes, instead of just at the original texture's size, I'm using some techniques from 
	 * "Random Phase Textures: Theory and Synthesis" by Bruno Galerne, Yann Gousseau, and Jean-Michel Morel. Specifically, from the section
	 * "Synthesizing Textures With Arbitrary Sizes", I'm using step 2, but not steps 1 and 3. Instead of step 1 I'm using the original image.
	 * I compensate for this by increasing alpha in step 2. Instead of step 3 I'm convolving with white noise, which seems to work just as well.
	 * @param rand
	 * @param texture If this is a color image (not type BufferedImage.TYPE_INT_RGB), then I generate a color result by processing each channel 
	 * separately similar to what Bruno Galerne do, except I use histogram matching to get the color levels right.
	 * @param targetRows Number of rows in the result.
	 * @param targetCols Number of columns in the result.
	 * @param allowScalingTextureLarger If true, then if the texture is less than 1/4th the target height or width, then it will be scaled so that it is not.
	 * @return A randomly generated texture.
	 */
	public static BufferedImage generateUsingWhiteNoiseConvolution(Random rand, BufferedImage texture, int targetRows, int targetCols, boolean allowScalingTextureLarger)
	{
		// The conditions under which the two calls below change the texture are mutually exclusive.
		texture = cropTextureSmallerIfNeeded(texture, targetRows, targetCols);
		if (allowScalingTextureLarger)
		{
			texture = scaleTextureLargerIfNeeded(texture, targetRows, targetCols);
		}

		int rows = ImageHelper.getPowerOf2EqualOrLargerThan(Math.max( texture.getHeight(), targetRows));
		int cols = ImageHelper.getPowerOf2EqualOrLargerThan(Math.max(texture.getWidth(), targetCols));
		
		
		float alpha = 0.5f;
		float textureArea = texture.getHeight() * texture.getHeight();
		Raster raster = texture.getRaster();
		float varianceScaler = (float)Math.sqrt(((float)(rows*cols)) / textureArea);
		int alphaRows = (int)(alpha * texture.getHeight());
		int alphaCols = (int)(alpha * texture.getWidth());
		
		int numberOfColorChannels;
		BufferedImage allChannels;
		float maxPixelValue = (float)ImageHelper.getMaxPixelValue(texture);
		float[] means;
		if (ImageHelper.isSupportedGrayscaleType(texture))
		{
			numberOfColorChannels = 1;
			allChannels = null;
			means = new float[] {ImageHelper.calcMeanOfGrayscaleImage(texture)/maxPixelValue};
		}
		else
		{
			numberOfColorChannels = 3;
			allChannels = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB);
			means = ImageHelper.calcMeanOfEachColor(texture);
		}
		
		int randomImageType = texture.getType() == BufferedImage.TYPE_USHORT_GRAY ? BufferedImage.TYPE_USHORT_GRAY : BufferedImage.TYPE_BYTE_GRAY;
		BufferedImage randomImage = ImageHelper.arrayToImage(ImageHelper.genWhiteNoise(rand, rows, cols), randomImageType);
		

		for (int channel : new Range(numberOfColorChannels))
		{
			float[][] kernel = new float[rows][cols];
			for (int r = 0; r < rows; r++)
			{
				for (int c = 0; c < cols; c++)
				{
					int textureR = r - (rows - texture.getHeight())/2;
					int textureC = c - (cols - texture.getWidth())/2;
					if (textureR >= 0 && textureR < texture.getHeight() && textureC >= 0 && textureC < texture.getWidth())
					{
						float level;
						if (ImageHelper.isSupportedGrayscaleType(texture))
						{
							level = raster.getSample(textureC, textureR, 0)/maxPixelValue;
						}
						else
						{
							// Color image
							Color color = new Color(texture.getRGB(textureC, textureR));
							if (channel == 0)
							{
								level = color.getRed(); 
							}
							else if (channel == 1)
							{
								level = color.getGreen();
							}
							else
							{
								level = color.getBlue();
							}
						}
						
						float ar = calcSmoothParamether(textureR, alphaRows, alpha, texture.getHeight());
						float ac = calcSmoothParamether(textureC, alphaCols, alpha, texture.getWidth());
						
						kernel[r][c] = means[channel] + varianceScaler * (level - means[channel]) * ar * ac;
					}
					else
					{
						kernel[r][c] = means[channel];
					}
				}
			}
			
			BufferedImage grayImage = ImageHelper.convolveGrayscale(randomImage, kernel, true);
			kernel = null;
			
			if (numberOfColorChannels == 1)
			{
				allChannels = grayImage;
			}
			else
			{
				// Copy grayImage to a color channel in allChanels.
				Raster gRaster = grayImage.getRaster();
				for (int y = 0; y < allChannels.getHeight(); y++)
					for (int x = 0; x < allChannels.getWidth(); x++)
					{
						Color color = new Color(allChannels.getRGB(x, y));
						
						int level = gRaster.getSample(x, y, 0);
							
						int r = (channel == 0) ? level : color.getRed();
						int g = (channel == 1) ? level : color.getGreen();
						int b = (channel == 2) ? level : color.getBlue();
						Color combined = new Color(r,g,b);
						allChannels.setRGB(x, y, combined.getRGB());				
					}
			}
		}
				
		BufferedImage result = ImageHelper.matchHistogram(allChannels, texture);
		result = ImageHelper.extractRegion(result, 0, 0, targetCols, targetRows);
		
		return result;
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

	private static BufferedImage cropTextureSmallerIfNeeded(BufferedImage texture, int rows, int cols)
	{
		if (texture.getWidth() < cols || texture.getHeight() < rows)
		{
			return texture;
		}
		
		// The texture is wider and taller than we need it to be. Return a piece cropped out of the middle.
		return ImageHelper.extractRegion(texture, (texture.getWidth() - cols) / 2, (texture.getHeight() - rows) / 2, cols, rows);
	}

	private static BufferedImage scaleTextureLargerIfNeeded(BufferedImage texture, int rows, int cols)
	{
		int scaleThreshold = 5;
		if (((float)texture.getWidth()) / cols < ((float)texture.getHeight()) / rows)
		{
			if (texture.getWidth() * scaleThreshold < cols)
			{
				return ImageHelper.scaleByWidth(texture, cols / scaleThreshold);
			}
		}
		else
		{
			if (texture.getHeight() * scaleThreshold < rows)
			{
				return ImageHelper.scaleByHeight(texture, rows / scaleThreshold);
			}
		}
		return texture;
	}

	public static void main(String[] args) throws IOException
	{		
		long startTime = System.currentTimeMillis();
		
		BufferedImage result = generateUsingWhiteNoiseConvolution(new Random(), ImageHelper.read("C:\\Users\\Joseph\\Dropbox\\Joseph\\Games\\SailGame\\textures\\seeds\\maple trunk.png"), 1024, 1024, false);
		ImageHelper.openImageInSystemDefaultEditor(result, "result");
		
		out.println("Total time (in seconds): " + (System.currentTimeMillis() - startTime)/1000.0);
		System.out.println("Done.");
		System.exit(0);
	}

}
