package nortantis;

import java.io.Serializable;
import java.util.Objects;

import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.Color;

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
	public RotatedRectangle line1Area;
	public RotatedRectangle line2Area;

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
	 * Allows the user to override whether the text can be split into two lines.
	 */
	public LineBreak lineBreak;

	public Color colorOverride;
	public Color boldBackgroundColorOverride;

	/**
	 * Holds the bounds of the text before rotation. This is not saved. Rather, it is populated by the generator when a map is first drawn.
	 * 
	 * Unlike the location, the bounds does vary with the resolution.
	 */
	public Rectangle line1Bounds;
	public Rectangle line2Bounds;

	public MapText(String text, Point location, double angle, TextType type, RotatedRectangle line1Area, RotatedRectangle line2Area,
			Rectangle line1Bounds, Rectangle line2Bounds, LineBreak lineBreak, Color colorOverride, Color boldBackgroundColorOverride)
	{
		this.value = text;
		this.line1Area = line1Area;
		this.line2Area = line2Area;
		this.location = location;
		this.angle = angle;
		this.type = type;
		this.line1Bounds = line1Bounds;
		this.line2Bounds = line2Bounds;
		this.lineBreak = lineBreak;
		this.colorOverride = colorOverride;
		this.boldBackgroundColorOverride = boldBackgroundColorOverride;
	}

	public MapText(String text, Point location, double angle, TextType type, LineBreak lineBreak, Color colorOverride,
			Color boldBackgroundColorOverride)
	{
		this(text, location, angle, type, null, null, null, null, lineBreak, colorOverride, boldBackgroundColorOverride);
	}

	/**
	 * See equals(...) for a list of fields to exclude.
	 */
	@Override
	public int hashCode()
	{
		return Objects.hash(angle, location, type, value, lineBreak, colorOverride, boldBackgroundColorOverride);
	}

	public MapText deepCopy()
	{
		String value = this.value;
		RotatedRectangle line1Area = this.line1Area;
		RotatedRectangle line2Area = this.line2Area;
		TextType type = this.type;
		double angle = this.angle;
		Point location = new Point(this.location.x, this.location.y);
		Rectangle line1Bounds = this.line1Bounds;
		Rectangle line2Bounds = this.line2Bounds;
		LineBreak lineBreak = this.lineBreak;
		Color colorOverride = this.colorOverride;
		Color boldBackgroundColorOverride = this.boldBackgroundColorOverride;

		return new MapText(value, location, angle, type, line1Area, line2Area, line1Bounds, line2Bounds, lineBreak, colorOverride,
				boldBackgroundColorOverride);
	}

	/**
	 * Excludes fields that get filled in on the fly during map creation: line1Bounds, line2Bounds, line1Area, line2Area
	 */
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
				&& type == other.type && Objects.equals(value, other.value) && lineBreak == other.lineBreak
				&& Objects.equals(colorOverride, other.colorOverride)
				&& Objects.equals(boldBackgroundColorOverride, other.boldBackgroundColorOverride);
	}

	@Override
	public String toString()
	{
		return "MapText [value=" + value + ", type=" + type + ", angle=" + angle + ", location=" + location + ", lineBreak=" + lineBreak
				+ ", colorOverride=" + colorOverride + ", boldBackgroundColorOverride=" + boldBackgroundColorOverride + "]";
	}

}
