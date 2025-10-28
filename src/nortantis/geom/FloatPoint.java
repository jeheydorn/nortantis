package nortantis.geom;

import java.util.Objects;

public class FloatPoint implements Comparable<FloatPoint>
{
	public static float distance(FloatPoint _coord, FloatPoint _coord0)
	{
		return (float) Math.sqrt((_coord.x - _coord0.x) * (_coord.x - _coord0.x) + (_coord.y - _coord0.y) * (_coord.y - _coord0.y));
	}

	public float x, y;

	public FloatPoint(float x, float y)
	{
		this.x = x;
		this.y = y;
	}

	public FloatPoint(FloatPoint other)
	{
		this.x = other.x;
		this.y = other.y;
	}

	public float distanceTo(FloatPoint other)
	{
		return distance(this, other);
	}

	/**
	 * Returns a new FloatPoint whose value is this FloatPoint minus other.
	 */
	public FloatPoint subtract(FloatPoint other)
	{
		return new FloatPoint(x - other.x, y - other.y);
	}

	public FloatPoint add(FloatPoint other)
	{
		return new FloatPoint(x + other.x, y + other.y);
	}
	
	public FloatPoint add(IntPoint other)
	{
		return new FloatPoint(x + other.x, y + other.y);
	}

	public FloatPoint add(float x, float y)
	{
		return new FloatPoint(this.x + x, this.y + y);
	}

	public FloatPoint mult(float value)
	{
		return new FloatPoint(x * value, y * value);
	}

	public FloatPoint mult(float xScale, float yScale)
	{
		return new FloatPoint(x * xScale, y * yScale);
	}

	public IntPoint toIntPoint()
	{
		return new IntPoint((int) x, (int) y);
	}
	
	public IntPoint toIntPointRounded()
	{
		return new IntPoint((int) Math.round(x), (int) Math.round(y));
	}

	@Override
	public String toString()
	{
		return "(" + x + ", " + y + ")";
	}

	public String toJson()
	{
		return "(" + x + ", " + y + ")";
	}

	public static FloatPoint fromJSonValue(String value)
	{
		String[] pieces = value.replace("(", "").replace(")", "").split(",");
		float x = Float.parseFloat(pieces[0]);
		float y = Float.parseFloat(pieces[1]);
		return new FloatPoint(x, y);
	}

	public float length()
	{
		return (int) Math.sqrt(x * x + y * y);
	}

	public static FloatPoint interpolate(FloatPoint p1, FloatPoint p2, float c)
	{
		return new FloatPoint(c * (p1.x) + (1 - c) * p2.x, c * (p1.y) + (1 - c) * p2.y);
	}

	public FloatPoint rotate(FloatPoint pivot, float angle)
	{
		float dx = this.x - pivot.x;
		float dy = this.y - pivot.y;
		float newX = pivot.x + dx * (float) Math.cos(angle) - dy * (float) Math.sin(angle);
		float newY = pivot.y + dx * (float) Math.sin(angle) + dy * (float) Math.cos(angle);
		return new FloatPoint(newX, newY);
	}

	@Override
	public int compareTo(FloatPoint other)
	{
		int c2 = Float.compare(y, other.y);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;

		int c1 = Float.compare(x, other.x);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;

		return 0;
	}

	public boolean isCloseEnough(FloatPoint other)
	{
		final float threshold = 0.00001f;
		return Math.abs(x - other.x) <= threshold && Math.abs(y - other.y) <= threshold;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y);
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
		FloatPoint other = (FloatPoint) obj;
		return Float.floatToIntBits(x) == Float.floatToIntBits(other.x) && Float.floatToIntBits(y) == Float.floatToIntBits(other.y);
	}


}
