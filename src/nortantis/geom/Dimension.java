package nortantis.geom;

import java.util.Objects;

public class Dimension implements Comparable<Dimension>
{
	public final double width, height;

	public Dimension(double width, double height)
	{
		this.width = width;
		this.height = height;
	}

	public IntDimension toIntDimension()
	{
		return new IntDimension((int) width, (int) height);
	}

	public IntDimension roundToIntDimension()
	{
		return new IntDimension((int) Math.round(width), (int) Math.round(height));
	}

	@Override
	public String toString()
	{
		return width + ", " + height;
	}

	public Dimension mult(double scale)
	{
		return new Dimension(width * scale, height * scale);
	}

	@Override
	public int compareTo(Dimension other)
	{
		int c1 = Double.compare(width, other.width);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;

		int c2 = Double.compare(height, other.height);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;

		return 0;
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
		Dimension other = (Dimension) obj;
		return Double.doubleToLongBits(height) == Double.doubleToLongBits(other.height)
				&& Double.doubleToLongBits(width) == Double.doubleToLongBits(other.width);
	}

}
