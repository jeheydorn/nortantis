package nortantis.util;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;

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
		if (ImageHelper.isSupportedGrayscaleType(image))
		{
			int[] histogram = countPixelLevels(image, 0);
			lookupTables.add(createLookupTable(histogram, image.getWidth() * image.getHeight()));
		}
		else
		{
			for (int band : new Range(3))
			{
				int[] histogram = countPixelLevels(image, band);
				int[] lookupTable = createLookupTable(histogram, image.getWidth() * image.getHeight());
				lookupTables.add(lookupTable);
			}
		}
	}

	private static int[] createLookupTable(int[] histogram, int imageArea)
	{
		int sum = 0;
		int[] lookupTable = new int[histogram.length];
		double scale = (lookupTable.length - 1)/(double)imageArea;
		for (int r = 0; r < lookupTable.length; r++)
		{
			sum += histogram[r];
			lookupTable[r] = (int) (scale * sum);
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
		Float[] inverse = new Float[lookupTable.length];
		int[] inverseCounts = new int[inverse.length];
		for (int i : new Range(lookupTable.length))
		{
			// Ignore 0 and the max value to avoid random pixels which are very light or very dark.
			if (lookupTable[i] != 0 && lookupTable[i] != lookupTable.length - 1)
			{
				if (inverse[lookupTable[i]] == null)
				{
					inverse[lookupTable[i]] = (float)i;
				}
				else
				{
					// Do a running average of all levels that map to the save value in the inverse.
					inverse[lookupTable[i]] = (inverse[lookupTable[i]] * inverseCounts[lookupTable[i]] + i) / (inverseCounts[lookupTable[i]] + 1);
				}
				inverseCounts[lookupTable[i]]++;
			}
		}

		// Interpolate values which are null in the inverse mapping.
		for (int pixelValue : new Range(lookupTable.length))
		{
			if (inverse[pixelValue] == null)
			{
				Float higher = findFirstNonNullValueAbove(inverse, pixelValue);
				Float lower = findFirstNonNullValueBelow(inverse, pixelValue);
				
				if (higher == null)
				{
					// I'm not worried about lower being null because that would mean the original image had no pixel levels.
					inverse[pixelValue] = lower;
				}
				else if (lower == null)
				{
					inverse[pixelValue] = higher;
				}
				else
				{
					inverse[pixelValue] = (lower + higher) / 2f;
				}
			}
		}
		
		int[] result = new int[inverse.length];
		for (int i : new Range(result.length))
		{
			if (inverse[i] != null)
			{
				result[i] = (int)(float)inverse[i];
			}
			else
			{
				// Happened when the image had only 1 color level.
				result[i] = lookupTable[0];
			}
		}
		return result;
	}
	
	private static Float findFirstNonNullValueAbove(Float[] inverse, int start)
	{
		if (start == inverse.length)
			return null;
		
		for (int i = start + 1; i < inverse.length; i++)
		{
			if (inverse[i] != null)
			{
				return inverse[i];
			}
		}
		return null;
	}

	private static Float findFirstNonNullValueBelow(Float[] inverse, int start)
	{
		if (start == 0)
			return null;
		
		for (int i = start - 1; i >= 0; i--)
		{
			if (inverse[i] != null)
			{
				return inverse[i];
			}
		}
		return null;
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
					if (ImageHelper.isSupportedGrayscaleType(inImage))
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

	

}
