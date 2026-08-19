package nortantis.platform;

import nortantis.geom.IntPoint;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.ThreadHelper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Blurs from inside jobs that are themselves running on {@link ThreadHelper}'s pools, which is what lazily built icon shading masks do.
 *
 * A blur parallelizes internally, so nesting it this way makes it ask for threads while already holding one. That is only safe because
 * {@code GaussianBlur} runs its own work on a fork-join pool, where a thread waiting on work it submitted helps run that work instead of
 * blocking. Waiting on a pool of a fixed size from inside that same pool deadlocks once every thread in it is waiting, which this project has
 * had to fix before. This test hangs and then fails rather than passing if that ever comes back.
 */
public class BlurNestingTest
{
	private static final int blurLevel = 80;
	private static final int maskSize = 512;

	@BeforeAll
	public static void setup()
	{
		PlatformFactory.setInstance(new AwtFactory());
	}

	@Test
	public void blurringFromInsideParallelJobsCompletes() throws Exception
	{
		int jobCount = ThreadHelper.getInstance().getThreadCount() * 2;
		List<Runnable> jobs = new ArrayList<>();
		for (int i = 0; i < jobCount; i++)
		{
			jobs.add(() ->
			{
				try (Image mask = createCoastlineLikeMask(maskSize, maskSize, 3))
				{
					closeQuietly(GaussianBlur.blurAndScale(mask, blurLevel, 0.35f, true, GaussianBlur.Algorithm.threeBoxes));
					closeQuietly(GaussianBlur.blur(mask, blurLevel, true, true, GaussianBlur.Algorithm.separableGaussian));
				}
			});
		}

		// Run on a thread of its own so that a hang can be caught and reported instead of stalling the whole test run.
		Thread runner = new Thread(() ->
		{
			ThreadHelper.getInstance().processInParallel(jobs, true);
			ThreadHelper.getInstance().processInParallel(jobs, false);
		});
		runner.start();
		runner.join(120_000);
		if (runner.isAlive())
		{
			throw new AssertionError("Blurring from inside ThreadHelper's pools did not finish.");
		}
	}

	/**
	 * Thin wandering white lines on black, which is the shape of the masks that get blurred during a draw.
	 */
	private static Image createCoastlineLikeMask(int width, int height, long seed)
	{
		Image mask = Image.create(width, height, ImageType.Binary);
		Random rand = new Random(seed);
		try (Painter p = mask.createPainter(DrawQuality.High))
		{
			p.setColor(Color.white);
			p.setBasicStroke(1f);
			for (int line = 0; line < 12; line++)
			{
				List<IntPoint> points = new ArrayList<>();
				double x = rand.nextInt(width);
				double y = rand.nextInt(height);
				double angle = rand.nextDouble() * Math.PI * 2;
				for (int step = 0; step < 200; step++)
				{
					points.add(new IntPoint((int) x, (int) y));
					angle += (rand.nextDouble() - 0.5) * 0.7;
					x += Math.cos(angle) * (width / 60.0);
					y += Math.sin(angle) * (height / 60.0);
				}
				p.drawPolyline(points);
			}
		}
		return mask;
	}

	private static void closeQuietly(Image image)
	{
		if (image != null)
		{
			image.close();
		}
	}
}
