package nortantis.platform;

import nortantis.platform.ImageHelper.BlurAlgorithm;
import nortantis.platform.awt.AwtFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Checks which way of blurring {@link ImageHelper} picks for a given blur level. Narrow blurs are meant to be convolved with the sampled
 * kernel directly whichever approximation is configured, because there the approximations are both less faithful and no cheaper.
 */
public class BlurAlgorithmSelectionTest
{
	/**
	 * Below the level at which {@link ImageHelper} starts approximating, and above it.
	 */
	private static final int narrowBlurLevel = 4;
	private static final int wideBlurLevel = 20;

	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new AwtFactory());
	}

	@Test
	public void narrowBlursConvolveTheKernelDirectly()
	{
		if (ImageHelper.getBlurAlgorithm() == BlurAlgorithm.fft)
		{
			// FFT convolves the whole kernel at every level, so there is nothing to fall back to.
			return;
		}

		try (Image mask = createMask();
				Image actual = ImageHelper.getInstance().blur(mask, narrowBlurLevel, true, true);
				Image exact = GaussianBlur.blur(mask, narrowBlurLevel, true, true, GaussianBlur.Algorithm.separableGaussian))
		{
			assertEquals(0, countDifferingPixels(exact, actual), "A narrow blur should convolve the sampled kernel directly.");
		}
	}

	/**
	 * The approximations differ from direct convolution by enough to tell them apart, so a wide blur that convolved the kernel directly would
	 * show up here as no difference at all.
	 */
	@Test
	public void widerBlursUseTheConfiguredApproximation()
	{
		if (ImageHelper.getBlurAlgorithm() == BlurAlgorithm.fft || ImageHelper.getBlurAlgorithm() == BlurAlgorithm.separableGaussian)
		{
			return;
		}

		try (Image mask = createMask();
				Image actual = ImageHelper.getInstance().blur(mask, wideBlurLevel, true, true);
				Image exact = GaussianBlur.blur(mask, wideBlurLevel, true, true, GaussianBlur.Algorithm.separableGaussian))
		{
			assertNotEquals(0, countDifferingPixels(exact, actual), "A wide blur should use the configured approximation.");
		}
	}

	private static Image createMask()
	{
		Image mask = Image.create(96, 96, ImageType.Grayscale8Bit);
		try (Painter p = mask.createPainter(DrawQuality.High))
		{
			p.setColor(Color.white);
			p.fillRect(20, 20, 40, 12);
			p.fillRect(30, 50, 8, 30);
			p.fillRect(60, 44, 20, 20);
		}
		return mask;
	}

	private static long countDifferingPixels(Image expected, Image actual)
	{
		long differing = 0;
		try (PixelReader expectedPixels = expected.createPixelReader(); PixelReader actualPixels = actual.createPixelReader())
		{
			for (int y = 0; y < expected.getHeight(); y++)
			{
				for (int x = 0; x < expected.getWidth(); x++)
				{
					if (expectedPixels.getGrayLevel(x, y) != actualPixels.getGrayLevel(x, y))
					{
						differing++;
					}
				}
			}
		}
		return differing;
	}
}
