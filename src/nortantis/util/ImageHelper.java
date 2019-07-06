package nortantis.util;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.analysis.function.Sinc;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.jtransforms.fft.FloatFFT_2D;

import hoten.geom.Point;
import nortantis.ComplexArray;
import nortantis.DimensionDouble;
import nortantis.IconDrawer;
import pl.edu.icm.jlargearrays.ConcurrencyUtils;

public class ImageHelper
{
	/**
	 * This should be called before closing the program if methods have been
	 *  called which use jTransforms or other thread pools.
	 *  
	 *  For some reason this doesn't need to be called when running a GUI, and will throw an errror if you do.
	 */
	public static void shutdownThreadPool()
	{
		ConcurrencyUtils.shutdownThreadPoolAndAwaitTermination(); 
	}
		
	public static DimensionDouble fitDimensionsWithinBoundingBox(Dimension maxDimensions, double originalWidth, double originalHeight)
	{
		double width = originalWidth;
		double height = originalHeight;
		if (originalWidth > maxDimensions.getWidth())
		{
			width = maxDimensions.getWidth();
			height = height * (width / originalWidth);
		}
		if (height > maxDimensions.getHeight())
		{
			double prevHeight = height;
			height = maxDimensions.getHeight();
			width = width * (height / prevHeight);
		}
		return new DimensionDouble(width, height);
	}

	/**
	 * Converts a BufferedImage to type BufferedImage.TYPE_BYTE_GRAY.
	 * @param img
	 * @return
	 */
	public static BufferedImage convertToGrayscale(BufferedImage img)
	{
		return convertImageToType(img, BufferedImage.TYPE_BYTE_GRAY);
	}

	/**
	 * Converts a BufferedImage to type BufferedImage.TYPE_BYTE_GRAY.
	 * @param img
	 * @return
	 */
	public static BufferedImage convertImageToType(BufferedImage img, int bufferedImageType)
	{
		BufferedImage result = new BufferedImage(img.getWidth(),
				img.getHeight(), bufferedImageType);
		Graphics g = result.getGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();
		return result;
	}

	public static boolean isSupportedGrayscaleType(BufferedImage image)
	{
		return image.getType() == BufferedImage.TYPE_BYTE_BINARY
				|| image.getType() == BufferedImage.TYPE_BYTE_GRAY 
				|| image.getType() == BufferedImage.TYPE_USHORT_GRAY;
	}
	
	public static String bufferedImageTypeToString(int type)
	{
		if (type == BufferedImage.TYPE_3BYTE_BGR)
				return "TYPE_3BYTE_BGR";
		if (type == BufferedImage.TYPE_4BYTE_ABGR)
			return "TYPE_4BYTE_ABGR";
		if (type == BufferedImage.TYPE_4BYTE_ABGR_PRE)
			return "TYPE_4BYTE_ABGR_PRE";
		if (type == BufferedImage.TYPE_BYTE_BINARY)
			return  "TYPE_BYTE_BINARY";
		if (type == BufferedImage.TYPE_BYTE_GRAY)
			return  "TYPE_BYTE_GRAY";
		if (type == BufferedImage.TYPE_BYTE_INDEXED)
			return  "TYPE_BYTE_INDEXED";
		if (type == BufferedImage.TYPE_INT_RGB)
			return "TYPE_INT_RGB";
		if (type == BufferedImage.TYPE_INT_ARGB)
			return  "TYPE_INT_ARGB";
		if (type == BufferedImage.TYPE_INT_ARGB_PRE)
			return  "TYPE_INT_ARGB_PRE";
		if (type == BufferedImage.TYPE_INT_BGR)
			return  "TYPE_INT_BGR";
		if (type == BufferedImage.TYPE_USHORT_555_RGB)
			return  "TYPE_USHORT_555_RGB";
		if (type == BufferedImage.TYPE_USHORT_565_RGB)
			return  "TYPE_USHORT_565_RGB";
		if (type == BufferedImage.TYPE_USHORT_GRAY)
			return  "TYPE_USHORT_GRAY";
		return "unknown";
	}
	
	public static String bufferedImageTypeToString(BufferedImage image)
	{
		return bufferedImageTypeToString(image.getType());
	}

	/**
	 * Scales the given image, preserving aspect ratio.
	 */
	public static BufferedImage scaleByWidth(BufferedImage inImage, int xSize)
	{
		double aspectRatio = ((double) inImage.getHeight())
				/ inImage.getWidth();
		int ySize = (int) (xSize * aspectRatio);
		if (ySize == 0)
			ySize = 1;
		
		// This library is described at http://stackoverflow.com/questions/1087236/java-2d-image-resize-ignoring-bicubic-bilinear-interpolation-rendering-hints-os
		BufferedImage scaled = Scalr.resize(inImage, Method.QUALITY, xSize, ySize);

		if (isSupportedGrayscaleType(inImage) && !isSupportedGrayscaleType(scaled))
		{
			scaled = convertImageToType(scaled, inImage.getType());
		}

		return scaled;
	}
	

	/**
	 * Scales the given image, preserving aspect ratio.
	 */
	public static BufferedImage scaleByHeight(BufferedImage inImage, int ySize)
	{
		double aspectRatioInverse = ((double) inImage.getWidth())
				/ inImage.getHeight();
		int xSize = (int) (aspectRatioInverse * ySize);
		if (xSize == 0)
			xSize = 1;
		
		// This library is described at http://stackoverflow.com/questions/1087236/java-2d-image-resize-ignoring-bicubic-bilinear-interpolation-rendering-hints-os
		BufferedImage scaled = Scalr.resize(inImage, Method.QUALITY, xSize, ySize);
		
		if (isSupportedGrayscaleType(inImage) && !isSupportedGrayscaleType(scaled))
		{
			scaled = convertImageToType(scaled, inImage.getType());
		}
		
		return scaled;
	}
	
	public static BufferedImage scaleFastByHeightAndWidth(BufferedImage inImage, int xSize, int ySize)
	{
        BufferedImage scaledImage = new BufferedImage(xSize, ySize, inImage.getType());
        Graphics2D graphics2D = scaledImage.createGraphics();
        graphics2D.drawImage(inImage, 0, 0, xSize, ySize, null);
        graphics2D.dispose();
        return scaledImage;
	}

	/**
	 * 
	 * @param size Number of pixels from 3 standard deviations from one side of the Guassian to the other.
	 * @return
	 */
	public static float[][] createGaussianKernel(int size)
	{
		if (size == 0)
		{
			return new float[][] {{1f}};
		}
		
		// I want the edge of the kernel to be 3 standard deviations away from
		// the middle. I also divide by 2 to get half of the size (the length from center to edge).
		double sd = size / (2.0 * 3.0);
		NormalDistribution dist = new NormalDistribution(0, sd);
		int resultSize = (size * 2);

		float[][] kernel = new float[resultSize][resultSize];
		for (int x : new Range(resultSize))
		{
			double xDistanceFromCenter = Math.abs(resultSize / 2.0 - (x));
			for (int y : new Range(resultSize))
			{
				double yDistanceFromCenter = Math.abs(resultSize / 2.0 - (y));
				// Find the distance from the center (0,0).
				double distanceFromCenter = Math.sqrt(xDistanceFromCenter
						* xDistanceFromCenter + yDistanceFromCenter
						* yDistanceFromCenter);
				kernel[y][x] = (float) dist.density(distanceFromCenter);
			}
		}
		normalize(kernel);
		return kernel;
	}
	
	public static float[][] createFractalKernel(int size, double p)
	{
		if (size == 0)
		{
			return new float[][] {{1f}};
		}

		float[][] kernel = new float[size][size];
		for (int x : new Range(size))
		{
			double xDistanceFromCenter = Math.abs(size / 2.0 - x);
			for (int y : new Range(size))
			{
				double yDistanceFromCenter = Math.abs(size / 2.0 - y);
				// Find the distance from the center (0,0).
				double distanceFromCenter = Math.sqrt(xDistanceFromCenter
						* xDistanceFromCenter + yDistanceFromCenter
						* yDistanceFromCenter);
				kernel[y][x] = (float) (1.0/(Math.pow(distanceFromCenter, p)));
			}
		}
		normalize(kernel);
		return kernel;
	}

	public static float[][] createPositiveSincKernel(int size, double scale)
	{
		if (size == 0)
		{
			return new float[][] {{1f}};
		}

		Sinc dist = new Sinc();

		float[][] kernel = new float[size][size];
		for (int x : new Range(size))
		{
			double xDistanceFromCenter = Math.abs(size / 2.0 - x);
			for (int y : new Range(size))
			{
				double yDistanceFromCenter = Math.abs(size / 2.0 - y);
				// Find the distance from the center (0,0).
				double distanceFromCenter = Math.sqrt(xDistanceFromCenter
						* xDistanceFromCenter + yDistanceFromCenter
						* yDistanceFromCenter);
				kernel[y][x] = (float) dist.value(distanceFromCenter * scale);
				kernel[y][x] = Math.max(0, kernel[y][x]);
			}
		}
		normalize(kernel);
		return kernel;
	}
	
	/** 
	 * Maximizes the contrast of the given grayscale image. The image must be a supported grayscale type.
	 */
	public static void maximizeContrastGrayscale(BufferedImage image)
	{
		if (!isSupportedGrayscaleType(image))
			throw new IllegalArgumentException("Image type must a supported grayscale type, but was " + bufferedImageTypeToString(image));
		
		int maxPixelValue = getMaxPixelValue(image);

		Raster in = image.getRaster();
		double min = maxPixelValue;
		double max = 0;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				double value = in.getSample(x, y, 0);
				if (value < min)
					min = value;
				if (value > max)
					max = value;
			}
		WritableRaster out = image.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				double value = in.getSample(x, y, 0);
				int newValue = (int)(((value - min)/(max - min)) * maxPixelValue);
				out.setSample(x, y, 0, newValue);
			}
	}
	
	public static int getMaxPixelValue(BufferedImage image)
	{
		if (image.getType() == BufferedImage.TYPE_BYTE_BINARY)
		{
			return 1;
		}
		return (1 << image.getColorModel().getPixelSize()) - 1;
	}
	
	public static int getMaxPixelValue(int bufferedImageType)
	{
		if (bufferedImageType == BufferedImage.TYPE_USHORT_GRAY)
		{
			return 65535;
		}		
		else
		{
			return 255;
		}
	}

	/*
	 * Increases the contrast of array to make the min 0 and the max 1.
	 */
	public static void maximizeContrast(float[][] array)
	{
		setContrast(array, 0f, 1f);
	}

	/*
	 * Scales values in the given array such that the minimum is targetMin, and the maximum is targetMax.
	 */
	public static void setContrast(float[][] array, float targetMin, float targetMax)
	{
		setContrast(array, targetMin, targetMax, 0, array.length, 0, array[0].length);
	}
	
	public static void setContrast(float[][] array, float targetMin, float targetMax,
			int rowStart, int rows, int colStart, int cols)
	{
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{			
				float value = array[r][c];
				if (value < min)
					min = value;
				if (value > max)
					max = value;
			}
		}
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{			
				float value = array[r][c];
				array[r][c] = (((value - min)/(max - min))) * (targetMax - targetMin) + targetMin;
			}
		}
	}
	

	/** 
	 * Multiplies each pixel by the given scale. The image must be a supported grayscale type
	 */
	public static void scaleGrayLevels(BufferedImage image, float scale)
	{
		if (!isSupportedGrayscaleType(image))
			throw new IllegalArgumentException("Image type must a supported grayscale type, but was " + bufferedImageTypeToString(image));

		WritableRaster out = image.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				double value = out.getSample(x, y, 0);
				int newValue = (int)(value * scale);
				out.setSample(x, y, 0, newValue);
			}
	}

	/**
	 * Normalizes the kernel such that the sum of its elements is 1.
	 */
	public static void normalize(float[][] kernel)
	{
		float sum = 0;
		for (float[] row : kernel)
		{
			for (float f : row)
			{
				sum += f;
			}
		}

		for (int r : new Range(kernel.length))
		{
			for (int c : new Range(kernel[0].length))
			{
				kernel[r][c] /= sum;
			}
		}
	}
	
	public static BufferedImage maskWithImage(BufferedImage image1,
			BufferedImage image2, BufferedImage mask)
	{
		return maskWithImage(image1, image2, mask, null);
	}

	/**
	 * Each pixel in the resulting image is a linear combination of that pixel
	 * from image1 and from image2 using the gray levels in the given mask.
	 * The mask must be BufferedImage.TYPE_BYTE_GRAY.
	 * 
	 * @param region Specifies only a region to create rather than masking the entire images.
	 */
	public static BufferedImage maskWithImage(BufferedImage image1,
			BufferedImage image2, BufferedImage mask, IntRectangle region)
	{
		if (region == null)
		{
			region = new IntRectangle(0, 0, image1.getWidth(), image1.getHeight());
		}
		
		if (mask.getType() != BufferedImage.TYPE_BYTE_GRAY 
				&& mask.getType() != BufferedImage.TYPE_BYTE_BINARY)
			throw new IllegalArgumentException("mask type must be BufferedImage.TYPE_BYTE_GRAY"
					+ " or TYPE_BYTE_BINARY.");

		if (image1.getWidth() != image2.getWidth())
			throw new IllegalArgumentException();
		if (image1.getHeight() != image2.getHeight())
			throw new IllegalArgumentException();
		if (image1.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is "
					+ mask.getWidth() + " but image1 has width "
					+ image1.getWidth() + ".");
		if (image1.getHeight() != mask.getHeight())
			throw new IllegalArgumentException();
		if (!new IntRectangle(0, 0, image1.getWidth(), image2.getHeight()).contains(region))
		{
			throw new IllegalArgumentException("Region for masking is not contained within the source images.");
		}

		BufferedImage result = new BufferedImage((int)region.width,
				(int)region.height, image1.getType());
		Raster mRaster = mask.getRaster();
		for (int y = region.y; y < region.height; y++)
			for (int x = region.x; x < region.width; x++)
			{
				Color color1 = new Color(image1.getRGB(x, y));
				Color color2 = new Color(image2.getRGB(x, y));
				double maskLevel = ((double) mRaster.getSampleDouble(x + (int)region.x, y + (int)region.y, 0));
				if (mask.getType() == BufferedImage.TYPE_BYTE_GRAY)
					maskLevel /= 255.0;

				int r = (int) (maskLevel * color1.getRed() + (1.0 - maskLevel)
						* color2.getRed());
				int g = (int) (maskLevel * color1.getGreen() + (1.0 - maskLevel)
						* color2.getGreen());
				int b = (int) (maskLevel * color1.getBlue() + (1.0 - maskLevel)
						* color2.getBlue());
				int combined = (r << 16) | (g << 8) | b;
				result.setRGB(x, y, combined);
			}
		return result;
	}
	
	/**
	 * Equivalent to combining a solid color image with an image and a mask in
	 * maskWithImage(...) except this way is more efficient.
	 */
	public static BufferedImage maskWithColor(BufferedImage image,
			Color color, BufferedImage mask, boolean invertMask)
	{
		if (mask.getType() != BufferedImage.TYPE_BYTE_GRAY 
				&& mask.getType() != BufferedImage.TYPE_BYTE_BINARY)
			throw new IllegalArgumentException("mask type must be BufferedImage.TYPE_BYTE_GRAY.");

		if (image.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is "
					+ mask.getWidth() + " but image has width "
					+ image.getWidth() + ".");
		if (image.getHeight() != mask.getHeight())
			throw new IllegalArgumentException("In maskWithColor, image height was " + image.getHeight() 
			+ " but mask height was " + mask.getHeight());

		BufferedImage result = new BufferedImage(image.getWidth(),
				image.getHeight(), image.getType());
		Raster mRaster = mask.getRaster();
		Raster alphaRaster = image.getAlphaRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				Color col = new Color(image.getRGB(x, y));
				
				int maskLevel = mRaster.getSample(x, y, 0);
				if (mask.getType() == BufferedImage.TYPE_BYTE_GRAY)
				{
					if (invertMask)
						maskLevel = 255 - maskLevel;

					int r = ((maskLevel * col.getRed()) + (255 - maskLevel) * color.getRed())/255;
					int g = ((maskLevel * col.getGreen()) + (255 - maskLevel) * color.getGreen())/255;
					int b = ((maskLevel * col.getBlue()) + (255 - maskLevel) * color.getBlue())/255;
					Color combined = new Color(r,g,b,alphaRaster == null ? 0 : alphaRaster.getSample(x, y, 0));
					result.setRGB(x, y, combined.getRGB());				
				}
				else
				{
					// TYPE_BYTE_BINARY
	
					if (invertMask)
						maskLevel = 1 - maskLevel;

					int r = ((maskLevel * col.getRed()) + (1 - maskLevel) * color.getRed());
					int g = ((maskLevel * col.getGreen()) + (1 - maskLevel) * color.getGreen());
					int b = ((maskLevel * col.getBlue()) + (1 - maskLevel) * color.getBlue());
					Color combined = new Color(r,g,b,alphaRaster == null ? 0 : alphaRaster.getSample(x, y, 0));
					result.setRGB(x, y, combined.getRGB());					
				}
				
			}
		return result;
	}
	
	/**
	 * Like maskWithColor except multiple colors can be specified.
	 * @param colorIndexes Each pixel stores a gray level which (converted to an int) is an index into colors.
	 */
	public static BufferedImage maskWithMultipleColors(BufferedImage image,
			Color[] colors, BufferedImage colorIndexes, BufferedImage mask, boolean invertMask)
	{
		if (mask.getType() != BufferedImage.TYPE_BYTE_GRAY 
				&& mask.getType() != BufferedImage.TYPE_BYTE_BINARY)
			throw new IllegalArgumentException("mask type must be BufferedImage.TYPE_BYTE_GRAY or BufferedImage.TYPE_BYTE_BINARY.");
		if (colorIndexes.getType() != BufferedImage.TYPE_BYTE_GRAY)
			throw new IllegalArgumentException("colorIndexes type must be BufferedImage.TYPE_BYTE_GRAY.");

		if (image.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is "
					+ mask.getWidth() + " but image has width "
					+ image.getWidth() + ".");
		if (image.getHeight() != mask.getHeight())
			throw new IllegalArgumentException();

		BufferedImage result = new BufferedImage(image.getWidth(),
				image.getHeight(), image.getType());
		Raster mRaster = mask.getRaster();
		Raster colorIndexesRaster = colorIndexes.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				Color col = new Color(image.getRGB(x, y));
				Color color = colors[colorIndexesRaster.getSample(x, y, 0)];
				
				int maskLevel = mRaster.getSample(x, y, 0);
				if (mask.getType() == BufferedImage.TYPE_BYTE_GRAY)
				{
					if (invertMask)
						maskLevel = 255 - maskLevel;

					int r = ((maskLevel * col.getRed()) + (255 - maskLevel) * color.getRed())/255;
					int g = ((maskLevel * col.getGreen()) + (255 - maskLevel) * color.getGreen())/255;
					int b = ((maskLevel * col.getBlue()) + (255 - maskLevel) * color.getBlue())/255;
					int combined = (r << 16) | (g << 8) | b;
					result.setRGB(x, y, combined);					
				}
				else
				{
					// TYPE_BYTE_BINARY
	
					if (invertMask)
						maskLevel = 255 - maskLevel;

					int r = ((maskLevel * col.getRed()) + (1 - maskLevel) * color.getRed());
					int g = ((maskLevel * col.getGreen()) + (1 - maskLevel) * color.getGreen());
					int b = ((maskLevel * col.getBlue()) + (1 - maskLevel) * color.getBlue());
					int combined = (r << 16) | (g << 8) | b;
					result.setRGB(x, y, combined);					
				}
				
			}
		return result;
	}
		
	/**
	 * Creates a new BufferedImage in which the values of the given alphaMask to be the alpha channel in image.
	 * @param image
	 * @param alphaMask Must be type BufferedImage.TYPE_BYTE_GRAY. It must also be the
	 * same dimension as image.
	 * @param invertMask If true, the alpha values from alphaMask will be inverted.
	 * @return
	 */
	public static BufferedImage setAlphaFromMask(BufferedImage image, BufferedImage alphaMask,
			boolean invertMask)
	{
		if (alphaMask.getType() != BufferedImage.TYPE_BYTE_GRAY 
				&& alphaMask.getType() != BufferedImage.TYPE_BYTE_BINARY)
			throw new IllegalArgumentException("mask type must be BufferedImage.TYPE_BYTE_GRAY or TYPE_BYTE_BINARY");

		if (image.getWidth() != alphaMask.getWidth())
			throw new IllegalArgumentException("Mask width is "
					+ alphaMask.getWidth() + " but image has width "
					+ image.getWidth() + ".");
		if (image.getHeight() != alphaMask.getHeight())
			throw new IllegalArgumentException();

		BufferedImage result = new BufferedImage(image.getWidth(),
				image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Raster mRaster = alphaMask.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				Color col = new Color(image.getRGB(x, y));
				int maskLevel = mRaster.getSample(x, y, 0);
				if (alphaMask.getType() == BufferedImage.TYPE_BYTE_BINARY)
				{
					if (maskLevel == 1)
						maskLevel = 255;
				}
				if (invertMask)
					maskLevel = 255 - maskLevel;
				
				int mc = (maskLevel << 24) | 0x00ffffff;
	            int newColor = col.getRGB() & mc;
				
				result.setRGB(x, y, newColor);
			}
		return result;
	}
	
	public static BufferedImage createColoredImageFromGrayScaleImages(BufferedImage redChanel, BufferedImage greenChanel, BufferedImage blueChanel, BufferedImage alphaChanel)
	{
		if (redChanel.getType() != BufferedImage.TYPE_BYTE_GRAY)
			throw new IllegalArgumentException("Red chanel image type must be type BufferedImage.TYPE_BYTE_GRAY.");
		
		if (greenChanel.getType() != BufferedImage.TYPE_BYTE_GRAY )
			throw new IllegalArgumentException("Green chanel image type must be type BufferedImage.TYPE_BYTE_GRAY");
		
		if (blueChanel.getType() != BufferedImage.TYPE_BYTE_GRAY )
			throw new IllegalArgumentException("Blue chanel image type must be type BufferedImage.TYPE_BYTE_GRAY");
		
		if (alphaChanel.getType() != BufferedImage.TYPE_BYTE_GRAY )
			throw new IllegalArgumentException("Alpha chanel image type must be type BufferedImage.TYPE_BYTE_GRAY.");
		
		if (redChanel.getWidth() != alphaChanel.getWidth())
			throw new IllegalArgumentException("Alpha chanel width is "
					+ alphaChanel.getWidth() + " but red chanel image has width "
					+ redChanel.getWidth() + ".");
		if (redChanel.getHeight() != alphaChanel.getHeight())
			throw new IllegalArgumentException();

		if (greenChanel.getWidth() != alphaChanel.getWidth())
			throw new IllegalArgumentException("Alpha chanel width is "
					+ alphaChanel.getWidth() + " but green chanel image has width "
					+ greenChanel.getWidth() + ".");
		if (greenChanel.getHeight() != alphaChanel.getHeight())
			throw new IllegalArgumentException();
		
		if (blueChanel.getWidth() != alphaChanel.getWidth())
			throw new IllegalArgumentException("Alpha chanel width is "
					+ alphaChanel.getWidth() + " but blue chanel image has width "
					+ blueChanel.getWidth() + ".");
		if (blueChanel.getHeight() != alphaChanel.getHeight())
			throw new IllegalArgumentException();

		BufferedImage result = new BufferedImage(redChanel.getWidth(),
				redChanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Raster alphaRaster = alphaChanel.getRaster();
		for (int y = 0; y < redChanel.getHeight(); y++)
			for (int x = 0; x < redChanel.getWidth(); x++)
			{
				int red = new Color(redChanel.getRGB(x, y)).getRed();
				int green = new Color(greenChanel.getRGB(x, y)).getGreen();
				int blue = new Color(blueChanel.getRGB(x, y)).getBlue();
				
				int maskLevel = alphaRaster.getSample(x, y, 0);
				
				int mc = (maskLevel << 24) | 0x00ffffff;
	            int newColor = new Color(red, green, blue).getRGB() & mc;
				
				result.setRGB(x, y, newColor);
			}
		return result;
	}
	
	public static BufferedImage createWhiteTransparentImage(int width, int height)
	{
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
			{		
				int mc = (255 << 24) | 0x00ffffff;
	            int newColor = Color.white.getRGB() & mc;
	            
				result.setRGB(x, y, newColor);
			}
		return result;
	}

	
	/**
	 * Extracts the specified region from image2, then makes the given mask be the alpha channel
	 * of that extracted region, then draws the extracted region onto image1.
	 */
	public static void combineImagesWithMaskInRegion(BufferedImage image1, BufferedImage image2,
			BufferedImage mask,  int xLoc, int yLoc, double angle)
	{
		if (mask.getType() != BufferedImage.TYPE_BYTE_GRAY)
			throw new IllegalArgumentException("Expected mask to be type BufferedImage.TYPE_BYTE_GRAY.");
    	
    	if (image1.getWidth() != image2.getWidth())
    		throw new IllegalArgumentException();
       	if (image1.getHeight() != image2.getHeight())
    		throw new IllegalArgumentException();
       	
       	BufferedImage region = extractRotatedRegion(image2, xLoc, yLoc, mask.getWidth(), mask.getHeight(),
       			angle);
       	      	       	
		Raster maskRaster = mask.getRaster();
		for (int y = 0; y < region.getHeight(); y++)
			for (int x = 0; x < region.getWidth(); x++)
			{
				int grayLevel = maskRaster.getSample(x, y, 0);
				Color r = new Color(region.getRGB(x, y), true);
				int alphaLevel = Math.min(r.getAlpha(), grayLevel); // Don't clobber the alpha level from the region.
				// Only change the alpha channel of the region.
				region.setRGB(x, y, new Color(r.getRed(), r.getGreen(), r.getBlue(), alphaLevel).getRGB());
			}
		
		// This pivot must exactly match the one used in extractRotatedRegion above.
		Point pivot = new Point(xLoc + mask.getWidth()/2, yLoc + mask.getHeight()/2);
		
       	Graphics2D g1 = image1.createGraphics();
       	g1.rotate(angle, pivot.x, pivot.y);
      	g1.drawImage(region, xLoc, yLoc, null);
       	//g1.drawRect(xLoc, yLoc, region.getWidth(), region.getHeight());
       	
	}
	
	public static int getAlphaLevel(BufferedImage image, int x, int y)
	{
		return new Color(image.getRGB(x, y), true).getAlpha();
	}
	
	/**
	 * Warning: This adds an alpha channel, so the output image may not be the same type as the input image.
	 */
	public static BufferedImage extractRotatedRegion(BufferedImage image, int xLoc, int yLoc,
			int width, int height, double angle)
	{
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gResult = result.createGraphics();
		gResult.rotate(-angle, width/2, height/2);
		gResult.translate(-xLoc, -yLoc);		
		gResult.drawImage(image, 0, 0, null);
		      	
		return result;
	}
	
	/**
	 * 
	 * Creates a copy of a piece of an image. 
	 * 
	 * It is important the the result is a copy even if the desired region is exactly the input.
	 */
	public static BufferedImage extractRegion(BufferedImage image, int xLoc, int yLoc,
			int width, int height)
	{		
		BufferedImage result = new BufferedImage(width, height, image.getType());
		Graphics2D gResult = result.createGraphics();
		gResult.translate(-xLoc, -yLoc);		
		gResult.drawImage(image, 0, 0, null);
		      	
		return result;
	}

	/**
	 * Creates a rotated version of the input image 90 degrees either clockwise or counter-clockwise.
=	 */
	public static BufferedImage rotate90Degrees(BufferedImage image, boolean isClockwise)
	{
		BufferedImage result = new BufferedImage(image.getHeight(), image.getWidth(), image.getType());
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{		
				if (isClockwise)
				{
					result.setRGB(image.getHeight() - y - 1, image.getWidth() - x - 1, image.getRGB(x, y));					
				}
				else
				{
					result.setRGB(y, x, image.getRGB(x, y));
				}
			}
		}
		
		return result;
	}

	/**
	 * From
	 * http://stackoverflow.com/questions/13605248/java-converting-image-to-
	 * bufferedimage
	 * 
	 * Converts a given Image into a BufferedImage of the specified type.
	 * 
	 * @param img
	 *            The Image to be converted
	 * @return The converted BufferedImage
	 */
	public static BufferedImage convertToBufferedImageOfType(Image img, int bufferedImageType)
	{
		if (img instanceof BufferedImage && ((BufferedImage) img).getType() == bufferedImageType)
		{
			return (BufferedImage) img;
		}

		// Create a buffered image with transparency
		BufferedImage bImage = new BufferedImage(img.getWidth(null),
				img.getHeight(null), bufferedImageType);

		// Draw the image on to the buffered image
		Graphics2D bGr = bImage.createGraphics();
		bGr.drawImage(img, 0, 0, null);
		bGr.dispose();

		// Return the buffered image
		return bImage;
	}
	
	public static BufferedImage deepCopy(BufferedImage bi) 
	{
		ColorModel cm = bi.getColorModel();
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		WritableRaster raster = bi.copyData(null);
		return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}
	
	public static void multiplyArrays(float[][] target, float[][] source)
	{
		assert target.length == source.length;
		assert target[0].length == source[0].length;
		
		for (int r = 0; r < target.length; r++)
		{
			for (int c = 0; c < target[r].length; c++)
			{
				target[r][c] *= source[r][c];
			}
		}
	}
		
	/**
	 * Convolves a gray-scale image and with a kernel. The input image is unchanged.
	 * @param img
	 * @param kernel The kernel to convolve with.
	 * @param maximizeContrast Iff true, the contrast of the convolved image
	 * will be maximized while it is still in floating point representation.
	 * In the result the pixel values will range from 0 to 255 for 8 bit pixels, or 65535 for 16 bit.
	 * This is better than maximizing the contrast of the result because the result
	 * is a BufferedImage, which is more discretized.
	 * @return
	 */
	public static BufferedImage convolveGrayscale(BufferedImage img, float[][] kernel, boolean maximizeContrast)
	{	
		return convolveGrayscale(img, kernel, maximizeContrast, 0f, 1f);
	}
	
	public static BufferedImage convolveGrayscale(BufferedImage img, float[][] kernel, boolean setContrast, float contrastMin, float contrastMax)
	{
		int cols = getPowerOf2EqualOrLargerThan(Math.max(img.getWidth(), kernel[0].length));
		int rows = getPowerOf2EqualOrLargerThan(Math.max(img.getHeight(), kernel.length));
		// Make sure rows and cols are greater than 1 for JTransforms.
		if (cols < 2)
			cols = 2;
		if (rows < 2)
			rows = 2;
		
		ComplexArray data = forwardFFT(img, rows, cols);
		
		ComplexArray kernelData = forwardFFT(kernel, rows, cols, true);
						
		data.multiplyInPlace(kernelData);
		kernelData = null;
		
		// Do the inverse DFT on the product.
		inverseFFT(data);
		
		// Only use 16 bit pixels if the input image used them, to save memory.
		int resultType = img.getType() == BufferedImage.TYPE_USHORT_GRAY ? BufferedImage.TYPE_USHORT_GRAY : BufferedImage.TYPE_BYTE_GRAY;
		
		return realToImage(data, img.getWidth(), img.getHeight(), setContrast, contrastMin, contrastMax, resultType);
	}
		
	public static BufferedImage realToImage(ComplexArray data, int imageWidth, int imageHeight, boolean setContrast,  float contrastMin, float contrastMax, int bufferedImageType)
	{
		moveRealToLeftSide(data.getArrayJTransformsFormat());
		swapQuadrantsOfLeftSideInPlace(data.getArrayJTransformsFormat()); 
		
		int imgRowPaddingOver2 = (data.getHeight() - imageHeight)/2;
		int imgColPaddingOver2 = (data.getWidth() - imageWidth)/2;

		if (setContrast)
		{
			setContrast(data.getArrayJTransformsFormat(), contrastMin, contrastMax, imgRowPaddingOver2, imageHeight, imgColPaddingOver2, imageWidth);
		}
		
		BufferedImage result = arrayToImage(data.getArrayJTransformsFormat(), imgRowPaddingOver2, imageHeight, imgColPaddingOver2, imageWidth, bufferedImageType);
		return result;
	}
		
	public static void inverseFFT(ComplexArray data)
	{
		FloatFFT_2D fft = new FloatFFT_2D(data.getHeight(), data.getWidth());
		fft.complexInverse(data.getArrayJTransformsFormat(), true);		
	}
	
	public static ComplexArray forwardFFT(BufferedImage img, int rows, int cols)
	{
		ComplexArray data = new ComplexArray(cols, rows);
		
		int imgRowPadding = rows - img.getHeight();
		int imgColPadding = cols - img.getWidth();
		int imgRowPaddingOver2 = imgRowPadding/2;
		int imgColPaddingOver2 = imgColPadding/2;
		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);
		
		boolean isGrayscale = isSupportedGrayscaleType(img);
		float maxPixelValue = getMaxPixelValue(img);

		Raster raster = img.getRaster();
		for (int r = 0; r < img.getHeight(); r++)
			for (int c = 0; c < img.getWidth(); c++)
			{
				float grayLevel = raster.getSample(c, r, 0);
				if (isGrayscale)
					grayLevel /= maxPixelValue;
				data.setRealInput(c + imgColPaddingOver2, r + imgRowPaddingOver2, grayLevel);
			}

		// Do the forward FFT.
		fft.realForwardFull(data.getArrayJTransformsFormat());

		return data;
	}
	
	/**
	 * Do a 2D forward FFT.
	 * @param input 
	 * @param rows Number of rows in the output
	 * @param cols Number of columns in the output
	 * @param flipXAndYAxis For kernels. Flip the kernel along the x and y axis as I get the values from it. This is needed to do convolution instead of cross-correlation.
	 * @return
	 */
	public static ComplexArray forwardFFT(float[][] input, int rows, int cols, boolean flipXAndYAxis)
	{
		// Convert the kernel to the format required by JTransforms.
		ComplexArray data = new ComplexArray(cols, rows);
		{
			int rowPadding = rows - input.length;
			int rowPaddingOver2 = rowPadding/2;
			int colPadding = cols - input[0].length;
			int columnPaddingOver2 = colPadding/2;
			for (int r = 0; r < input.length; r++)
				for (int c = 0; c < input[0].length; c++)
				{
					if (flipXAndYAxis)
					{
						data.setRealInput(c + columnPaddingOver2, r + rowPaddingOver2, input[input.length - 1 - r][input[0].length - 1 - c]);
					}
					else
					{
						data.setRealInput(c + columnPaddingOver2, r + rowPaddingOver2, input[r][c]);
					}
				}	

			// Do the forward FFT.
			FloatFFT_2D fft = new FloatFFT_2D(rows, cols);
			fft.realForwardFull(data.getArrayJTransformsFormat());
		}
		return data;
	}
		
	public static void swapQuadrantsOfLeftSideInPlace(float[][] data)
	{
		int rows = data.length;
		int cols = data[0].length/2;
		for (int r = 0; r < rows/2; r++)
		{			
			for (int c = 0; c < cols; c++)
			{				
				if (c < cols/2)
				{
					float temp = data[r + rows/2][c + cols/2];
					data[r + rows/2][c + cols/2] = data[r][c];
					data[r][c] = temp;
				}
				else
				{
					float temp = data[r + rows/2][c - cols/2];
					data[r + rows/2][c - cols/2] = data[r][c];
					data[r][c] = temp;
				}
			}
		}		
	}

	
	public static void moveRealToLeftSide(float[][] data)
	{
		for (int r = 0; r < data.length; r++)
			for (int c = 0; c < data[0].length/2; c++)
			{
				data[r][c] = data[r][c*2];
			}
	}

	public static float[][] getRealPart(float[][] data)
	{
		float[][] result = new float[data.length][data[0].length/2];
		for (int r = 0; r < data.length; r++)
			for (int c = 0; c < data[0].length/2; c++)
			{
				result[r][c] = data[r][c*2];
			}
		return result;
	}

	public static float[][] getImaginaryPart(float[][] data)
	{
		float[][] result = new float[data.length][data[0].length/2];
		for (int r = 0; r < data.length; r++)
			for (int c = 0; c < data[0].length/2; c++)
			{
				result[r][c] = data[r][c*2 + 1];
			}
		return result;
	}

	public static BufferedImage arrayToImage(float[][] array, int rowStart, int rows, int colStart, int cols, int bufferedImageType)
	{
		BufferedImage image = new BufferedImage(cols, rows, bufferedImageType);
		WritableRaster raster = image.getRaster();
		int maxPixelValue = getMaxPixelValue(bufferedImageType);
		for (int r = rowStart; r < rowStart + rows; r++)
		{
			for (int c = colStart; c < colStart + cols; c++)
			{			
				float value = array[r][c] * maxPixelValue;
				raster.setSample(c - colStart, r - rowStart, 0, value);
			}
		}
		return image;
	}

	public static BufferedImage arrayToImage(float[][] array, int bufferedImageType)
	{
		BufferedImage image = new BufferedImage(array[0].length, array.length, bufferedImageType);
		WritableRaster raster = image.getRaster();
		int maxPixelValue = getMaxPixelValue(bufferedImageType);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{				
				raster.setSample(x, y, 0, array[y][x] * maxPixelValue);
			}
		}
		return image;
	}

	public static float[][] imageToArray(BufferedImage img)
	{
		float[][] result = new float[img.getWidth()][img.getHeight()];
		Raster raster = img.getRaster();
		for (int r = 0; r < img.getWidth(); r++)
		{
			for (int c = 0; c < img.getHeight(); c++)
			{				
				result[r][c] = raster.getSample(r, c, 0);
			}
		}
		return result;
	}

	public static double[][] convertToDoubleArray(float[][] array)
	{
		double[][] result = new double[array.length][array[0].length];
		for (int i : new Range(array.length))
			for (int j : new Range(array[0].length))
		{
			result[i][j] = array[i][j];
		}
		return result;
	}
	
	public static float[][] getLefHalf(float[][] array)
	{
		float[][] result = new float[array.length][array[0].length/2];
		for (int r = 0; r < result.length; r++)
		{
			for (int c = 0; c < result[0].length; c++)
			{				
				result[r][c] = array[r][c];
			}
		}
		return result;		
	}
	
	public static int getPowerOf2EqualOrLargerThan(int value)
	{
		return getPowerOf2EqualOrLargerThan((double) value);
	}
	
	public static int getPowerOf2EqualOrLargerThan(double value)
	{
		double logLength = Math.log(value)/Math.log(2.0);
		if (((int)logLength) == logLength )
		{
			return (int)value;
		}
		
		return (int)Math.pow(2.0, ((int)logLength) + 1.0);
	}

	
	public static float[][] genWhiteNoise(Random rand, int rows, int cols)
	{
		float[][] result = new float[rows][cols];
		for (int r : new Range(rows))
		{
			for (int c : new Range(cols))
			{
				result[r][c] = rand.nextFloat();
			}
		}
		return result;
	}
	
	public static float[][] tile(float[][] array, int targetRows, int targetCols, int rowOffset, int colOffset)
	{
		float[][] result = new float[targetRows][targetCols];
		for (int r = 0; r < result.length; r++)
			for (int c = 0; c < result[0].length; c++)
			{
				int arrayRow  = (r + rowOffset) % array.length;;
				if (((r + rowOffset) / array.length) % 2 == 1)
					arrayRow = array.length - 1 - arrayRow;
				
				int arrayCol = (c + colOffset) % array[0].length;
				if (((c + colOffset) / array[0].length) % 2 == 1)
					 arrayCol = array[0].length - 1 - arrayCol;
				
				result[r][c] = array[arrayRow][arrayCol];

			}
		
		return result;
	}
		
	public static BufferedImage tile(BufferedImage image, int targetRows, int targetCols)
	{
		return arrayToImage(tile(imageToArray(image), targetRows, targetCols, 0, 0), image.getType());
	}
	
	public static BufferedImage tileNTimes(BufferedImage image, int n)
	{
		return tile(image, image.getWidth() * n, image.getHeight() * n);
	}

	
	public static float[][] convertToTransformsInputFormat(float[][] transformReal, float[][] transformImaginary)
	{
		if (transformReal.length != transformImaginary.length)
			throw new IllegalArgumentException();
		if (transformReal[0].length != transformImaginary[0].length)
			throw new IllegalArgumentException();
		float[][] result = new float[transformReal.length][transformReal[0].length * 2];
		for (int r = 0; r < result.length; r++)
			for (int c = 0; c < result[0].length; c++)
			{
				result[r][c] = c % 2 == 0 ? transformReal[r][c/2] : transformImaginary[r][c/2]; 
			}
		return result;
	}
	
	/**
	 * Do histogram matching on an image.
	 * @param target The image to do histogram matching on.
	 * @param source The source of histogram information.
	 * @param resultType BufferedImage type of the result.
	 */
	public static BufferedImage matchHistogram(BufferedImage target, BufferedImage source, int resultType)
	{
		HistogramEqualizer targetEqualizer = new HistogramEqualizer(target);
		HistogramEqualizer sourceEqualizer = new HistogramEqualizer(source);
		sourceEqualizer.imageType = resultType;
		
		sourceEqualizer.createInverse();
						
		// Equalize the target.
		BufferedImage targetEqualized = targetEqualizer.equalize(target);
		
		// Apply the inverse map to the equalized target.
		BufferedImage outImage = sourceEqualizer.inverseEqualize(targetEqualized);
		
		return outImage;
		
	}
	
	public static BufferedImage matchHistogram(BufferedImage target, BufferedImage source)
	{
		return matchHistogram(target, source, target.getType());
	}
	
	public static BufferedImage colorify(BufferedImage image, Color color, ColorifyAlgorithm how)
	{
		if (how == ColorifyAlgorithm.none)
		{
			return image;
		}
		
		if (image.getType() != BufferedImage.TYPE_BYTE_GRAY)
			throw new IllegalArgumentException("The image must by type BufferedImage.TYPE_BYTE_GRAY, but was type "  
		+ bufferedImageTypeToString(image.getType()));
		BufferedImage result = new BufferedImage(image.getWidth(),
				image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Raster raster = image.getRaster();
		
		float[] hsb = new float[3];
		Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);

		for (int y = 0; y < result.getHeight(); y++)
			for (int x = 0; x < result.getWidth(); x++)
			{
				float level = raster.getSampleFloat(x, y, 0);
				result.setRGB(x, y, colorifyPixel(level, hsb, how));
			}
		
		return result;
	}
	
	private static int colorifyPixel(float pixelLevel, float[] hsb, ColorifyAlgorithm how)
	{	
		if (how == ColorifyAlgorithm.algorithm2)
		{
			float I = hsb[2] * 255f;
			float overlay = ((I/255f) * (I + ((2*pixelLevel)/255f) * (255f - I)))/255f;
			return Color.HSBtoRGB(hsb[0], hsb[1], overlay);
		}
		else if (how == ColorifyAlgorithm.algorithm3)
		{
			float resultLevel;
			pixelLevel /= 255f;
			if (hsb[2] < 0.5f)
			{	
				resultLevel = pixelLevel * (hsb[2] * 2f);
			}
			else
			{
				float range = (1f - hsb[2]) * 2;
				resultLevel = range * pixelLevel + (1f - range);
			}
			return Color.HSBtoRGB(hsb[0], hsb[1], resultLevel);
		}
		else if (how == ColorifyAlgorithm.none)
		{
			return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
		}
		else
		{
			throw new IllegalArgumentException("Unrecognize colorify algorithm.");
		}

	}
	
	public enum ColorifyAlgorithm
	{
		none,
		algorithm2,
		algorithm3  // algorithm3 preserves contrast a little better than algorithm2.
	}

	
	/**
	 * Like colorify but for multiple colors. Colorifies an image using a an array of colors and
	 * a second image which maps those colors to pixels. This way you can specify multiple colors for the resulting image.
	 * @param image The image to colorify
	 * @param colors Used as a map from region index (in politicalRegions) to region color. The 
	 * index of each color corresponds to a pixel level in pixelColors.
	 * @param colorIndexes Each pixel stores a gray level which (converted to an int) is an index into colors.
	 */
	public static BufferedImage colorifyMulti(BufferedImage image, Color[] colors,
			BufferedImage colorIndexes, ColorifyAlgorithm how)
	{
		if (image.getType() != BufferedImage.TYPE_BYTE_GRAY)
			throw new IllegalArgumentException("The image must by type BufferedImage.TYPE_BYTE_GRAY, but was type "  
					+ bufferedImageTypeToString(image.getType()));
		if (colorIndexes.getType() != BufferedImage.TYPE_BYTE_GRAY)
			throw new IllegalArgumentException("colorIndexes type must be BufferedImage.TYPE_BYTE_GRAY.");

		BufferedImage result = new BufferedImage(image.getWidth(),
				image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Raster raster = image.getRaster();
		Raster colorIndexesRaster = colorIndexes.getRaster();
		
		float[][] hsb = new float[colors.length][3];
		for (int i : new Range(colors.length))
		{
			Color.RGBtoHSB(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), hsb[i]);
		}
		
		for (int y = 0; y < result.getHeight(); y++)
			for (int x = 0; x < result.getWidth(); x++)
			{
				float level = raster.getSampleFloat(x, y, 0);
				int colorIndex = colorIndexesRaster.getSample(x, y, 0);
				
				result.setRGB(x, y, colorifyPixel(level, hsb[colorIndex], how));
			}
		
		return result;
	}
	
	public static Color colorFromHSB(float hue, float saturation, float brightness)
	{
		return new Color(Color.HSBtoRGB(hue/360f, saturation/255f, brightness/255f));
	}
	
	public static void write(BufferedImage image, String fileName)
	{
		try
		{
			ImageIO.write(image, FilenameUtils.getExtension(fileName), new File(fileName));
		} 
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public static BufferedImage read(String fileName)
	{
		try
		{
			return ImageIO.read(new File(fileName));
		} 
		catch (IOException e)
		{
			throw new RuntimeException("Can't read the file " + fileName);
		}
	}
	
	/***
	 * Opens an image in the system default image editor.
	 * @return The file name, in the system's temp folder.
	 */
	public static String openImageInSystemDefaultEditor(BufferedImage map, String filenameWithoutExtension) throws IOException
	{
		// Save the map to a file.
		String format = "png";
		File tempFile = File.createTempFile(filenameWithoutExtension, "." + format);
		ImageIO.write(map, format, tempFile);
		
		openImageInSystemDefaultEditor(tempFile.getPath());
		return tempFile.getAbsolutePath();
	}
	
	public static void openImageInSystemDefaultEditor(String imageFilePath)
	{
		// Attempt to open the map in the system's default image viewer.
		if (Desktop.isDesktopSupported())
		{
			Desktop desktop = Desktop.getDesktop();
			if (desktop.isSupported(Desktop.Action.OPEN))
			{
				try 
				{
					desktop.open(new File(imageFilePath));
				} 
				catch (IOException e) 
				{
					throw new RuntimeException(e);
				}
			}
		}
		else
		{
			throw new RuntimeException("Unable to open the map because java's Desktop is not supported");
		}
	}
	
	public static int bound(int value)
	{
		return Math.min(255, Math.max(0, value));
	}
	
	public static float calcMeanOfGrayscaleImage(BufferedImage image)
	{
		Raster raster = image.getRaster();
		long sum = 0;
		for (int r = 0; r < image.getHeight(); r++)
		{
			for (int c = 0; c < image.getWidth(); c++)
			{
				sum += raster.getSample(c, r, 0);
			}
		}
		
		return sum / ((float)(image.getHeight() * image.getWidth()));
	}
	
	public static float[] calcMeanOfEachColor(BufferedImage image)
	{
		float[] result = new float[3];
		for (int channel : new Range(3))
		{
			long sum = 0;
			for (int r = 0; r < image.getHeight(); r++)
			{
				for (int c = 0; c < image.getWidth(); c++)
				{
					Color color = new Color(image.getRGB(c, r));
					int level;
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
	
					sum += level;
				}
			}
			result[channel] = sum / ((float)(image.getHeight() * image.getWidth()));
		}
		
		return result;
	}
	
	public static BufferedImage flipOnXAxis(BufferedImage image)
	{
		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{		
				result.setRGB(image.getWidth() - x - 1, y, image.getRGB(x, y));					
			}
		}
		
		return result;
	}
	
	public static BufferedImage flipOnYAxis(BufferedImage image)
	{
		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{		
				result.setRGB(x, image.getHeight() - y - 1, image.getRGB(x, y));		
			}
		}
		
		return result;
	}
	
	public static BufferedImage blur(BufferedImage image, int blurLevel)
	{
		if (blurLevel == 0)
		{
			return image;
		}
		return ImageHelper.convolveGrayscale(image, ImageHelper.createGaussianKernel(blurLevel), false);
	}
	
	public static void threshold(BufferedImage image, int threshold)
	{
		int maxPixelValue = getMaxPixelValue(image);
		threshold(image, threshold, maxPixelValue);
	}
	
	/**
	 * Thresholds an image in-place
	 * @param image Input and output image.
	 * @param threshold Pixel values equal to or greater than this value will be set to highValue. Pixel values lower than this will be set to 0.
	 * @param highValue Value pixels will be set to if thresholded high.
	 */
	public static void threshold(BufferedImage image, int threshold, int highValue)
	{
		if (!isSupportedGrayscaleType(image))
		{
			throw new IllegalArgumentException("Unsupported image type for thresholding: " + bufferedImageTypeToString(image.getType()));
		}

		int maxPixelValue = getMaxPixelValue(image);
		WritableRaster out = image.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				double value = (int) out.getSample(x, y, 0);
				if (value * maxPixelValue >= threshold)
				{
					out.setSample(x, y, 0, highValue);
				}
				else
				{
					out.setSample(x, y, 0, 0);
				}
			}
	}
	
	public static void add(BufferedImage target, BufferedImage other)
	{
		if (!isSupportedGrayscaleType(target))
		{
			throw new IllegalArgumentException("Unsupported target image type for target: " + bufferedImageTypeToString(target.getType()));
		}
		if (!isSupportedGrayscaleType(other))
		{
			throw new IllegalArgumentException("Unsupported other image type for target: " + bufferedImageTypeToString(other.getType()));
		}
		
		int maxPixelValue = getMaxPixelValue(target);
		WritableRaster out = target.getRaster();
		Raster otherRaster = other.getRaster();
		for (int y = 0; y < target.getHeight(); y++)
			for (int x = 0; x < target.getWidth(); x++)
			{
				double value = (int) out.getSample(x, y, 0);
				double otherValue = (int) otherRaster.getSample(x, y, 0);
				out.setSample(x, y, 0, Math.min(maxPixelValue, value + otherValue));
			}
	}
	
	public static void subtract(BufferedImage target, BufferedImage other)
	{
		if (!isSupportedGrayscaleType(target))
		{
			throw new IllegalArgumentException("Unsupported target image type for subtracting: " + bufferedImageTypeToString(target.getType()));
		}
		
		if (!isSupportedGrayscaleType(other))
		{
			throw new IllegalArgumentException("Unsupported other image type for subtracting: " + bufferedImageTypeToString(other.getType()));
		}

		WritableRaster out = target.getRaster();
		Raster otherRaster = other.getRaster();
		for (int y = 0; y < target.getHeight(); y++)
			for (int x = 0; x < target.getWidth(); x++)
			{
				double value = (int) out.getSample(x, y, 0);
				double otherValue = (int) otherRaster.getSample(x, y, 0);
				out.setSample(x, y, 0, Math.max(0, value - otherValue));
			}
	}
	
}
