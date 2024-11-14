package nortantis.geom;

import java.util.Objects;

public class IntDimension
{
	public final int width;
	public final int height;

	public IntDimension(int width, int height)
	{
		this.width = width;
		this.height = height;
	}

	public Dimension toDimension()
	{
		return new Dimension(width, height);
	}

	@Override
	public String toString()
	{
		return "(" + width + ", " + height + ")";
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(height, width);
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
		IntDimension other = (IntDimension) obj;
		return height == other.height && width == other.width;
	}

}
