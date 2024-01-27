package nortantis.platform.awt;

import java.awt.geom.AffineTransform;

import nortantis.platform.Transform;

public class AwtTransform extends Transform
{
	AffineTransform transform;
	
	public AwtTransform(AffineTransform transform)
	{
		this.transform = transform;
	}

	@Override
	public Transform copy()
	{
		return new AwtTransform(new AffineTransform(((AwtTransform)this).transform));
	}

	@Override
	public void rotate(double angle, double x, double y)
	{
		transform.rotate(angle, x, y);
	}
}
