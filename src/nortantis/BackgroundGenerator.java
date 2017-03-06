package nortantis;

import static java.lang.System.out;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

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
				imgScaled = ImageHelper.imageToArrayFloat(ImageHelper.scaleByWidth(img, img.getWidth() * curScale));
			else
				imgScaled = ImageHelper.imageToArrayFloat(img);
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

	// Results don't look much like the original snippet.
//	public static void runExperiment3() throws IOException
//	{
//		int scale = 8;
//		BufferedImage img = ImageHelper.arrayToImage(ImageHelper.genWhiteNoise(new Random(), 256 * scale, 256 * scale));
//		img = ImageHelper.convertToGrayscale(img);
//	
//		BufferedImage kernelImage = runExperiment2(scale);
//		float[][] kernel = ImageHelper.imageToArrayFloat(kernelImage);
//		ImageHelper.normalize(kernel);
//		
//		BufferedImage result = ImageHelper.convolveGrayscale(img, kernel);
//		ImageIO.write(result, "png", new File("experiment3.png"));
//		System.out.println("Done.");
//		System.exit(0);
//	
//		BufferedImage img = ImageHelper.convertToGrayscale(ImageIO.read(new File("ocean_small.png")));
//		int cols = ImageHelper.getPowerOf2EqualOrLargerThan(img.getWidth());
//		int rows = ImageHelper.getPowerOf2EqualOrLargerThan(img.getHeight());
//		
//		// Convert the input to the format required by JTransforms.
//		float[][] data = new float[rows][2 * cols];
//		
//		int imgRowPadding = rows - img.getHeight();
//		int imgColPadding = cols - img.getWidth();
//		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);
//		{
//			Raster raster = img.getRaster();
//			for (int c = 0; c < img.getWidth(); c++)
//				for (int r = 0; r < img.getHeight(); r++)
//				{
//					float grayLevel = raster.getSample(c, r, 0)/255f;
//					data[r + imgRowPadding/2][c + imgColPadding/2] = grayLevel;
//				}
//	
//	
//			// Do the forward FFT.
//			fft.realForwardFull(data);
//		}
//	
//	}
	
	public static void runExperiment4() throws IOException
	{
		int scale = 4;
		String snippetFileName = "";
//		BufferedImage snippet = ImageHelper.convertToGrayscale(runExperiment2(scale, ImageIO.read(new File(snippetFileName))));
		BufferedImage snippet = ImageHelper.convertToGrayscale(ImageIO.read(new File(snippetFileName)));
		BufferedImage randomImage = ImageHelper.arrayToImage(ImageHelper.genWhiteNoise(new Random(), snippet.getWidth(), snippet.getHeight()));
		//BufferedImage randomImage = FractalBGGenerator.generate(new Random(), 0.2f, snippet.getWidth(), snippet.getHeight(), 0.75f);
		BufferedImage grayImage = ImageHelper.convolveGrayscale(randomImage, ImageHelper.imageToArrayFloat(snippet), true);
		BufferedImage result = ImageHelper.matchHistogram(grayImage, ImageIO.read(new File(snippetFileName)));
		ImageHelper.write(result, "result.png");
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
