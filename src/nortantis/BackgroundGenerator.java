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
	public static BufferedImage runExperiment2(int scale, BufferedImage snippet)
	{
		BufferedImage img = ImageHelper.convertToGrayscale(snippet);
		int rows = img.getWidth() * scale;
		int cols = img.getHeight() * scale;
		Random rand = new Random();
		
		float[][] imgArray = new float[rows][cols]; 
		
		for (int curScale = 1; curScale <= scale; curScale *= 2)
		{
			float[][] imgScaled;
			if (curScale > 1)
				imgScaled = ImageHelper.imageToArray(ImageHelper.scaleByWidth(img, img.getWidth() * curScale));
			else
				imgScaled = ImageHelper.imageToArray(img);
			float[][] tiledScaledImg = ImageHelper.tile(imgScaled, rows, cols, 
					rand.nextInt(img.getWidth()), rand.nextInt(img.getHeight()));
			
//			ImageIO.write(arrayToImage(tiledScaledImg), "png", new File("tiledScaledImg" + curScale + ".png"));
			
			for (int r = 0; r < rows; r++)
				for (int c = 0; c < cols; c++)
				{	
					imgArray[r][c] += tiledScaledImg[r][c];
				}
		}
		
		ImageHelper.maximizeContrast(imgArray);
		
		BufferedImage result = ImageHelper.arrayToImage(imgArray);
//		ImageIO.write(result, "png", new File("experiment2.png"));
		return result;
	}
	
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

	private static BufferedImage softenEdgesWithGausianBlur(BufferedImage snippet)
	{
		BufferedImage box = new BufferedImage(snippet.getWidth(), snippet.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D bg = box.createGraphics();
		bg.setColor(Color.white);
		int padding = 50;
		bg.fillRect(padding, padding, box.getWidth() - padding*2, box.getHeight() - padding*2);
		ImageHelper.write(box, "box.png");
		// Use convolution to make a hazy background for the text.
		BufferedImage haze = ImageHelper.convolveGrayscale(box, ImageHelper.createGaussianKernel(padding), true);	
		ImageHelper.write(haze, "haze.png");
		snippet = ImageHelper.maskWithColor(snippet, Color.black, haze, false);
		ImageHelper.write(snippet, "filtered_snippet.png");		
		return snippet;
	}
	
	private static BufferedImage randomizeTexture(BufferedImage texture)
	{
		float[][] snippetArray = ImageHelper.tile(ImageHelper.imageToArray(texture), texture.getHeight(), texture.getWidth(), 0, 0);
		ImageHelper.normalize(snippetArray);
		BufferedImage randomImage = ImageHelper.arrayToImage(ImageHelper.genWhiteNoise(new Random(), texture.getWidth(), texture.getHeight()));
		BufferedImage grayImage = ImageHelper.convolveGrayscale(randomImage, snippetArray, true);
		return grayImage;
	}
	
	private static void runExperiment6() throws IOException
	{
		int compositeReps = 8;
		String snippetFileName = "valcia_snippet.png";
		String histogramSourceFileName = snippetFileName;
		BufferedImage snippet = ImageHelper.convertToGrayscale(ImageHelper.read(snippetFileName));
		snippet = ImageHelper.tileNTimes(snippet, 1);
		
		BufferedImage composite = new BufferedImage(snippet.getWidth() * compositeReps, snippet.getHeight() * compositeReps, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = composite.createGraphics();
		for (int y : new Range(compositeReps))
		{
			for (int x : new Range(compositeReps))
			{
				g.drawImage(randomizeTexture(snippet), x * snippet.getWidth(), y * snippet.getHeight(), null);
			}
		}
		g.dispose();
		
		BufferedImage result = randomizeTexture(composite);
		result = ImageHelper.matchHistogram(result, ImageHelper.read(histogramSourceFileName));
		ImageHelper.write(result, "result.png");
		ImageHelper.openImageInSystemDefaultEditor("result.png");
	}
	
	private static void runExperiment7() throws IOException
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
		BufferedImage randomImage = ImageHelper.arrayToImage(ImageHelper.genWhiteNoise(new Random(), kernel.length, kernel[0].length));
		BufferedImage grayImage = ImageHelper.convolveGrayscale(randomImage, kernel, true);
		
		ImageHelper.write(ImageHelper.matchHistogram(grayImage, originalTexture), "result.png");
		
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

	public static void main(String[] args) throws IOException
	{		
		long startTime = System.currentTimeMillis();
		
		runExperiment7();
		
		out.println("Total time (in seconds): " + (System.currentTimeMillis() - startTime)/1000.0);
		System.out.println("Done.");
		System.exit(0);
	}

}
