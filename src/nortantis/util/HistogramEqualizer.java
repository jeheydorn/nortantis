package nortantis.util;

import java.util.ArrayList;
import java.util.List;

import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;

/**
 * Performs histogram equalization on images.
 * 
 * @author joseph
 *
 */

public class HistogramEqualizer
{
	/**
	 * lookupTable maps grayscale levels to grayscale levels. The index used is the function input to look up.
	 */
	List<int[]> lookupTables;
	List<int[]> inverses;
	ImageType imageType;

	public HistogramEqualizer(Image image)
	{
		this.imageType = image.getType();
		lookupTables = new ArrayList<>();
		if (image.isGrayscaleOrBinary())
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
		double scale = (lookupTable.length - 1) / (double) imageArea;
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
					inverse[lookupTable[i]] = (float) i;
				}
				else
				{
					// Do a running average of all levels that map to the save value in the inverse.
					inverse[lookupTable[i]] = (inverse[lookupTable[i]] * inverseCounts[lookupTable[i]] + i)
							/ (inverseCounts[lookupTable[i]] + 1);
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
				result[i] = (int) (float) inverse[i];
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
		FileHelper.writeToFile(csvFileName, exportStr.toString());
	}

	public Image equalize(Image inImage)
	{
		return applyLookupTables(inImage, lookupTables);
	}

	public Image inverseEqualize(Image inImage)
	{
		return applyLookupTables(inImage, inverses);
	}

	private Image applyLookupTables(Image inImage, List<int[]> lookupTables)
	{
		int width = inImage.getWidth();
		int height = inImage.getHeight();
		Image outImage = Image.create(width, height, imageType);

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if (lookupTables.size() == 1)
				{
					int grayLevel = inImage.getGrayLevel(x, y);
					outImage.setGrayLevel(x, y, lookupTables.get(0)[grayLevel]);
				}
				else
				{
					Color inColor;
					if (inImage.isGrayscaleOrBinary())
					{
						int grayLevel = inImage.getGrayLevel(x, y);
						inColor = Color.create(grayLevel, grayLevel, grayLevel, 255);
					}
					else
					{
						inColor = Color.create(inImage.getRGB(x, y));
					}
					int r = lookupTables.get(0)[inColor.getRed()];
					int g = lookupTables.get(1)[inColor.getGreen()];
					int b = lookupTables.get(2)[inColor.getBlue()];
					Color outColor = Color.create(r, g, b, 255);
					outImage.setRGB(x, y, outColor.getRGB());
				}
			}
		}

		return outImage;

	}

	private static int[] countPixelLevels(Image image, int band)
	{

		// Create the list of pixels to use with the histogram.
		int[] counts = new int[image.getMaxPixelLevel() + 1];
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int pixelValue = image.getBandLevel(x, y, band);
				counts[pixelValue]++;
			}
		}

		return counts;
	}

}
