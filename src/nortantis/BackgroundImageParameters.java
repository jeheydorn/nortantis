package nortantis;

import java.nio.file.Path;
import java.util.Random;

import nortantis.platform.Image;
import nortantis.platform.ImageHelper;

/**
 * Everything that decides one of the images a background generates from random noise: either a fractal, or a texture synthesized at the
 * background's size.
 *
 * @param textureArtPack
 *            The art pack the texture comes from. Unused for a fractal.
 * @param texturePath
 *            The texture to synthesize from, or null for a fractal.
 * @param convertTextureToGrayscale
 *            Whether to synthesize from a grayscale copy of the texture, for a background that will be colorized.
 */
public record BackgroundImageParameters(long backgroundRandomSeed, int width, int height, String textureArtPack, String customArtPackPath, Path texturePath,
		boolean convertTextureToGrayscale)
{
	public Image create()
	{
		if (texturePath == null)
		{
			final float fractalPower = 1.3f;
			return FractalBGGenerator.generate(new Random(backgroundRandomSeed), fractalPower, width, height, 0.75f);
		}

		Image texture;
		try
		{
			texture = ImageCache.getInstance(textureArtPack, customArtPackPath).getImageFromFile(texturePath);
		}
		catch (RuntimeException e)
		{
			throw new RuntimeException("Unable to read the texture image file name \"" + texturePath + "\"", e);
		}
		return BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(backgroundRandomSeed),
				convertTextureToGrayscale ? ImageHelper.getInstance().convertToGrayscale(texture) : texture, height, width);
	}
}
