package nortantis;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.io.Serializable;
import java.util.Objects;

import nortantis.geom.Point;

/**
 * Stores a piece of text (and data about it) drawn onto a map.
 * 
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapText implements Serializable
{
	public String value;
	/**
	 * The (possibly rotated) bounding boxes of the text.
	 */
	public transient Area line1Area;
	public transient Area line2Area;

	public TextType type;

	/**
	 * If the user has rotated the text, then this stores the angle. 0 means horizontal.
	 */
	public double angle;

	/**
	 * For text that has one line, this is the center of the text both horizontally and vertically. For text that has multiple lines, this
	 * is the horizontal center and the vertical center between the two lines.
	 * 
	 * This is stored in a resolution-invariant way, meaning the creating the map at a different resolution will give the same location
	 * (within the limits of floating point precision).
	 */
	public Point location;

	/**
	 * Holds the bounds of the text before rotation. This is not saved. Rather, it is populated by the generator when a map is first drawn.
	 * 
	 * Unlike the location, the bounds does vary with the resolution.
	 */
	public Rectangle line1Bounds;
	public Rectangle line2Bounds;

	public MapText(String text, Point location, double angle, TextType type, Area line1Area, Area line2Area, Rectangle line1Bounds,
			Rectangle line2Bounds)
	{
		this.value = text;
		this.line1Area = line1Area;
		this.line2Area = line2Area;
		this.location = location;
		this.angle = angle;
		this.type = type;
		this.line1Bounds = line1Bounds;
		this.line2Bounds = line2Bounds;
	}

	public MapText(String text, Point location, double angle, TextType type)
	{
		this(text, location, angle, type, null, null, null, null);
	}

	@Override
	public String toString()
	{
		return "MapText [value=" + value + ", type=" + type + ", angle=" + angle + ", location=" + location + "]";
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(angle, location, type, value);
	}

	public MapText deepCopy()
	{
		String value = this.value;
		Area line1Area = this.line1Area == null ? null : new Area(this.line1Area);
		Area line2Area = this.line2Area == null ? null : new Area(this.line2Area);
		TextType type = this.type;
		double angle = this.angle;
		Point location = new Point(this.location.x, this.location.y);
		Rectangle line1Bounds = this.line1Bounds == null ? null : new Rectangle(this.line1Bounds);
		Rectangle line2Bounds = this.line2Bounds == null ? null : new Rectangle(this.line2Bounds);

		return new MapText(value, location, angle, type, line1Area, line2Area, line1Bounds, line2Bounds);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MapText other = (MapText) obj;
		return Double.doubleToLongBits(angle) == Double.doubleToLongBits(other.angle) && Objects.equals(location, other.location)
				&& type == other.type && Objects.equals(value, other.value);
	}

}
