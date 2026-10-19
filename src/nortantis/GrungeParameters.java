package nortantis;

import java.util.Random;

import nortantis.platform.Image;
import nortantis.platform.ImageHelper;

/**
 * Everything that decides a map's grunge.
 */
public record GrungeParameters(long backgroundRandomSeed, int width, int height, int grungeWidth, double resolution)
{
	/**
	 * Creates the grunge these parameters describe.
	 *
	 * @param checkForCancel
	 *            Called between steps. Throws if the map's creation was cancelled.
	 */
	public Image createGrunge(Runnable checkForCancel)
	{
		// 104567 is an arbitrary number added so that the grunge is not the same pattern as the background.
		final float fractalPower = 1.3f;
		Image grunge = FractalBGGenerator.generate(new Random(backgroundRandomSeed + 104567), fractalPower, width, height, 0.75f);

		checkForCancel.run();

		// Whiten the middle of clouds.
		ImageHelper.getInstance().darkenMiddleOfImage(grunge, grungeWidth, resolution, false);

		return grunge;
	}
}
