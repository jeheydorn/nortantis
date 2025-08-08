package nortantis;

import java.io.Serializable;
import java.util.Objects;

import nortantis.geom.Point;
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
	 * The (possibly rotated) bounding boxes of the text. This is populated when text is drawn, and is not stored to disk.
	 */
	public RotatedRectangle line1Bounds;
	public RotatedRectangle line2Bounds;

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
	
	public double curvature;
	public int spacing;

	public MapText(String text, Point location, double angle, TextType type, RotatedRectangle line1Bounds, RotatedRectangle line2Bounds,
			LineBreak lineBreak, Color colorOverride, Color boldBackgroundColorOverride, double curvature, int spacing)
	{
		this.value = text;
		this.line1Bounds = line1Bounds;
		this.line2Bounds = line2Bounds;
		this.location = location;
		this.angle = angle;
		this.type = type;
		this.lineBreak = lineBreak;
		this.colorOverride = colorOverride;
		this.boldBackgroundColorOverride = boldBackgroundColorOverride;
		this.curvature = curvature;
		this.spacing = spacing;
	}

	public MapText(String text, Point location, double angle, TextType type, LineBreak lineBreak, Color colorOverride,
			Color boldBackgroundColorOverride, double curvature, int spacing)
	{
		this(text, location, angle, type, null, null, lineBreak, colorOverride, boldBackgroundColorOverride, curvature, spacing);
	}


	public MapText deepCopy()
	{
		String value = this.value;
		RotatedRectangle line1Bounds = this.line1Bounds;
		RotatedRectangle line2Bounds = this.line2Bounds;
		TextType type = this.type;
		double angle = this.angle;
		Point location = new Point(this.location.x, this.location.y);
		LineBreak lineBreak = this.lineBreak;
		Color colorOverride = this.colorOverride;
		Color boldBackgroundColorOverride = this.boldBackgroundColorOverride;

		return new MapText(value, location, angle, type, line1Bounds, line2Bounds, lineBreak, colorOverride, boldBackgroundColorOverride, curvature, spacing);
	}


	/**
	 * See equals(...) for a list of fields to exclude.
	 */
	@Override
	public int hashCode()
	{
		return Objects.hash(angle, boldBackgroundColorOverride, colorOverride, curvature, lineBreak, location, spacing, type, value);
	}

	/**
	 * Excludes fields that get filled in on the fly during map creation: line1Bounds, line2Bounds
	 */

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
		MapText other = (MapText) obj;
		return Double.doubleToLongBits(angle) == Double.doubleToLongBits(other.angle)
				&& Objects.equals(boldBackgroundColorOverride, other.boldBackgroundColorOverride)
				&& Objects.equals(colorOverride, other.colorOverride)
				&& Double.doubleToLongBits(curvature) == Double.doubleToLongBits(other.curvature) && lineBreak == other.lineBreak
				&& Objects.equals(location, other.location) && spacing == other.spacing && type == other.type
				&& Objects.equals(value, other.value);
	}

	@Override
	public String toString()
	{
		return "MapText [value=" + value + ", type=" + type + ", angle=" + angle + ", location=" + location + ", lineBreak=" + lineBreak
				+ ", colorOverride=" + colorOverride + ", boldBackgroundColorOverride=" + boldBackgroundColorOverride + ", curvature="
				+ curvature + ", spacing=" + spacing + "]";
	}
	
}
