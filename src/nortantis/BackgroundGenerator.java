package nortantis;

import static java.lang.System.out;

import java.io.IOException;
import java.util.Random;

import org.imgscalr.Scalr.Method;

import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.ImageHelper;
import nortantis.util.Range;

public class BackgroundGenerator
{
	/**
	 * See generateUsingWhiteNoiseConvolution(Random, Image, int, int, boolean)
	 */
	public static Image generateUsingWhiteNoiseConvolution(Random rand, Image texture, int targetRows, int targetCols)
	{
		return generateUsingWhiteNoiseConvolution(rand, texture, targetRows, targetCols, true);
	}

	/**
	 * Generates a texture of the specified size which is similar in appearance to the given texture.
	 * 
	 * To allow generating textures at arbitrary sizes, instead of just at the original texture's size, I'm using some techniques from
	 * "Random Phase Textures: Theory and Synthesis" by Bruno Galerne, Yann Gousseau, and Jean-Michel Morel. Specifically, from the section
	 * "Synthesizing Textures With Arbitrary Sizes", I'm using step 2, but not steps 1 and 3. Instead of step 1 I'm using the original
	 * image. I compensate for this by increasing alpha in step 2. Instead of step 3 I'm convolving with white noise, which seems to work
	 * just as well.
	 * 
	 * @param rand
	 * @param texture
	 *            If this is a color image (not type Image.TYPE_INT_RGB), then I generate a color result by processing each channel
	 *            separately similar to what Bruno Galerne do, except I use histogram matching to get the color levels right.
	 * @param targetRows
	 *            Number of rows in the result.
	 * @param targetCols
	 *            Number of columns in the result.
	 * @param allowScalingTextureLarger
	 *            If true, then if the texture is less than 1/4th the target height or width, then it will be scaled so that it is not.
	 * @return A randomly generated texture.
	 */
	public static Image generateUsingWhiteNoiseConvolution(Random rand, Image texture, int targetRows, int targetCols,
			boolean allowScalingTextureLarger)
	{
		// The conditions under which the two calls below change the texture are mutually exclusive.
		texture = cropTextureSmallerIfNeeded(texture, targetRows, targetCols);
		if (allowScalingTextureLarger)
		{
			texture = scaleTextureLargerIfNeeded(texture, targetRows, targetCols);
		}

		int rows = ImageHelper.getPowerOf2EqualOrLargerThan(Math.max(texture.getHeight(), targetRows));
		int cols = ImageHelper.getPowerOf2EqualOrLargerThan(Math.max(texture.getWidth(), targetCols));

		float alpha = 0.5f;
		float textureArea = texture.getHeight() * texture.getHeight();
		float varianceScaler = (float) Math.sqrt(((float) (rows * cols)) / textureArea);
		int alphaRows = (int) (alpha * texture.getHeight());
		int alphaCols = (int) (alpha * texture.getWidth());

		int numberOfColorChannels;
		Image allChannels;
		float maxPixelValue = (float) texture.getMaxPixelLevel();
		float[] means;
		if (texture.isGrayscaleOrBinary())
		{
			numberOfColorChannels = 1;
			allChannels = null;
			means = new float[]
			{
					ImageHelper.calcMeanOfGrayscaleImage(texture) / maxPixelValue
			};
		}
		else
		{
			numberOfColorChannels = 3;
			allChannels = Image.create(cols, rows, ImageType.RGB);
			means = ImageHelper.calcMeanOfEachColor(texture);
		}

		ImageType randomImageType = texture.getType() == ImageType.Grayscale16Bit ? ImageType.Grayscale16Bit : ImageType.Grayscale8Bit;
		Image randomImage = ImageHelper.genWhiteNoise(rand, rows, cols, randomImageType);

		for (int channel : new Range(numberOfColorChannels))
		{
			float[][] kernel = new float[rows][cols];
			for (int r = 0; r < rows; r++)
			{
				for (int c = 0; c < cols; c++)
				{
					int textureR = r - (rows - texture.getHeight()) / 2;
					int textureC = c - (cols - texture.getWidth()) / 2;
					if (textureR >= 0 && textureR < texture.getHeight() && textureC >= 0 && textureC < texture.getWidth())
					{
						float level;
						if (texture.isGrayscaleOrBinary())
						{
							level = texture.getNormalizedPixelLevel(textureC, textureR);
						}
						else
						{
							// Color image
							level = texture.getBandLevel(textureC, textureR, channel);
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

			Image grayImage = ImageHelper.convolveGrayscale(randomImage, kernel, true, false);
			kernel = null;

			if (numberOfColorChannels == 1)
			{
				allChannels = grayImage;
			}
			else
			{
				// Copy grayImage to a color channel in allChanels.
				for (int y = 0; y < allChannels.getHeight(); y++)
				{
					for (int x = 0; x < allChannels.getWidth(); x++)
					{
						int level = grayImage.getGrayLevel(x, y);
						allChannels.setBandLevel(x, y, channel, level);
					}
				}
			}
		}

		randomImage = null;

		// If the texture is small, scale it with interpolation to create a better histogram for histogram matching.
		// This reduces frequency of bright white spots on the resulting image.
		Image colorsForHistogramMatching;
		if (texture.getWidth() < targetCols || texture.getHeight() < targetRows)
		{
			colorsForHistogramMatching = ImageHelper.scale(texture, Math.max(texture.getWidth(), targetCols),
					Math.max(texture.getHeight(), targetRows), Method.BALANCED);
		}
		else
		{
			colorsForHistogramMatching = texture;
		}

		Image result = ImageHelper.matchHistogram(allChannels, colorsForHistogramMatching);
		result = ImageHelper.copySnippet(result, 0, 0, targetCols, targetRows);

		return result;
	}

	private static float calcSmoothParamether(int textureR, int alphaPixels, float alpha, int imageLength)
	{
		if (textureR <= alphaPixels / 2)
		{
			return calcSmoothingFunction(alpha, ((float) textureR) / imageLength);
		}
		else if (textureR >= (imageLength - alphaPixels / 2))
		{
			return calcSmoothingFunction(alpha, ((float) (textureR - (imageLength - alphaPixels))) / imageLength);
		}

		return 1f;

	}

	private static float calcSmoothingFunction(float alpha, float t)
	{
		float x = (2 * t / alpha) - 1;
		// The number 0.367879 is the value of the smoothing function at alpha/2, which is its maximum.
		// I divide by 0.367879 to make the range of the smoothing function [0,1].
		return ((float) Math.exp(-1 / (1 - (x * x)))) / 0.367879f;
	}

	private static Image cropTextureSmallerIfNeeded(Image texture, int rows, int cols)
	{
		if (texture.getWidth() < cols || texture.getHeight() < rows)
		{
			return texture;
		}

		// The texture is wider and taller than we need it to be. Return a piece cropped out of the middle.
		return ImageHelper.copySnippet(texture, (texture.getWidth() - cols) / 2, (texture.getHeight() - rows) / 2, cols, rows);
	}

	private static Image scaleTextureLargerIfNeeded(Image texture, int rows, int cols)
	{
		int scaleThreshold = 5;
		if (((float) texture.getWidth()) / cols < ((float) texture.getHeight()) / rows)
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
		// Tell drawing code to use AWT.
		PlatformFactory.setInstance(new AwtFactory());

		Stopwatch sw = new Stopwatch();

		Image image = Image.read("C:\\Program Files\\Nortantis\\app\\assets\\background textures\\wavy paper.png");
		if (image == null)
		{
			out.print("Unable to load image.");
		}
		Image result = generateUsingWhiteNoiseConvolution(new Random(), image, 3072, 3072, false);
		ImageHelper.openImageInSystemDefaultEditor(result, "result");

		sw.printElapsedTime();
		System.out.println("Done.");
		System.exit(0);
	}

}
