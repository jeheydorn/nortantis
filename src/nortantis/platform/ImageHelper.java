package nortantis.platform;

import nortantis.ComplexArray;
import nortantis.MapSettings;
import nortantis.TextDrawer;
import nortantis.WorldGraph;
import nortantis.geom.*;
import nortantis.geom.Dimension;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.util.Helper;
import nortantis.util.Range;
import nortantis.util.ThreadHelper;
import nortantis.util.Tuple2;
import org.apache.commons.math3.analysis.function.Sinc;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.imgscalr.Scalr.Method;
import org.jtransforms.fft.FloatFFT_2D;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public abstract class ImageHelper
{
	public static final int minParallelRowCount = 128;
	public static final int minParallelSize = minParallelRowCount * minParallelRowCount;

	private static ImageHelper instance;

	public static void setInstance(ImageHelper inst)
	{
		instance = inst;
	}

	public static ImageHelper getInstance()
	{
		return instance;
	}

	// =====================================================================
	// The 10 overridable methods: static method delegates to instance
	// =====================================================================

	public static Image convertToGrayscale(Image img)
	{
		if (instance != null)
		{
			return instance.doConvertToGrayscale(img);
		}
		return convertImageToType(img, ImageType.Grayscale8Bit);
	}

	protected Image doConvertToGrayscale(Image img)
	{
		return convertImageToType(img, ImageType.Grayscale8Bit);
	}

	public static Image maskWithImage(Image image1, Image image2, Image mask)
	{
		if (mask.getType() != ImageType.Grayscale8Bit && mask.getType() != ImageType.Binary)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale" + " or TYPE_BYTE_BINARY.");

		if (image1.getWidth() != image2.getWidth())
			throw new IllegalArgumentException();
		if (image1.getHeight() != image2.getHeight())
			throw new IllegalArgumentException();

		if (image1.getType() != ImageType.RGB && image1.getType() != ImageType.ARGB)
		{
			throw new IllegalArgumentException("Image 1 must be type " + ImageType.RGB + " or " + ImageType.ARGB + ", but was type " + image1.getType() + ".");
		}
		if (image2.getType() != ImageType.RGB && image2.getType() != ImageType.ARGB)
		{
			throw new IllegalArgumentException("Image 2 must be type " + ImageType.RGB + " or " + ImageType.ARGB + ", but was type " + image2.getType() + ".");
		}

		if (instance != null)
		{
			return instance.doMaskWithImage(image1, image2, mask);
		}
		return doMaskWithImageCPU(image1, image2, mask);
	}

	protected Image doMaskWithImage(Image image1, Image image2, Image mask)
	{
		return doMaskWithImageCPU(image1, image2, mask);
	}

	private static Image doMaskWithImageCPU(Image image1, Image image2, Image mask)
	{
		ImageType resultType;
		if (image1.hasAlpha() || image2.hasAlpha())
		{
			resultType = ImageType.ARGB;
		}
		else
		{
			resultType = image1.getType();
		}
		Image result = Image.create(image1.getWidth(), image1.getHeight(), resultType);

		IntRectangle image1Bounds = new IntRectangle(0, 0, image1.getWidth(), image1.getHeight());

		int numTasks = ThreadHelper.getInstance().getThreadCount();
		List<Runnable> tasks = new ArrayList<>(numTasks);
		int rowsPerJob = mask.getHeight() / numTasks;

		try (PixelReader image1Pixels = image1.createPixelReader();
				PixelReader image2Pixels = image2.createPixelReader();
				PixelReader maskPixels = mask.createPixelReader();
				PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int taskNumber : new Range(numTasks))
			{
				tasks.add(() ->
				{
					int endY = taskNumber == numTasks - 1 ? mask.getHeight() : ((taskNumber + 1) * rowsPerJob);
					for (int y = (taskNumber * rowsPerJob); y < endY; y++)
						for (int x = 0; x < mask.getWidth(); x++)
						{
							if (!image1Bounds.contains(x, y))
							{
								continue;
							}

							Color color1 = Color.create(image1Pixels.getRGB(x, y), image1.hasAlpha());
							Color color2 = Color.create(image2Pixels.getRGB(x, y), image2.hasAlpha());
							double maskLevel = maskPixels.getNormalizedPixelLevel(x, y);

							int r = (int) (maskLevel * color1.getRed() + (1.0 - maskLevel) * color2.getRed());
							int g = (int) (maskLevel * color1.getGreen() + (1.0 - maskLevel) * color2.getGreen());
							int b = (int) (maskLevel * color1.getBlue() + (1.0 - maskLevel) * color2.getBlue());
							int a = (int) (maskLevel * color1.getAlpha() + (1.0 - maskLevel) * color2.getAlpha());
							resultPixels.setRGB(x, y, r, g, b, a);
						}
				});
			}

			if (mask.getPixelCount() < minParallelSize)
			{
				ThreadHelper.getInstance().processSerial(tasks);
			}
			else
			{
				ThreadHelper.getInstance().processInParallel(tasks, true);
			}
		}

		return result;
	}

	public static Image maskWithColor(Image image, Color color, Image mask, boolean invertMask)
	{
		if (image.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is " + mask.getWidth() + " but image has width " + image.getWidth() + ".");
		if (image.getHeight() != mask.getHeight())
			throw new IllegalArgumentException("In maskWithColor, image height was " + image.getHeight() + " but mask height was " + mask.getHeight());

		if (instance != null)
		{
			return instance.doMaskWithColor(image, color, mask, invertMask);
		}
		return maskWithColorInRegion(image, color, mask, invertMask, new IntPoint(0, 0));
	}

	protected Image doMaskWithColor(Image image, Color color, Image mask, boolean invertMask)
	{
		return maskWithColorInRegion(image, color, mask, invertMask, new IntPoint(0, 0));
	}

	public static Image maskWithMultipleColors(Image image, Map<Integer, Color> colors, Image colorIndexes, Image mask, boolean invertMask)
	{
		if (mask.getType() != ImageType.Grayscale8Bit && mask.getType() != ImageType.Binary)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale or ImageType.Binary.");
		if (colorIndexes.getType() != ImageType.RGB)
			throw new IllegalArgumentException("colorIndexes type must be type RGB.");

		if (image.getWidth() != mask.getWidth())
			throw new IllegalArgumentException("Mask width is " + mask.getWidth() + " but image has width " + image.getWidth() + ".");
		if (image.getHeight() != mask.getHeight())
			throw new IllegalArgumentException();

		if (instance != null)
		{
			return instance.doMaskWithMultipleColors(image, colors, colorIndexes, mask, invertMask);
		}
		return doMaskWithMultipleColorsCPU(image, colors, colorIndexes, mask, invertMask);
	}

	protected Image doMaskWithMultipleColors(Image image, Map<Integer, Color> colors, Image colorIndexes, Image mask, boolean invertMask)
	{
		return doMaskWithMultipleColorsCPU(image, colors, colorIndexes, mask, invertMask);
	}

	private static Image doMaskWithMultipleColorsCPU(Image image, Map<Integer, Color> colors, Image colorIndexes, Image mask,
			boolean invertMask)
	{
		Image result = Image.create(image.getWidth(), image.getHeight(), image.getType());

		int numTasks = ThreadHelper.getInstance().getThreadCount();
		List<Runnable> tasks = new ArrayList<>(numTasks);
		int rowsPerJob = image.getHeight() / numTasks;
		try (PixelReader imagePixels = image.createPixelReader();
				PixelReader colorIndexesPixels = colorIndexes.createPixelReader();
				PixelReader maskPixels = mask.createPixelReader();
				PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int taskNumber : new Range(numTasks))
			{
				tasks.add(() ->
				{
					int endY = taskNumber == numTasks - 1 ? image.getHeight() : (taskNumber + 1) * rowsPerJob;
					for (int y = taskNumber * rowsPerJob; y < endY; y++)
					{
						for (int x = 0; x < image.getWidth(); x++)
						{
							Color col = Color.create(imagePixels.getRGB(x, y));
							Color color = colors.get(WorldGraph.getValueFromColor(colorIndexesPixels.getPixelColor(x, y)));
							if (color != null)
							{
								int maskLevel = maskPixels.getGrayLevel(x, y);
								if (mask.getType() == ImageType.Grayscale8Bit)
								{
									if (invertMask)
										maskLevel = 255 - maskLevel;

									int r = ((maskLevel * col.getRed()) + (255 - maskLevel) * color.getRed()) / 255;
									int g = ((maskLevel * col.getGreen()) + (255 - maskLevel) * color.getGreen()) / 255;
									int b = ((maskLevel * col.getBlue()) + (255 - maskLevel) * color.getBlue()) / 255;
									resultPixels.setRGB(x, y, r, g, b);
								}
								else
								{
									// TYPE_BYTE_BINARY

									if (invertMask)
										maskLevel = 255 - maskLevel;

									int r = ((maskLevel * col.getRed()) + (1 - maskLevel) * color.getRed());
									int g = ((maskLevel * col.getGreen()) + (1 - maskLevel) * color.getGreen());
									int b = ((maskLevel * col.getBlue()) + (1 - maskLevel) * color.getBlue());
									resultPixels.setRGB(x, y, r, g, b);
								}
							}
						}
					}
				});
			}
			ThreadHelper.getInstance().processInParallel(tasks, true);
		}

		return result;
	}

	public static Image setAlphaFromMask(Image image, Image alphaMask, boolean invertMask)
	{
		if (image.getWidth() != alphaMask.getWidth())
			throw new IllegalArgumentException("Mask width is " + alphaMask.getWidth() + " but image has width " + image.getWidth() + ".");
		if (image.getHeight() != alphaMask.getHeight())
			throw new IllegalArgumentException();

		if (instance != null)
		{
			return instance.doSetAlphaFromMask(image, alphaMask, invertMask);
		}
		return setAlphaFromMaskInRegion(image, alphaMask, invertMask, new IntPoint(0, 0));
	}

	protected Image doSetAlphaFromMask(Image image, Image alphaMask, boolean invertMask)
	{
		return setAlphaFromMaskInRegion(image, alphaMask, invertMask, new IntPoint(0, 0));
	}

	public static Image copyAlphaTo(Image target, Image alphaSource)
	{
		if (alphaSource.getType() != ImageType.ARGB)
		{
			throw new IllegalArgumentException("alphaSource is not a supported type");
		}

		if (!target.size().equals(alphaSource.size()))
		{
			throw new IllegalArgumentException("target and alphaSource are different sizes.");
		}

		if (instance != null)
		{
			return instance.doCopyAlphaTo(target, alphaSource);
		}
		return doCopyAlphaToCPU(target, alphaSource);
	}

	protected Image doCopyAlphaTo(Image target, Image alphaSource)
	{
		return doCopyAlphaToCPU(target, alphaSource);
	}

	private static Image doCopyAlphaToCPU(Image target, Image alphaSource)
	{
		Image result = Image.create(target.getWidth(), target.getHeight(), ImageType.ARGB);
		try (PixelReader alphaSourcePixels = alphaSource.createPixelReader(); PixelReader targetPixels = target.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int y = 0; y < target.getHeight(); y++)
				for (int x = 0; x < target.getWidth(); x++)
				{

					int alphaLevel = alphaSourcePixels.getAlpha(x, y);
					Color originalColor = targetPixels.getPixelColor(x, y);
					resultPixels.setPixelColor(x, y, Color.create(originalColor.getRed(), originalColor.getGreen(), originalColor.getBlue(), alphaLevel));
				}
		}
		return result;
	}

	public static Image colorify(Image image, Color color, ColorifyAlgorithm how)
	{
		return colorify(image, color, how, false);
	}

	public static Image colorify(Image image, Color color, ColorifyAlgorithm how, boolean forceAddAlpha)
	{
		if (how == ColorifyAlgorithm.none)
		{
			return image;
		}

		if (image.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("The image must by type ImageType.Grayscale, but was type " + image.getType());

		if (instance != null)
		{
			return instance.doColorify(image, color, how, forceAddAlpha);
		}
		return doColorifyCPU(image, color, how, forceAddAlpha);
	}

	protected Image doColorify(Image image, Color color, ColorifyAlgorithm how, boolean forceAddAlpha)
	{
		return doColorifyCPU(image, color, how, forceAddAlpha);
	}

	private static Image doColorifyCPU(Image image, Color color, ColorifyAlgorithm how, boolean forceAddAlpha)
	{
		ImageType resultType = forceAddAlpha || color.hasTransparency() ? ImageType.ARGB : ImageType.RGB;
		Image result = Image.create(image.getWidth(), image.getHeight(), resultType);

		float[] hsb = color.getHSB();
		try (PixelReader imagePixels = image.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			if (resultType == ImageType.ARGB)
			{
				int alpha = color.getAlpha();
				ThreadHelper.getInstance().processRowsInParallel(0, image.getHeight(), (y) ->
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						float level = imagePixels.getNormalizedPixelLevel(x, y);
						int rgb = colorifyPixel(level, hsb, how);
						Color resultColor = Color.create(rgb, false);
						int r = resultColor.getRed();
						int g = resultColor.getGreen();
						int b = resultColor.getBlue();
						resultPixels.setRGB(x, y, r, g, b, alpha);
					}
				});
			}
			else
			{
				ThreadHelper.getInstance().processRowsInParallel(0, image.getHeight(), (y) ->
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						float level = imagePixels.getNormalizedPixelLevel(x, y);
						resultPixels.setRGB(x, y, colorifyPixel(level, hsb, how));
					}
				});
			}
		}

		return result;
	}

	public static Image colorifyMulti(Image image, Map<Integer, Color> colorMap, Image colorIndexes, ColorifyAlgorithm how, IntPoint where)
	{
		if (image.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("The image must by type ImageType.Grayscale, but was type " + image.getType());
		if (colorIndexes.getType() != ImageType.RGB)
			throw new IllegalArgumentException("colorIndexes type must be type RGB, but was type " + colorIndexes.getType());

		// Extract sub-image if 'where' is specified
		Image imageToUse = image;
		if (where != null)
		{
			imageToUse = image.copySubImage(new IntRectangle(where, colorIndexes.getWidth(), colorIndexes.getHeight()));
		}

		if (instance != null)
		{
			return instance.doColorifyMulti(imageToUse, colorMap, colorIndexes, how, where);
		}
		return doColorifyMultiCPU(imageToUse, colorMap, colorIndexes, how);
	}

	protected Image doColorifyMulti(Image imageToUse, Map<Integer, Color> colorMap, Image colorIndexes, ColorifyAlgorithm how, IntPoint where)
	{
		return doColorifyMultiCPU(imageToUse, colorMap, colorIndexes, how);
	}

	private static Image doColorifyMultiCPU(Image imageToUse, Map<Integer, Color> colorMap, Image colorIndexes, ColorifyAlgorithm how)
	{
		Image result = Image.create(colorIndexes.getWidth(), colorIndexes.getHeight(), ImageType.RGB);

		Map<Integer, float[]> hsbMap = new HashMap<>();

		for (int regionId : colorMap.keySet())
		{
			Color color = colorMap.get(regionId);
			float[] hsb = color.getHSB();
			hsbMap.put(regionId, hsb);
		}

		Image imageToUseFinal = imageToUse;
		try (PixelReader imagePixels = imageToUseFinal.createPixelReader(); PixelReader colorIndexesPixels = colorIndexes.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			ThreadHelper.getInstance().processRowsInParallel(0, colorIndexes.getHeight(), (y) ->
			{
				for (int x = 0; x < colorIndexes.getWidth(); x++)
				{
					float level = imagePixels.getNormalizedPixelLevel(x, y);
					int colorKey = WorldGraph.getValueFromColor(colorIndexesPixels.getPixelColor(x, y));
					float[] hsb = hsbMap.get(colorKey);
					if (hsb != null)
					{
						resultPixels.setRGB(x, y, colorifyPixel(level, hsb, how));
					}
				}
			});
		}

		return result;
	}

	public static Image blur(Image image, int blurLevel, boolean maximizeContrast, boolean padImageToAvoidWrapping)
	{
		if (blurLevel == 0)
		{
			return image;
		}

		if (instance != null)
		{
			return instance.doBlur(image, blurLevel, maximizeContrast, padImageToAvoidWrapping);
		}
		return ImageHelper.convolveGrayscale(image, ImageHelper.createGaussianKernel(blurLevel), maximizeContrast, padImageToAvoidWrapping);
	}

	protected Image doBlur(Image image, int blurLevel, boolean maximizeContrast, boolean padImageToAvoidWrapping)
	{
		return ImageHelper.convolveGrayscale(image, ImageHelper.createGaussianKernel(blurLevel), maximizeContrast, padImageToAvoidWrapping);
	}

	public static Image blurAndScale(Image image, int blurLevel, float scale, boolean padImageToAvoidWrapping)
	{
		if (blurLevel == 0)
		{
			return image;
		}

		if (instance != null)
		{
			return instance.doBlurAndScale(image, blurLevel, scale, padImageToAvoidWrapping);
		}
		return ImageHelper.convolveGrayscaleThenScale(image, ImageHelper.createGaussianKernel(blurLevel), scale, padImageToAvoidWrapping);
	}

	protected Image doBlurAndScale(Image image, int blurLevel, float scale, boolean padImageToAvoidWrapping)
	{
		return ImageHelper.convolveGrayscaleThenScale(image, ImageHelper.createGaussianKernel(blurLevel), scale, padImageToAvoidWrapping);
	}

	// =====================================================================
	// Static utility methods below (unchanged)
	// =====================================================================

	private static int colorifyPixel(float pixelLevelNormalized, float[] hsb, ColorifyAlgorithm how)
	{
		if (how == ColorifyAlgorithm.algorithm2)
		{
			float I = hsb[2] * 255f;
			float overlay = ((I / 255f) * (I + (2 * pixelLevelNormalized) * (255f - I))) / 255f;
			return Color.createFromHSB(hsb[0], hsb[1], overlay).getRGB();
		}
		else if (how == ColorifyAlgorithm.algorithm3)
		{
			float resultLevel;
			if (hsb[2] < 0.5f)
			{
				resultLevel = pixelLevelNormalized * (hsb[2] * 2f);
			}
			else
			{
				float range = (1f - hsb[2]) * 2;
				resultLevel = range * pixelLevelNormalized + (1f - range);
			}
			return Color.createFromHSB(hsb[0], hsb[1], resultLevel).getRGB();
		}
		else if (how == ColorifyAlgorithm.solidColor)
		{
			return Color.createFromHSB(hsb[0], hsb[1], hsb[2]).getRGB();
		}
		else if (how == ColorifyAlgorithm.none)
		{
			return Color.createFromHSB(hsb[0], hsb[1], hsb[2]).getRGB();
		}
		else
		{
			throw new IllegalArgumentException("Unrecognize colorify algorithm.");
		}

	}

	public enum ColorifyAlgorithm
	{
		// algorithm3 preserves contrast a little better than algorithm2.
		// solidColor paints the pixels one color.
		none, algorithm2, algorithm3, solidColor
	}

	public static Dimension fitDimensionsWithinBoundingBox(Dimension maxDimensions, double originalWidth, double originalHeight)
	{
		double width = originalWidth;
		double height = originalHeight;
		if (originalWidth > maxDimensions.width)
		{
			width = maxDimensions.width;
			height = height * (width / originalWidth);
		}
		if (height > maxDimensions.height)
		{
			double prevHeight = height;
			height = maxDimensions.height;
			width = width * (height / prevHeight);
		}
		return new Dimension(width, height);
	}

	public static Image convertImageToType(Image img, ImageType type)
	{
		Image result = Image.create(img.getWidth(), img.getHeight(), type);
		try (Painter p = result.createPainter())
		{
			p.drawImage(img, 0, 0);
		}
		return result;
	}

	public static Image scaleByWidth(Image inImage, int xSize)
	{
		return scaleByWidth(inImage, xSize, Method.QUALITY);
	}

	public static Image scaleByWidth(Image inImage, int xSize, Method method)
	{
		int ySize = getHeightWhenScaledByWidth(inImage, xSize);
		return scale(inImage, xSize, ySize, method);
	}

	public static Image scale(Image inImage, int width, int height, Method method)
	{
		return inImage.scale(method, width, height);
	}

	public static int getHeightWhenScaledByWidth(Image inImage, int xSize)
	{
		double aspectRatio = ((double) inImage.getHeight()) / inImage.getWidth();
		int ySize = (int) Math.round(xSize * aspectRatio);
		if (ySize == 0)
			ySize = 1;
		return ySize;
	}

	public static int getWidthWhenScaledByHeight(Image inImage, int ySize)
	{
		double aspectRatioInverse = ((double) inImage.getWidth()) / inImage.getHeight();
		int xSize = (int) Math.round(aspectRatioInverse * ySize);
		if (xSize == 0)
			xSize = 1;
		return xSize;
	}

	public static Image scaleByHeight(Image inImage, int ySize)
	{
		return scaleByHeight(inImage, ySize, Method.QUALITY);
	}

	public static Image scaleByHeight(Image inImage, int ySize, Method method)
	{
		int xSize = getWidthWhenScaledByHeight(inImage, ySize);
		return inImage.scale(method, xSize, ySize);
	}

	public static void scaleInto(Image source, Image target, IntRectangle boundsInSource)
	{
		boolean sourceHasAlpha = source.hasAlpha();
		boolean targetHasAlpha = target.hasAlpha();

		double scale = ((double) target.getWidth()) / ((double) source.getWidth());

		IntRectangle pixelsToUpdate;
		if (boundsInSource == null)
		{
			pixelsToUpdate = new IntRectangle(0, 0, target.getWidth(), target.getHeight());
		}
		else
		{
			int upperLeftX = Math.max(0, (int) (boundsInSource.x * scale));
			int upperLeftY = Math.max(0, (int) (boundsInSource.y * scale));
			pixelsToUpdate = new IntRectangle(upperLeftX, upperLeftY, Math.min((int) (boundsInSource.width * scale) + 1, target.getWidth() - 1 - upperLeftX),
					Math.min((int) (boundsInSource.height * scale) + 1, target.getHeight() - 1 - upperLeftY));
		}

		try (PixelReader sourcePixels = source.createPixelReader(null); PixelReaderWriter targetPixels = target.createPixelReaderWriter(pixelsToUpdate))
		{
			for (int y = pixelsToUpdate.y; y < pixelsToUpdate.y + pixelsToUpdate.height; y++)
			{
				for (int x = pixelsToUpdate.x; x < pixelsToUpdate.x + pixelsToUpdate.width; x++)
				{
					int x1 = (int) (x / scale);
					int y1 = (int) (y / scale);
					int x2 = Math.min(x1 + 1, source.getWidth() - 1);
					int y2 = Math.min(y1 + 1, source.getHeight() - 1);
					double dx = x / scale - x1;
					double dy = y / scale - y1;
					Color c00 = Color.create(sourcePixels.getRGB(x1, y1), sourceHasAlpha);
					Color c01 = Color.create(sourcePixels.getRGB(x2, y1), sourceHasAlpha);
					Color c10 = Color.create(sourcePixels.getRGB(x1, y2), sourceHasAlpha);
					Color c11 = Color.create(sourcePixels.getRGB(x2, y2), sourceHasAlpha);
					int r0 = interpolate(c00.getRed(), c01.getRed(), c10.getRed(), c11.getRed(), dx, dy);
					int g0 = interpolate(c00.getGreen(), c01.getGreen(), c10.getGreen(), c11.getGreen(), dx, dy);
					int b0 = interpolate(c00.getBlue(), c01.getBlue(), c10.getBlue(), c11.getBlue(), dx, dy);
					if (targetHasAlpha)
					{
						int a0 = interpolate(c00.getAlpha(), c01.getAlpha(), c10.getAlpha(), c11.getAlpha(), dx, dy);
						targetPixels.setRGB(x, y, r0, g0, b0, a0);
					}
					else
					{
						targetPixels.setRGB(x, y, r0, g0, b0);
					}
				}
			}
		}
	}

	public static int interpolate(int v00, int v01, int v10, int v11, double dx, double dy)
	{
		double v0 = v00 * (1 - dx) + v01 * dx;
		double v1 = v10 * (1 - dx) + v11 * dx;
		return (int) ((v0 * (1 - dy) + v1 * dy) + 0.5);
	}

	public static float[][] createGaussianKernel(int size)
	{
		if (size == 0)
		{
			return new float[][] { { 1f } };
		}

		NormalDistribution dist = createDistributionForSize(size);
		int resultSize = (size * 2);

		float[][] kernel = new float[resultSize][resultSize];
		for (int x = 0; x < resultSize; x++)
		{
			double xDistanceFromCenter = Math.abs(size - x - 0.5);
			for (int y = 0; y < resultSize; y++)
			{
				double yDistanceFromCenter = Math.abs(size - y - 0.5);
				double distanceFromCenter = Math.sqrt(xDistanceFromCenter * xDistanceFromCenter + yDistanceFromCenter * yDistanceFromCenter);
				kernel[y][x] = (float) dist.density(distanceFromCenter);
			}
		}
		normalize(kernel);
		return kernel;
	}

	public static NormalDistribution createDistributionForSize(int size)
	{
		return new NormalDistribution(0, getStandardDeviationSizeForGaussianKernel(size));
	}

	public static float getGaussianMode(int kernelSize)
	{
		if (kernelSize == 0)
		{
			return 0f;
		}

		NormalDistribution dist = new NormalDistribution(0, getStandardDeviationSizeForGaussianKernel(kernelSize));
		return (float) dist.density(0.0);
	}

	protected static double getStandardDeviationSizeForGaussianKernel(int kernelSize)
	{
		if (kernelSize == 0)
		{
			return 0f;
		}

		return kernelSize / (2.0 * 3.0);
	}

	public static float[][] createPositiveSincKernel(int size, double scale)
	{
		if (size == 0)
		{
			return new float[][] { { 1f } };
		}

		Sinc dist = new Sinc();

		float[][] kernel = new float[size][size];
		for (int x : new Range(size))
		{
			double xDistanceFromCenter = Math.abs(size / 2.0 - x - 0.5);
			for (int y : new Range(size))
			{
				double yDistanceFromCenter = Math.abs(size / 2.0 - y - 0.5);
				double distanceFromCenter = Math.sqrt(xDistanceFromCenter * xDistanceFromCenter + yDistanceFromCenter * yDistanceFromCenter);
				kernel[y][x] = Math.max(0, (float) dist.value(distanceFromCenter * scale));
			}
		}
		normalize(kernel);
		return kernel;
	}

	public static void maximizeContrastGrayscale(Image image)
	{
		if (!image.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Image type must a supported grayscale type, but was " + image.getType());
		}

		int maxPixelValue = image.getMaxPixelLevel();
		double min = maxPixelValue;
		double max = 0;

		try (PixelReaderWriter pixels = image.createPixelReaderWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					double value = pixels.getGrayLevel(x, y);
					if (value < min)
						min = value;
					if (value > max)
						max = value;
				}
			}

			if (max > min)
			{
				double range = max - min;
				for (int y = 0; y < image.getHeight(); y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						double value = pixels.getGrayLevel(x, y);
						int newValue = (int) (((value - min) / range) * maxPixelValue);
						pixels.setGrayLevel(x, y, newValue);
					}
				}
			}
		}
	}

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
			for (int c = 0; c < kernel[0].length; c++)
			{
				kernel[r][c] /= sum;
			}
		}
	}

	public static void maskWithImageInPlace(Image image1, Image image2, Image mask, IntPoint maskOffset, boolean invertMask)
	{
		if (maskOffset == null)
		{
			maskOffset = new IntPoint(0, 0);
		}

		if (mask.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale");

		if (image1.getWidth() != image2.getWidth())
			throw new IllegalArgumentException();
		if (image1.getHeight() != image2.getHeight())
			throw new IllegalArgumentException();

		if (image1.getType() != ImageType.RGB && image1.getType() != ImageType.ARGB)
		{
			throw new IllegalArgumentException("Image 1 must be type " + ImageType.RGB + " or " + ImageType.ARGB + ", but was type " + image1.getType() + ".");
		}
		if (image2.getType() != ImageType.RGB && image2.getType() != ImageType.ARGB)
		{
			throw new IllegalArgumentException("Image 2 must be type " + ImageType.RGB + " or " + ImageType.ARGB + ", but was type " + image2.getType() + ".");
		}

		IntRectangle image1Bounds = new IntRectangle(0, 0, image1.getWidth(), image1.getHeight());
		IntRectangle maskBounds = new IntRectangle(maskOffset.x, maskOffset.y, mask.getWidth(), mask.getHeight());
		IntRectangle maskBoundsInImage1 = image1Bounds.findIntersection(maskBounds);
		if (maskBoundsInImage1 == null)
		{
			return;
		}
		IntPoint diff = maskBoundsInImage1.upperLeftCorner().subtract(maskBounds.upperLeftCorner());
		IntRectangle maskBoundsInMask = new IntRectangle(diff.x, diff.y, maskBoundsInImage1.width, maskBoundsInImage1.height);

		Image image2Snippet = image2.copySubImage(maskBoundsInImage1, image1.hasAlpha() || image2.hasAlpha());

		try (PixelReader image1Pixels = image1.createPixelReader(maskBoundsInImage1);
				PixelReaderWriter image2SnippetPixels = image2Snippet.createPixelReaderWriter();
				PixelReader maskPixels = mask.createPixelReader(maskBoundsInMask))
		{
			ThreadHelper.getInstance().processRowsInParallel(0, image2Snippet.getHeight(), (y) ->
			{
				for (int x = 0; x < image2Snippet.getWidth(); x++)
				{
					Color c1 = image1Pixels.getPixelColor(x + maskBoundsInImage1.x, y + maskBoundsInImage1.y);
					Color c2 = image2SnippetPixels.getPixelColor(x, y);

					int maskLevel = invertMask ? 255 - maskPixels.getGrayLevel(x, y) : maskPixels.getGrayLevel(x, y);

					int r = Helper.linearComboBase255(maskLevel, (c1.getRed()), (c2.getRed()));
					int g = Helper.linearComboBase255(maskLevel, (c1.getGreen()), (c2.getGreen()));
					int b = Helper.linearComboBase255(maskLevel, (c1.getBlue()), (c2.getBlue()));
					int a = Helper.linearComboBase255(maskLevel, c1.getAlpha(), c2.getAlpha());
					image2SnippetPixels.setRGB(x, y, r, g, b, a);
				}
			});
		}

		try (Painter p = image1.createPainter())
		{
			p.setAlphaComposite(AlphaComposite.Src);
			p.drawImage(image2Snippet, maskBoundsInImage1.x, maskBoundsInImage1.y);
		}
	}

	public static Image maskWithColorInRegion(Image image, Color color, Image mask, boolean invertMask, IntPoint imageOffsetInMask)
	{
		if (mask.getType() != ImageType.Grayscale8Bit && mask.getType() != ImageType.Binary)
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale.");

		try (Image overlay = Image.create(image.getWidth(), image.getHeight(), ImageType.ARGB))
		{
			try (PixelReader maskPixels = mask.createPixelReader(new IntRectangle(imageOffsetInMask, overlay.getWidth(), overlay.getHeight())); PixelWriter overlayPixels = overlay.createPixelWriter())
			{
				ThreadHelper.getInstance().processRowsInParallel(0, overlay.getHeight(), (y) ->
				{
					for (int x = 0; x < overlay.getWidth(); x++)
					{
						int xInMask = x + imageOffsetInMask.x;
						int yInMask = y + imageOffsetInMask.y;
						if (xInMask < 0 || yInMask < 0 || xInMask >= mask.getWidth() || yInMask >= mask.getHeight())
						{
							continue;
						}

						int maskLevel = (int) (maskPixels.getNormalizedPixelLevel(xInMask, yInMask) * color.getAlpha());
						if (invertMask)
							maskLevel = 255 - maskLevel;

						int r = color.getRed();
						int g = color.getGreen();
						int b = color.getBlue();
						int a = 255 - maskLevel;
						overlayPixels.setRGB(x, y, r, g, b, a);
					}
				});
			}

			Image result = image.deepCopy();
			try (Painter p = result.createPainter())
			{
				p.drawImage(overlay, 0, 0);
			}

			return result;
		}
	}

	public static void drawMaskOntoImage(Image image, Image mask, Color color, IntPoint maskOffsetInImage)
	{
		if (mask.getType() != ImageType.Binary)
		{
			throw new IllegalArgumentException("Mask must be of type ImageType.Binary.");
		}

		try (PixelReader maskPixels = mask.createPixelReader(); PixelReaderWriter imagePixels = image.createPixelReaderWriter(new IntRectangle(maskOffsetInImage, mask.getWidth(), mask.getHeight())))
		{
			ThreadHelper.getInstance().processRowsInParallel(0, mask.getHeight(), (yInMask) ->
			{
				for (int xInMask = 0; xInMask < mask.getWidth(); xInMask++)
				{
					if (maskPixels.getGrayLevel(xInMask, yInMask) > 0)
					{
						int xInImage = xInMask + maskOffsetInImage.x;
						int yInImage = yInMask + maskOffsetInImage.y;

						if (xInImage >= 0 && xInImage < image.getWidth() && yInImage >= 0 && yInImage < image.getHeight())
						{
							imagePixels.setPixelColor(xInImage, yInImage, color);
						}
					}
				}
			});
		}
	}

	public static void clearImageToTransparent(Image image)
	{
		try (Painter p = image.createPainter())
		{
			p.setAlphaComposite(AlphaComposite.Clear);
			p.fillRect(0, 0, image.getWidth(), image.getHeight());
		}
	}

	public static Image applyAlpha(Image original, Integer alpha)
	{
		if (alpha == null)
		{
			throw new IllegalArgumentException("Alpha must be between 0.0 and 1.0");
		}

		if (alpha == 255)
		{
			return original;
		}

		Image transparentImage = Image.create(original.getWidth(), original.getHeight(), ImageType.ARGB);

		try (Painter p = transparentImage.createPainter())
		{
			p.setAlphaComposite(AlphaComposite.SrcOver, alpha / 255f);
			p.drawImage(original, 0, 0);
		}

		return transparentImage;
	}

	public static Image setAlphaFromMaskInRegion(Image image, Image alphaMask, boolean invertMask, IntPoint imageOffsetInMask)
	{
		if (alphaMask.getType() != ImageType.Grayscale8Bit && alphaMask.getType() != ImageType.Binary)
		{
			throw new IllegalArgumentException("mask type must be ImageType.Grayscale or TYPE_BYTE_BINARY");
		}

		Image result = Image.create(image.getWidth(), image.getHeight(), ImageType.ARGB);
		IntRectangle imageBoundsInMask = imageOffsetInMask == null ? null : new IntRectangle(imageOffsetInMask, image.getWidth(), image.getHeight());
		try (PixelReader alphaMaskPixels = alphaMask.createPixelReader(imageBoundsInMask); PixelReader imagePixels = image.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
				for (int x = 0; x < image.getWidth(); x++)
				{
					int xInMask = x + imageOffsetInMask.x;
					int yInMask = y + imageOffsetInMask.y;
					if (xInMask < 0 || yInMask < 0 || xInMask >= alphaMask.getWidth() || yInMask >= alphaMask.getHeight())
					{
						continue;
					}

					int maskLevel = alphaMaskPixels.getGrayLevel(xInMask, yInMask);
					if (alphaMask.getType() == ImageType.Binary)
					{
						if (maskLevel == 1)
						{
							maskLevel = 255;
						}
					}
					if (invertMask)
					{
						maskLevel = 255 - maskLevel;
					}

					Color originalColor = imagePixels.getPixelColor(x, y);
					int newAlpha = Math.min(maskLevel, originalColor.getAlpha());
					if (newAlpha == 0)
					{
						resultPixels.setPixelColor(x, y, Color.transparentBlack);
					}
					else
					{
						resultPixels.setPixelColor(x, y, Color.create(originalColor.getRed(), originalColor.getGreen(), originalColor.getBlue(), newAlpha));
					}
				}
		}
		return result;
	}

	public static void combineImagesWithMaskInRegion(Image image1, Image image2, Image mask, int xLoc, int yLoc, double angle, Point pivot)
	{
		if (mask.getType() != ImageType.Grayscale8Bit)
			throw new IllegalArgumentException("Expected mask to be type ImageType.Grayscale.");

		if (image1.getWidth() != image2.getWidth())
			throw new IllegalArgumentException("Image widths do not match. image1 width: " + image1.getWidth() + ", image 2 width: " + image2.getWidth());
		if (image1.getHeight() != image2.getHeight())
			throw new IllegalArgumentException();

		if (image1.hasAlpha() || image2.hasAlpha())
		{
			Rectangle rotatedMaskBounds = new RotatedRectangle(new Point(0, 0), mask.getWidth(), mask.getHeight(), angle, pivot.subtract(new Point(xLoc, yLoc))).getBounds();
			try (Image maskRotated = Image.create((int) rotatedMaskBounds.width, (int) rotatedMaskBounds.height, mask.getType()))
			{
				try (Painter p = maskRotated.createPainter(DrawQuality.High))
				{
					p.rotate(angle, pivot.subtract(new Point(xLoc, yLoc).add(rotatedMaskBounds.upperLeftCorner())));
					p.translate(-rotatedMaskBounds.x, -rotatedMaskBounds.y);
					p.drawImage(mask, 0, 0);
				}

				Rectangle rotatedBounds = new RotatedRectangle(new Point(xLoc, yLoc), mask.getWidth(), mask.getHeight(), angle, pivot).getBounds();
				IntPoint maskOffset = rotatedBounds.upperLeftCorner().toIntPointRounded();
				maskWithImageInPlace(image1, image2, maskRotated, maskOffset, true);
			}
		}
		else
		{
			try (Image region = copySnippetRotated(image2, xLoc, yLoc, mask.getWidth(), mask.getHeight(), angle, pivot))
			{
				try (PixelReader maskPixels = mask.createPixelReader(); PixelReaderWriter regionPixels = region.createPixelReaderWriter())
				{
					for (int y = 0; y < region.getHeight(); y++)
						for (int x = 0; x < region.getWidth(); x++)
						{
							int grayLevel = maskPixels.getGrayLevel(x, y);
							Color r = Color.create(regionPixels.getRGB(x, y), true);
							int alphaLevel = Math.min(r.getAlpha(), grayLevel);
							regionPixels.setRGB(x, y, Color.create(r.getRed(), r.getGreen(), r.getBlue(), alphaLevel).getRGB());
						}
				}

				try (Painter p = image1.createPainter())
				{
					p.rotate(angle, pivot);
					p.drawImage(region, xLoc, yLoc);
				}
			}
		}
	}

	public static Image copySnippetRotated(Image image, int xLoc, int yLoc, int width, int height, double angle, Point pivot)
	{
		Image result = Image.create(width, height, ImageType.ARGB);
		try (Painter pResult = result.createPainter(DrawQuality.High))
		{
			pResult.rotate(-angle, pivot.x - xLoc, pivot.y - yLoc);
			pResult.translate(-xLoc, -yLoc);
			pResult.drawImage(image, 0, 0);
		}

		return result;
	}

	public static Image copySnippetPreservingAlphaOfTransparentPixels(Image source, int xLoc, int yLoc, int width, int height)
	{
		IntRectangle sourceBounds = new IntRectangle(0, 0, source.size().width, source.size().height);
		Image result = Image.create(width, height, source.getType());
		IntRectangle copyBoundsInSource = new IntRectangle(xLoc, yLoc, width, height).findIntersection(sourceBounds);
		try (PixelReader sourcePixels = source.createPixelReader(copyBoundsInSource); PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					int xInSource = xLoc + x;
					int yInSource = yLoc + y;
					if (sourceBounds.contains(xInSource, yInSource))
					{
						int rgb = sourcePixels.getRGB(xInSource, yInSource);
						resultPixels.setRGB(x, y, rgb);
					}
				}
			}
		}

		return result;
	}

	public static Image copySnippetPreservingAlphaOfTransparentPixels(Image source, IntRectangle boundsInSourceToCopyFrom)
	{
		return copySnippetPreservingAlphaOfTransparentPixels(source, boundsInSourceToCopyFrom.x, boundsInSourceToCopyFrom.y, boundsInSourceToCopyFrom.width, boundsInSourceToCopyFrom.height);
	}

	public static Image rotate90Degrees(Image image, boolean isClockwise)
	{
		Image result = Image.create(image.getHeight(), image.getWidth(), image.getType());
		try (PixelReader imagePixels = image.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					if (isClockwise)
					{
						resultPixels.setRGB(image.getHeight() - y - 1, image.getWidth() - x - 1, imagePixels.getRGB(x, y));
					}
					else
					{
						resultPixels.setRGB(y, x, imagePixels.getRGB(x, y));
					}
				}
			}
		}

		return result;
	}

	public static void drawIfPixelValueIsGreaterThanTarget(Image target, Image toDraw, int xLoc, int yLoc)
	{
		if (toDraw.getType() != ImageType.Binary)
		{
			throw new IllegalArgumentException("Unsupported buffered image type for toDraw. Actual type: " + toDraw.getType());
		}
		if (target.getType() != ImageType.Binary)
		{
			throw new IllegalArgumentException("Unsupported buffered image type for target. Actual type: " + target.getType());
		}

		try (PixelReader toDrawPixels = toDraw.createPixelReader();
				PixelReaderWriter targetPixels = target.createPixelReaderWriter(new IntRectangle(xLoc, yLoc, toDraw.getWidth(), toDraw.getHeight())))
		{
			for (int r = 0; r < toDraw.getHeight(); r++)
			{
				int targetR = yLoc + r;
				if (targetR < 0 || targetR >= target.getHeight())
				{
					continue;
				}
				for (int c = 0; c < toDraw.getWidth(); c++)
				{
					int targetC = xLoc + c;
					if (targetC < 0 || targetC >= target.getWidth())
					{
						continue;
					}

					int toDrawValue = toDrawPixels.getGrayLevel(c, r);
					int targetValue = targetPixels.getGrayLevel(targetC, targetR);
					if (toDrawValue > targetValue)
					{
						targetPixels.setGrayLevel(targetC, targetR, toDrawValue);
					}
				}
			}
		}
	}

	public static Image convolveGrayscale(Image img, float[][] kernel, boolean maximizeContrast, boolean paddImageToAvoidWrapping)
	{
		return convolveGrayscaleThenSetContrast(img, kernel, maximizeContrast, 0f, 1f, paddImageToAvoidWrapping).getSecond();
	}

	public static Tuple2<ComplexArray, Image> convolveGrayscaleThenSetContrast(Image img, float[][] kernel, boolean setContrast, float contrastMin, float contrastMax, boolean paddImageToAvoidWrapping)
	{
		ComplexArray data = convolveGrayscale(img, kernel, paddImageToAvoidWrapping);

		ImageType resultType = img.getType() == ImageType.Grayscale16Bit ? ImageType.Grayscale16Bit : ImageType.Grayscale8Bit;

		data.moveRealToLeftSide();
		data.swapQuadrantsOfLeftSideInPlace();

		return new Tuple2<>(data, realToImage(data, resultType, img.getWidth(), img.getHeight(), setContrast, contrastMin, contrastMax, false, 0f));
	}

	public static Image convolveGrayscaleThenScale(Image img, float[][] kernel, float scale, boolean paddImageToAvoidWrapping)
	{
		ImageType resultType = img.getType() == ImageType.Grayscale16Bit ? ImageType.Grayscale16Bit : ImageType.Grayscale8Bit;
		return convolveGrayscaleThenScale(img, kernel, scale, paddImageToAvoidWrapping, resultType);
	}

	public static Image convolveGrayscaleThenScale(Image img, float[][] kernel, float scale, boolean paddImageToAvoidWrapping, ImageType resultType)
	{
		ComplexArray data = convolveGrayscale(img, kernel, paddImageToAvoidWrapping);

		data.moveRealToLeftSide();
		data.swapQuadrantsOfLeftSideInPlace();

		return realToImage(data, resultType, img.getWidth(), img.getHeight(), false, 0f, 0f, true, scale);
	}

	private static ComplexArray convolveGrayscale(Image img, float[][] kernel, boolean paddImageToAvoidWrapping)
	{
		int colsPaddingToAvoidWrapping = paddImageToAvoidWrapping ? kernel[0].length / 2 : 0;
		int cols = getPowerOf2EqualOrLargerThan(Math.max(img.getWidth() + colsPaddingToAvoidWrapping, kernel[0].length));
		int rowsPaddingToAvoidWrapping = paddImageToAvoidWrapping ? kernel.length / 2 : 0;
		int rows = getPowerOf2EqualOrLargerThan(Math.max(img.getHeight() + rowsPaddingToAvoidWrapping, kernel.length));
		if (cols < 2)
			cols = 2;
		if (rows < 2)
			rows = 2;

		ComplexArray data = forwardFFT(img, rows, cols);

		ComplexArray kernelData = forwardFFT(kernel, rows, cols, true);

		data.multiplyInPlace(kernelData);
		kernelData = null;

		inverseFFT(data);

		return data;
	}

	public static Image realToImage(ComplexArray data, ImageType type, int imageWidth, int imageHeight, boolean setContrast, float contrastMin, float contrastMax, boolean scaleLevels, float scale)
	{
		int imgRowPaddingOver2 = (data.getHeight() - imageHeight) / 2;
		int imgColPaddingOver2 = (data.getWidth() - imageWidth) / 2;

		if (setContrast)
		{
			data.setContrast(contrastMin, contrastMax, imgRowPaddingOver2, imageHeight, imgColPaddingOver2, imageWidth);
		}
		else if (scaleLevels)
		{
			data.scale(scale, imgRowPaddingOver2, imageHeight, imgColPaddingOver2, imageWidth);
		}

		Image result = data.toImage(imgRowPaddingOver2, imageHeight, imgColPaddingOver2, imageWidth, type);
		return result;
	}

	public static void inverseFFT(ComplexArray data)
	{
		FloatFFT_2D fft = new FloatFFT_2D(data.getHeight(), data.getWidth());
		fft.complexInverse(data.getArrayJTransformsFormat(), true);
	}

	public static ComplexArray forwardFFT(Image img, int rows, int cols)
	{
		ComplexArray data = new ComplexArray(cols, rows);

		int imgRowPadding = rows - img.getHeight();
		int imgColPadding = cols - img.getWidth();
		int imgRowPaddingOver2 = imgRowPadding / 2;
		int imgColPaddingOver2 = imgColPadding / 2;
		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);

		boolean isGrayscale = img.isGrayscaleOrBinary();
		float maxPixelValue = img.getMaxPixelLevel();

		try (PixelReader imgPixels = img.createPixelReader())
		{
			for (int r = 0; r < img.getHeight(); r++)
			{
				for (int c = 0; c < img.getWidth(); c++)
				{
					float grayLevel = imgPixels.getGrayLevel(c, r);
					if (isGrayscale)
						grayLevel /= maxPixelValue;
					data.setRealInput(c + imgColPaddingOver2, r + imgRowPaddingOver2, grayLevel);
				}
			}
		}

		fft.realForwardFull(data.getArrayJTransformsFormat());

		return data;
	}

	public static ComplexArray forwardFFT(float[][] input, int rows, int cols, boolean flipXAndYAxis)
	{
		ComplexArray data = new ComplexArray(cols, rows);

		int rowPadding = rows - input.length;
		int rowPaddingOver2 = rowPadding / 2;
		int colPadding = cols - input[0].length;
		int columnPaddingOver2 = colPadding / 2;
		for (int r = 0; r < input.length; r++)
		{
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
		}

		FloatFFT_2D fft = new FloatFFT_2D(rows, cols);
		fft.realForwardFull(data.getArrayJTransformsFormat());

		return data;
	}

	public static Image arrayToImage(float[][] array, ImageType imageType)
	{
		Image image = Image.create(array[0].length, array.length, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(imageType);
		try (PixelWriter imagePixels = image.createPixelWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					imagePixels.setGrayLevel(x, y, (int) (array[y][x] * maxPixelValue));
				}
			}
		}
		return image;
	}

	public static int getPowerOf2EqualOrLargerThan(int value)
	{
		return getPowerOf2EqualOrLargerThan((double) value);
	}

	public static int getPowerOf2EqualOrLargerThan(double value)
	{
		double logLength = Math.log(value) / Math.log(2.0);
		if (((int) logLength) == logLength)
		{
			return (int) value;
		}

		return (int) Math.pow(2.0, ((int) logLength) + 1.0);
	}

	public static Image genWhiteNoise(Random rand, int rows, int cols, ImageType imageType)
	{
		Image image = Image.create(cols, rows, imageType);
		int maxPixelValue = Image.getMaxPixelLevelForType(image.getType());
		try (PixelWriter imagePixels = image.createPixelWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					imagePixels.setGrayLevel(x, y, (int) (rand.nextFloat() * maxPixelValue));
				}
			}
		}
		return image;
	}

	public static Image matchHistogram(Image target, Image source, ImageType resultType)
	{
		nortantis.util.HistogramEqualizer targetEqualizer = new nortantis.util.HistogramEqualizer(target);
		nortantis.util.HistogramEqualizer sourceEqualizer = new nortantis.util.HistogramEqualizer(source);
		sourceEqualizer.imageType = resultType;

		sourceEqualizer.createInverse();

		Image targetEqualized = targetEqualizer.equalize(target);

		Image outImage = sourceEqualizer.inverseEqualize(targetEqualized);

		return outImage;
	}

	public static Image matchHistogram(Image target, Image source)
	{
		return matchHistogram(target, source, target.getType());
	}

	public static void write(Image image, String fileName)
	{
		image.write(fileName);
	}

	public static String openImageInSystemDefaultEditor(Image map, String filenameWithoutExtension) throws IOException
	{
		String format = "png";
		File tempFile = File.createTempFile(filenameWithoutExtension, "." + format);
		map.write(tempFile.getAbsolutePath());

		openImageInSystemDefaultEditor(tempFile.getAbsolutePath());
		return tempFile.getAbsolutePath();
	}

	public static void openImageInSystemDefaultEditor(String imageFilePath)
	{
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
			throw new RuntimeException("Unable to open the map because Java's Desktop is not supported");
		}
	}

	public static int bound(int value)
	{
		return Math.min(255, Math.max(0, value));
	}

	public static float calcMeanOfGrayscaleImage(Image image)
	{
		long sum = 0;
		try (PixelReader imagePixels = image.createPixelReader())
		{
			for (int r = 0; r < image.getHeight(); r++)
			{
				for (int c = 0; c < image.getWidth(); c++)
				{
					sum += imagePixels.getGrayLevel(c, r);
				}
			}
		}

		return sum / ((float) (image.getHeight() * image.getWidth()));
	}

	public static float[] calcMeanOfEachColor(Image image)
	{
		float[] result = new float[3];
		try (PixelReader imagePixels = image.createPixelReader())
		{
			for (int channel : new Range(3))
			{
				long sum = 0;
				for (int r = 0; r < image.getHeight(); r++)
				{
					for (int c = 0; c < image.getWidth(); c++)
					{
						Color color = Color.create(imagePixels.getRGB(c, r));
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
				result[channel] = sum / ((float) (image.getHeight() * image.getWidth()));
			}
		}

		return result;
	}

	public static Image flipOnXAxis(Image image)
	{
		Image result = Image.create(image.getWidth(), image.getHeight(), image.getType());
		try (PixelReader imagePixels = image.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					resultPixels.setRGB(image.getWidth() - x - 1, y, imagePixels.getRGB(x, y));
				}
			}
		}

		return result;
	}

	public static Image flipOnYAxis(Image image)
	{
		Image result = Image.create(image.getWidth(), image.getHeight(), image.getType());
		try (PixelReader imagePixels = image.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					resultPixels.setRGB(x, image.getHeight() - y - 1, imagePixels.getRGB(x, y));
				}
			}
		}

		return result;
	}

	public static void fillInTarget(Image target, Image source, int lowThreshold, int highThreshold, int fillValue)
	{
		if (!target.size().equals(source.size()))
		{
			throw new IllegalArgumentException("Source and target must be the same size. Source size: " + source.size() + ", target size: " + target.size());
		}

		try (PixelReader sourcePixels = source.createPixelReader(); PixelReaderWriter targetPixels = target.createPixelReaderWriter())
		{
			for (int y = 0; y < source.getHeight(); y++)
				for (int x = 0; x < source.getWidth(); x++)
				{
					int value = sourcePixels.getGrayLevel(x, y);
					if (value >= lowThreshold && value < highThreshold)
					{
						targetPixels.setGrayLevel(x, y, fillValue);
					}
				}
		}
	}

	public static void subtractThresholded(Image toThreshold, int threshold, int highValue, Image toSubtractFrom)
	{
		if (!toThreshold.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported image type for thresholding: " + toThreshold.getType());
		}

		if (!toSubtractFrom.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported target image type for subtracting from: " + toSubtractFrom.getType());
		}

		if (!toThreshold.size().equals(toSubtractFrom.size()))
		{
			throw new IllegalArgumentException("Images for thresholding and subtracting must be the same size. First size: " + toThreshold.size() + ", second size: " + toSubtractFrom.size());
		}

		try (PixelReader toThresholdPixels = toThreshold.createPixelReader(); PixelReaderWriter toSubtractFromPixels = toSubtractFrom.createPixelReaderWriter())
		{
			ThreadHelper.getInstance().processRowsInParallel(0, toThreshold.getHeight(), (y) ->
			{
				for (int x = 0; x < toThreshold.getWidth(); x++)
				{
					int thresholdedValue = toThresholdPixels.getGrayLevel(x, y) >= threshold ? highValue : 0;
					toSubtractFromPixels.setGrayLevel(x, y, Math.max(0, toSubtractFromPixels.getGrayLevel(x, y) - thresholdedValue));
				}
			});
		}
	}

	public static void addThresholded(Image toThreshold, int threshold, int highValue, Image toAddTo)
	{
		if (!toThreshold.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported image type for thresholding: " + toThreshold.getType());
		}

		if (!toAddTo.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported target image type for adding to: " + toAddTo.getType());
		}

		if (!toThreshold.size().equals(toAddTo.size()))
		{
			throw new IllegalArgumentException("Images for thresholding and adding must be the same size. First size: " + toThreshold.size() + ", second size: " + toAddTo.size());
		}

		try (PixelReader toThresholdPixels = toThreshold.createPixelReader(); PixelReaderWriter toAddToPixels = toAddTo.createPixelReaderWriter())
		{
			ThreadHelper.getInstance().processRowsInParallel(0, toThreshold.getHeight(), (y) ->
			{
				for (int x = 0; x < toThreshold.getWidth(); x++)
				{
					int thresholdedValue = toThresholdPixels.getGrayLevel(x, y) >= threshold ? highValue : 0;
					toAddToPixels.setGrayLevel(x, y, Math.max(0, toAddToPixels.getGrayLevel(x, y) + thresholdedValue));
				}
			});
		}
	}

	public static void threshold(Image image, int threshold)
	{
		int maxPixelValue = image.getMaxPixelLevel();
		threshold(image, threshold, maxPixelValue);
	}

	public static void threshold(Image image, int threshold, int highValue)
	{
		if (!image.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported image type for thresholding: " + image.getType());
		}

		try (PixelReaderWriter imagePixels = image.createPixelReaderWriter())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					int value = imagePixels.getGrayLevel(x, y);
					imagePixels.setGrayLevel(x, y, value >= threshold ? highValue : 0);
				}
			}
		}
	}

	public static void subtract(Image target, Image other)
	{
		if (!target.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported target image type for subtracting: " + target.getType());
		}

		if (!other.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Unsupported other image type for subtracting: " + other.getType());
		}

		try (PixelReaderWriter targetPixels = target.createPixelReaderWriter(); PixelReader otherPixels = other.createPixelReader())
		{
			for (int y = 0; y < target.getHeight(); y++)
				for (int x = 0; x < target.getWidth(); x++)
				{
					int value = targetPixels.getGrayLevel(x, y);
					int otherValue = otherPixels.getGrayLevel(x, y);
					targetPixels.setGrayLevel(x, y, Math.max(0, value - otherValue));
				}
		}
	}

	public static void copySnippetFromSourceAndPasteIntoTarget(Image target, Image source, IntPoint upperLeftCornerToPasteIntoInTarget, IntRectangle boundsInSourceToCopyFrom,
			int widthOfBorderToNotDrawOn)
	{
		Image snippet = source.getSubImage(new IntRectangle(boundsInSourceToCopyFrom.x, boundsInSourceToCopyFrom.y, boundsInSourceToCopyFrom.width, boundsInSourceToCopyFrom.height));

		try (Painter p = target.createPainter())
		{
			p.setClip(widthOfBorderToNotDrawOn, widthOfBorderToNotDrawOn, target.getWidth() - widthOfBorderToNotDrawOn * 2, target.getHeight() - widthOfBorderToNotDrawOn * 2);
			p.setAlphaComposite(AlphaComposite.Src);
			p.drawImage(snippet, upperLeftCornerToPasteIntoInTarget.x, upperLeftCornerToPasteIntoInTarget.y);
		}
	}

	public static void darkenMiddleOfImage(Image image, int grungeWidth, double resolutionScale, boolean forceConvolutionBlur)
	{
		int blurLevel = (int) (grungeWidth * resolutionScale);
		if (blurLevel == 0)
			blurLevel = 1;

		int lineWidth = (int) (resolutionScale);
		if (lineWidth == 0)
		{
			lineWidth = 1;
		}
		int blurBoxWidth = blurLevel * 2 + lineWidth + 1;
		Image blurredBox;
		try (Image blurBox = Image.create(blurBoxWidth, blurBoxWidth, ImageType.Binary))
		{
			try (Painter p = blurBox.createPainter())
			{
				p.setColor(Color.white);
				p.fillRect(0, 0, blurBoxWidth, blurBoxWidth);

				p.setColor(Color.black);
				p.fillRect(lineWidth, lineWidth, blurBoxWidth, blurBoxWidth);
			}

			if (forceConvolutionBlur)
			{
				blurredBox = ImageHelper.convolveGrayscale(blurBox, ImageHelper.createGaussianKernel(blurLevel), true, true);
			}
			else
			{
				blurredBox = ImageHelper.blur(blurBox, blurLevel, true, true);
			}
		}

		try (Image blurredBoxTemp = blurredBox)
		{
			blurredBox = blurredBoxTemp.copySubImage(new IntRectangle(lineWidth, lineWidth, blurLevel + 1, blurLevel + 1));
		}

		assert image.getType() == ImageType.Grayscale8Bit;

		int imgWidth = image.getWidth();
		int imgHeight = image.getHeight();
		int blurBoxW = blurredBox.getWidth();

		try (Image finalBlurBox = blurredBox; PixelReaderWriter imagePixels = image.createPixelReaderWriter(); PixelReader blurBoxPixels = finalBlurBox.createPixelReader())
		{
			for (int y = 0; y < imgHeight; y++)
			{
				for (int x = 0; x < imgWidth; x++)
				{
					int imgLevel = imagePixels.getGrayLevel(x, y);

					int blurBoxX1 = x > blurLevel ? (x < imgWidth - blurLevel ? blurBoxW - 1 : imgWidth - x) : x;
					int x2 = imgWidth - x - 1;
					int blurBoxX2 = x2 > blurLevel ? (x2 < imgWidth - blurLevel ? blurBoxW - 1 : imgWidth - x2) : x2;
					int blurBoxY1 = y > blurLevel ? (y < imgHeight - blurLevel ? blurBoxW - 1 : imgHeight - y) : y;
					int y2 = imgHeight - y - 1;
					int blurBoxY2 = y2 > blurLevel ? (y2 < imgHeight - blurLevel ? blurBoxW - 1 : imgHeight - y2) : y2;

					int b1 = blurBoxPixels.getGrayLevel(blurBoxX1, blurBoxY1);
					int b2 = blurBoxPixels.getGrayLevel(blurBoxX2, blurBoxY1);
					int b3 = blurBoxPixels.getGrayLevel(blurBoxX1, blurBoxY2);
					int b4 = blurBoxPixels.getGrayLevel(blurBoxX2, blurBoxY2);
					int blurLevel_ = Math.max(b1, Math.max(b2, Math.max(b3, b4)));

					imagePixels.setGrayLevel(x, y, (imgLevel * blurLevel_) / 255);
				}
			}
		}
	}

	public static Image createPlaceholderImage(String[] message, Color textColor)
	{
		if (message.length == 0)
		{
			return Image.create(1, 1, ImageType.ARGB);
		}

		Font font = MapSettings.parseFont("URW Chancery L\t0\t30");

		IntDimension textBounds = TextDrawer.getTextDimensions(message[0], font).toIntDimension();
		for (int i : new Range(1, message.length))
		{
			IntDimension lineBounds = TextDrawer.getTextDimensions(message[i], font).toIntDimension();
			textBounds = new IntDimension(Math.max(textBounds.width, lineBounds.width), textBounds.height + lineBounds.height);
		}

		try (Image placeHolder = Image.create((textBounds.width + 15), (textBounds.height + 20), ImageType.ARGB))
		{
			try (Painter p = placeHolder.createPainter(DrawQuality.High))
			{
				p.setFont(font);
				p.setColor(textColor);

				int fontHeight = TextDrawer.getFontHeight(p);
				for (int i : new Range(message.length))
				{
					p.drawString(message[i], 14, fontHeight + (i * fontHeight));
				}
			}

			return ImageHelper.scaleByWidth(placeHolder, placeHolder.getWidth(), Method.QUALITY);
		}
	}
}
