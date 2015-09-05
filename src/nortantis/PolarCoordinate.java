package nortantis;

import hoten.geom.Point;

public class PolarCoordinate 
{
	/**
	 * In radians.
	 */
	public double angle;
	
	public double radius;
	
	public PolarCoordinate(double angleInRadians, double radius)
	{
		this.angle = angleInRadians;
		this.radius = radius;
	}
	
	public PolarCoordinate(PolarCoordinate velocity)
	{
		this.angle = velocity.angle;
		this.radius = velocity.radius;
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
