package util;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Performs histogram equalization on images.
 * @author joseph
 *
 */

public class HistogramEqualizer 
{	
	/**
	 * lookupTable maps grayscale levels to grayscale levels. The index used is the 
	 * function input to look up.
	 */
	List<int[]> lookupTables;
	List<int[]> inverses;
	int imageType;
	
	public HistogramEqualizer(BufferedImage image)
	{
		this.imageType = image.getType();
		lookupTables = new ArrayList<>();
		if (image.getType() == BufferedImage.TYPE_BYTE_GRAY)
		{
			int[] histogram = countPixelLevels(image, 0);
			lookupTables.add(createLookupTable(histogram, image.getWidth() * image.getHeight()));
		}
		else
		{
			for (int band : new Range(3))
			{
				int[] histogram = countPixelLevels(image, band);
				createLookupTable(histogram, image.getWidth() * image.getHeight());
				lookupTables.add(createLookupTable(histogram, image.getWidth() * image.getHeight()));
			}
		}
//		else
//		{
//			throw new UnsupportedOperationException("Unsupported image type: " + 
//					ImageHelper.bufferedImageTypeToString(image));
//		}
	}

	private static int[] createLookupTable(int[] histogram, int imageArea)
	{
		int sum = 0;
		int[] lookupTable = new int[histogram.length];
		double scale = (lookupTable.length - 1)/(double)imageArea;
		for (int r = 0; r < lookupTable.length; r++)
		{
			sum += histogram[r];
			lookupTable[r] = (int) (scale * ((double)sum));
		}
		
		return lookupTable;
	}
	
	public void createInverse()
	{
		inverses = new ArrayList<>();
		for (int[] lookupTable : lookupTables)
		{
			inverses.add(createInverseLookupTable(lookupTable));
		}
	}
	
	private static int[] createInverseLookupTable(int[] lookupTable)
	{
		Integer[] inverse = new Integer[lookupTable.length];
		for (int i : new Range(lookupTable.length))
		{
			inverse[lookupTable[i]] = i;
		}

		for (int pixelValue : new Range(lookupTable.length))
		{
			if (inverse[pixelValue] == null)
			{
				// Find the closest pixel value that is in the inverse map.
				int i = 0;
				Integer inverseValue = null;
				while (inverseValue == null)
				{
					i++;
					try
					{
						if (inverse[pixelValue + i] != null)
						{
							inverseValue = inverse[pixelValue + i];
							break;
						}
					}
					catch(IndexOutOfBoundsException e)
					{
						
					}
					try
					{
						if (inverse[pixelValue - i] != null)
						{
							inverseValue = inverse[pixelValue - i];
						}
					}
					catch(IndexOutOfBoundsException e)
					{
						
					}
				}
				inverse[pixelValue] = inverseValue;
			}
		}
		
		int[] result = new int[inverse.length];
		for (int i : new Range(result.length))
			result[i] = inverse[i];
		return result;
	}
	
	@SuppressWarnings("unused")
	private void writeToCSV(int[] histogram, String csvFileName)
	{
		StringBuilder exportStr = new StringBuilder();
		exportStr.append("Pixel Value, Frequency\n");
		int pixelMax = histogram.length - 1;
		for (int pixelVal = 0; pixelVal <= pixelMax; pixelVal++)
		{
			exportStr.append(pixelVal);
			exportStr.append(",");
			exportStr.append(histogram[pixelVal]);
			exportStr.append("\n");
		}
		Helper.writeToFile(csvFileName, exportStr.toString());
	}


	public BufferedImage equalize(BufferedImage inImage)
	{	
		return applyLookupTables(inImage, lookupTables);
	}
	
	public BufferedImage inverseEqualize(BufferedImage inImage)
	{	
		return applyLookupTables(inImage, inverses);
	}
	
	private BufferedImage applyLookupTables(BufferedImage inImage,List<int[]> lookupTables)
	{	
		int width = inImage.getWidth();
		int height = inImage.getHeight();
		BufferedImage outImage = new BufferedImage(width, height, imageType);

		// Note: There is a bug where if the target is gray and the source is color, 
		// the result has white spots. This doesn't happen if the target is color.
		
		WritableRaster in = inImage.getRaster();
		WritableRaster out = outImage.getRaster();

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if (lookupTables.size() == 1)
				{
					int grayLevel = in.getSample(x, y, 0);			
					out.setSample(x, y, 0, lookupTables.get(0)[grayLevel]);
				}
				else
				{
					Color inColor;
					if (inImage.getType() == BufferedImage.TYPE_BYTE_GRAY)
					{
						int grayLevel = in.getSample(x, y, 0);	
						inColor = new Color(grayLevel, grayLevel, grayLevel, 255);
					}
					else
					{
						inColor = new Color(inImage.getRGB(x, y));
					}
					int r = lookupTables.get(0)[inColor.getRed()];
					int g = lookupTables.get(1)[inColor.getGreen()];
					int b = lookupTables.get(2)[inColor.getBlue()];
					Color outColor = new Color(r, g, b, 255);
					outImage.setRGB(x, y, outColor.getRGB());

				}
			}
		}

		return outImage;

	}
	
	private static int[] countPixelLevels(BufferedImage image, int band)
	{
		
		WritableRaster raster = image.getRaster();
					
		// Create the list of pixels to use with the histogram.
		int bitsPerPixel = image.getColorModel().getComponentSize(0);
		int[] counts = new int[1 << bitsPerPixel];
		for (int r = 0; r < image.getHeight(); r++)
		{
			for (int c = 0; c < image.getWidth(); c++)
			{
				int pixelValue = raster.getSample(c, r, band);
				counts[pixelValue]++;
			}
		}
		
		return counts;
	}
	
	public static void main(String[] args) throws IOException
	{
		BufferedImage inImage = ImageIO.read(new File("mystery.png"));
		HistogramEqualizer equalizer = new HistogramEqualizer(inImage);

		BufferedImage outImage = equalizer.equalize(inImage);
		
		ImageIO.write(outImage, "png", new File("equalized.png"));
		System.out.println("Done.");
	}
	
	

}
