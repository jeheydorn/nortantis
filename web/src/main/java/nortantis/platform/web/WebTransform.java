package nortantis.platform.web;

import java.util.Arrays;

import nortantis.platform.Transform;

/**
 * An affine transform, stored like java.awt.geom.AffineTransform: x' = m00 * x + m01 * y + m02, y' = m10 * x + m11 * y + m12.
 */
public class WebTransform extends Transform
{
	final double m00, m01, m02, m10, m11, m12;

	WebTransform(double m00, double m01, double m02, double m10, double m11, double m12)
	{
		this.m00 = m00;
		this.m01 = m01;
		this.m02 = m02;
		this.m10 = m10;
		this.m11 = m11;
		this.m12 = m12;
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(new double[] { m00, m01, m02, m10, m11, m12 });
	}

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof WebTransform))
		{
			return false;
		}
		WebTransform o = (WebTransform) obj;
		return m00 == o.m00 && m01 == o.m01 && m02 == o.m02 && m10 == o.m10 && m11 == o.m11 && m12 == o.m12;
	}
}
