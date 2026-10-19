package org.teavm.classlib.java.util;

import org.teavm.classlib.java.util.random.TRandomGenerator;

/**
 * Replaces TeaVM's java.util.Random with one that gives exactly the numbers the JDK's does for the same seed, so a map generated in a
 * browser comes out the same as on the desktop. TeaVM's own version leaves some methods, such as nextInt(int), to RandomGenerator's
 * defaults, which use the seed differently.
 */
public class TRandom implements TRandomGenerator, java.io.Serializable
{
	private static final long serialVersionUID = 3905348978240129619L;
	private static final long multiplier = 0x5DEECE66DL;
	private static final int multiplierHigh = (int) (multiplier >>> 24);
	private static final int multiplierLow = (int) (multiplier & 0xFFFFFF);
	private static final int addend = 0xB;
	private static final long mask = (1L << 48) - 1;
	private static final double twoTo24 = 0x1.0p24;
	private static final double doubleUnit = 0x1.0p-53;

	// The 48-bit seed, as its high and low 24 bits. Browsers have no 64-bit integers, so TeaVM emulates long arithmetic slowly. Products of
	// 24-bit halves fit exactly in a double, so the seed advances with doubles instead, giving the same numbers as the JDK's long arithmetic.
	private int seedHigh;
	private int seedLow;
	private double nextNextGaussian;
	private boolean haveNextNextGaussian;

	public TRandom()
	{
		this((long) (Math.random() * Long.MAX_VALUE) ^ System.nanoTime());
	}

	public TRandom(long seed)
	{
		setSeed(seed);
	}

	public void setSeed(long seed)
	{
		long scrambled = (seed ^ multiplier) & mask;
		seedHigh = (int) (scrambled >>> 24);
		seedLow = (int) (scrambled & 0xFFFFFF);
		haveNextNextGaussian = false;
	}

	protected int next(int bits)
	{
		double lowProduct = (double) seedLow * multiplierLow + addend;
		int carry = (int) (lowProduct / twoTo24);
		int newLow = (int) (lowProduct - carry * twoTo24);
		double highProduct = (double) seedHigh * multiplierLow + (double) seedLow * multiplierHigh + carry;
		int newHigh = (int) (highProduct - (int) (highProduct / twoTo24) * twoTo24);
		seedHigh = newHigh;
		seedLow = newLow;

		int shift = 48 - bits;
		if (shift >= 24)
		{
			return newHigh >>> (shift - 24);
		}
		return (newHigh << (24 - shift)) | (newLow >>> shift);
	}

	public void nextBytes(byte[] bytes)
	{
		for (int i = 0, len = bytes.length; i < len;)
		{
			for (int rnd = nextInt(), n = Math.min(len - i, Integer.SIZE / Byte.SIZE); n-- > 0; rnd >>= Byte.SIZE)
			{
				bytes[i++] = (byte) rnd;
			}
		}
	}

	@Override
	public int nextInt()
	{
		return next(32);
	}

	@Override
	public int nextInt(int bound)
	{
		if (bound <= 0)
		{
			throw new IllegalArgumentException("bound must be positive");
		}
		int r = next(31);
		int m = bound - 1;
		if ((bound & m) == 0)
		{
			r = (int) ((bound * (long) r) >> 31);
		}
		else
		{
			for (int u = r; u - (r = u % bound) + m < 0; u = next(31))
			{
			}
		}
		return r;
	}

	/**
	 * The JDK's algorithm for this, from RandomSupport.boundedNextInt, which java.util.Random inherits.
	 */
	@Override
	public int nextInt(int origin, int bound)
	{
		if (origin >= bound)
		{
			throw new IllegalArgumentException("bound must be greater than origin");
		}
		int r = nextInt();
		int n = bound - origin;
		int m = n - 1;
		if ((n & m) == 0)
		{
			r = (r & m) + origin;
		}
		else if (n > 0)
		{
			for (int u = r >>> 1; u + m - (r = u % n) < 0; u = nextInt() >>> 1)
			{
			}
			r += origin;
		}
		else
		{
			while (r < origin || r >= bound)
			{
				r = nextInt();
			}
		}
		return r;
	}

	/**
	 * The JDK's algorithm for this, from RandomSupport.boundedNextLong, which java.util.Random inherits.
	 */
	@Override
	public long nextLong(long bound)
	{
		if (bound <= 0)
		{
			throw new IllegalArgumentException("bound must be positive");
		}
		long m = bound - 1;
		long r = nextLong();
		if ((bound & m) == 0L)
		{
			r &= m;
		}
		else
		{
			for (long u = r >>> 1; u + m - (r = u % bound) < 0L; u = nextLong() >>> 1)
			{
			}
		}
		return r;
	}

	@Override
	public long nextLong()
	{
		return ((long) (next(32)) << 32) + next(32);
	}

	@Override
	public boolean nextBoolean()
	{
		return next(1) != 0;
	}

	@Override
	public float nextFloat()
	{
		return next(24) / ((float) (1 << 24));
	}

	@Override
	public double nextDouble()
	{
		return (next(26) * 0x1.0p27 + next(27)) * doubleUnit;
	}

	@Override
	public double nextGaussian()
	{
		if (haveNextNextGaussian)
		{
			haveNextNextGaussian = false;
			return nextNextGaussian;
		}
		double v1, v2, s;
		do
		{
			v1 = 2 * nextDouble() - 1;
			v2 = 2 * nextDouble() - 1;
			s = v1 * v1 + v2 * v2;
		}
		while (s >= 1 || s == 0);
		double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
		nextNextGaussian = v2 * multiplier;
		haveNextNextGaussian = true;
		return v1 * multiplier;
	}
}
