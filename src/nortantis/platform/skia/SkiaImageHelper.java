package nortantis.platform.skia;

import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.ImageType;

import java.util.Map;

/**
 * Skia implementation of ImageHelper. Overrides the 10 delegatable methods to use Skia shaders when applicable.
 */
public class SkiaImageHelper extends ImageHelper
{
	@Override
	protected Image doConvertToGrayscale(Image img)
	{
		if (img instanceof SkiaImage)
		{
			return SkiaShaderOps.convertToGrayscale(img);
		}
		return super.doConvertToGrayscale(img);
	}

	@Override
	protected Image doMaskWithImage(Image image1, Image image2, Image mask)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(image1, image2, mask) && image1.getWidth() == mask.getWidth() && image1.getHeight() == mask.getHeight())
		{
			return SkiaShaderOps.maskWithImage(image1, image2, mask);
		}
		return super.doMaskWithImage(image1, image2, mask);
	}

	@Override
	protected Image doMaskWithColor(Image image, Color color, Image mask, boolean invertMask)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(image, mask))
		{
			return SkiaShaderOps.maskWithColor(image, color, mask, invertMask);
		}
		return super.doMaskWithColor(image, color, mask, invertMask);
	}

	@Override
	protected Image doMaskWithMultipleColors(Image image, Map<Integer, Color> colors, Image colorIndexes, Image mask,
			boolean invertMask)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(image, colorIndexes, mask) && mask.getType() == ImageType.Grayscale8Bit)
		{
			return SkiaShaderOps.maskWithMultipleColors(image, colors, colorIndexes, mask, invertMask);
		}
		return super.doMaskWithMultipleColors(image, colors, colorIndexes, mask, invertMask);
	}

	@Override
	protected Image doSetAlphaFromMask(Image image, Image alphaMask, boolean invertMask)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(image, alphaMask))
		{
			return SkiaShaderOps.setAlphaFromMask(image, alphaMask, invertMask);
		}
		return super.doSetAlphaFromMask(image, alphaMask, invertMask);
	}

	@Override
	protected Image doCopyAlphaTo(Image target, Image alphaSource)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(target, alphaSource))
		{
			return SkiaShaderOps.copyAlphaTo(target, alphaSource);
		}
		return super.doCopyAlphaTo(target, alphaSource);
	}

	@Override
	protected Image doColorify(Image image, Color color, ColorifyAlgorithm how, boolean forceAddAlpha)
	{
		if (image instanceof SkiaImage)
		{
			return SkiaShaderOps.colorify(image, color, how, forceAddAlpha);
		}
		return super.doColorify(image, color, how, forceAddAlpha);
	}

	@Override
	protected Image doColorifyMulti(Image imageToUse, Map<Integer, Color> colorMap, Image colorIndexes, ColorifyAlgorithm how,
			nortantis.geom.IntPoint where)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(imageToUse, colorIndexes) && how != ColorifyAlgorithm.none)
		{
			return SkiaShaderOps.colorifyMulti(imageToUse, colorMap, colorIndexes, how);
		}
		return super.doColorifyMulti(imageToUse, colorMap, colorIndexes, how, where);
	}

	@Override
	protected Image doBlur(Image image, int blurLevel, boolean maximizeContrast, boolean padImageToAvoidWrapping)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(image) && image.getType() != ImageType.Grayscale16Bit)
		{
			float sigma = (float) getStandardDeviationSizeForGaussianKernel(blurLevel);
			return SkiaShaderOps.blur(image, sigma, padImageToAvoidWrapping, maximizeContrast);
		}
		return super.doBlur(image, blurLevel, maximizeContrast, padImageToAvoidWrapping);
	}

	@Override
	protected Image doBlurAndScale(Image image, int blurLevel, float scale, boolean padImageToAvoidWrapping)
	{
		if (SkiaShaderOps.shouldUseSkiaShaders(image) && image.getType() != ImageType.Grayscale16Bit)
		{
			float sigma = (float) getStandardDeviationSizeForGaussianKernel(blurLevel);
			return SkiaShaderOps.blurAndScale(image, sigma, padImageToAvoidWrapping, scale);
		}
		return super.doBlurAndScale(image, blurLevel, scale, padImageToAvoidWrapping);
	}
}
