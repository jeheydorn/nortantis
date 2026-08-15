package nortantis.geom;

public class PolarCoordinate
{
	/**
	 * In radians.
	 */
	public final double angle;

	public final double radius;

	public PolarCoordinate(double angleInRadians, double radius)
	{
		this.angle = angleInRadians;
		this.radius = radius;
	}

	public Point toCartesian()
	{
		return new Point(radius * Math.cos(angle), radius * Math.sin(angle));
	}

	@Override
	public String toString()
	{
		return "angle: " + angle + ", r: " + radius;
	}
}
