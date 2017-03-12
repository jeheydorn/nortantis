package nortantis;

import static java.lang.System.out;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import org.jtransforms.fft.FloatFFT_2D;

import util.ImageHelper;

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
	
	// The result has clearly repeating patterns, and blending with lower frequencies makes the higher frequencies less visible, which doesn't look as good
	// as the original.
	public static void runExperiment5()
	{
		int scale = 4; // must be a power of 2
		BufferedImage snippet = ImageHelper.read("valcia_snippet.png");

		BufferedImage img = ImageHelper.convertToGrayscale(snippet);
		int rows = img.getWidth() * scale;
		int cols = img.getHeight() * scale;
		Random rand = new Random();
		
		float[][] summedArray = new float[rows][cols]; 

		float[][] layer = ImageHelper.tile(ImageHelper.imageToArray(randomizeTexture(snippet, 2)), rows, cols, 
				rand.nextInt(snippet.getHeight()), rand.nextInt(snippet.getWidth()));
		for (int r = 0; r < rows; r++)
			for (int c = 0; c < cols; c++)
			{	
				summedArray[r][c] += layer[r][c];
			}
		
		for (int curScale = 1; curScale <= scale; curScale *= 2)
		{
			BufferedImage scaledSnippet = ImageHelper.scaleByWidth(snippet, snippet.getWidth() * curScale);
			BufferedImage randomizedScaledSnippet = randomizeTexture(scaledSnippet, 2);
			float[][] fractalLowPass = applyFractalLowPassFiltering(randomizedScaledSnippet);
			layer = ImageHelper.tile(fractalLowPass, rows, cols, 
					rand.nextInt(fractalLowPass.length), rand.nextInt(fractalLowPass[0].length)); 
			
			ImageHelper.write(ImageHelper.arrayToImage(layer), "layer_" + curScale + ".png");
			//ImageHelper.write(ImageHelper.arrayToImage(fractalLowPass), "fractalLowPass" + curScale + ".png");
						
			for (int r = 0; r < rows; r++)
				for (int c = 0; c < cols; c++)
				{	
					summedArray[r][c] += layer[r][c];
				}
		}
		
		ImageHelper.maximizeContrast(summedArray);
		BufferedImage result = ImageHelper.matchHistogram(ImageHelper.arrayToImage(summedArray), snippet);
		
		ImageHelper.write(result, "result.png");
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
	
	// Works well but the result must be 2x the dimension of the input and no more.
	public static void runExperiment4() throws IOException
	{
		int scale = 2;
		String snippetFileName = "fractal_paper.png";
		BufferedImage grayImage = randomizeTexture(ImageHelper.convertToGrayscale(ImageIO.read(new File(snippetFileName))), scale);
		BufferedImage result = ImageHelper.matchHistogram(grayImage, ImageIO.read(new File(snippetFileName)));
		ImageHelper.write(result, "result.png");		
	}
	
	private static BufferedImage randomizeTexture(BufferedImage texture, int scale)
	{
		float[][] snippetArray = ImageHelper.tile(ImageHelper.imageToArray(texture), texture.getHeight() * scale, texture.getWidth() * scale, 0, 0);
		ImageHelper.normalize(snippetArray);
		//float[][] snippetArray = ImageHelper.tile(ImageHelper.imageToArrayFloat(snippet), snippet.getHeight(), snippet.getWidth(), 0, 0);
		BufferedImage randomImage = ImageHelper.arrayToImage(ImageHelper.genWhiteNoise(new Random(), texture.getWidth() * scale, texture.getHeight() * scale));
		//BufferedImage randomImage = FractalBGGenerator.generate(new Random(), 0.2f, snippet.getWidth(), snippet.getHeight(), 0.75f);
		BufferedImage grayImage = ImageHelper.convolveGrayscale(randomImage, snippetArray, true);
		return grayImage;
	}

	public static void main(String[] args) throws IOException
	{		
		long startTime = System.currentTimeMillis();
		
		// If I want the colors to look better, I could use mutiple images to get colors for
		// histogram matching.
//		BufferedImage grayBackground = runExperiment2(8, ImageIO.read(new File("lord_of_the_rings_snippet.png")));
//		BufferedImage background = ImageHelper.matchHistogram(grayBackground,
//				ImageIO.read(new File("lord_of_the_rings_snippet.png")));
//		ImageIO.write(background, "png", new File("background.png"));
		
		runExperiment4();
		
		out.println("Total time (in seconds): " + (System.currentTimeMillis() - startTime)/1000.0);
		System.out.println("Done.");
		System.exit(0);
	}

}
