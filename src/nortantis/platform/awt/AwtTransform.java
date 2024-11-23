package nortantis.platform.awt;

import java.awt.geom.AffineTransform;
import java.util.Objects;

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
		return new AwtTransform(new AffineTransform(((AwtTransform) this).transform));
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(transform);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		AwtTransform other = (AwtTransform) obj;
		return Objects.equals(transform, other.transform);
	}
}
