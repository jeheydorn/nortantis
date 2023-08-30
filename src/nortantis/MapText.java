package nortantis;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nortantis.graph.geom.Point;

/**
 * Stores a piece of text (and data about it) drawn onto a map.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapText implements Serializable
{
	public String value;
	/**
	 * The (possibly rotated) bounding boxes of the text. This usually has only 1 area.
	 */
	public transient Area area;
	
	public TextType type;
	
	/**
	 * If the user has rotated the text, then this stores the angle. 0 means horizontal.
	 */
	public double angle;
	
	/**
	 * If the user has moved the text, then this store the location. null means let the generator determine the location.
	 * For text that can be rotated, the text will be draw such that the center of it's bounding box is at this location.
	 * For text that cannot be rotated (title and region names), the bounding box of the text will be determined by
	 * font metrics added to this location. TODO Update this description when I make all text rotatable.
	 * 
	 * This is stored in a resolution-invariant way, meaning the creating the map at a different resolution will give the same location
	 * (although it is subject to floating point precision error).
	 */
	public Point location;
	
	/**
	 * Holds the bounds of the text before rotation.
	 * This is not saved. Rather, it is populated by the editor when a map is first drawn.
	 * 
	 * Unlike the location, the bounds does vary with the resolution.
	 */
	public Rectangle bounds;
	
	public MapText(String text, Point location, double angle, TextType type, Area area, Rectangle bounds)
	{
		this.value = text;
		this.area = area;
		this.location = location;
		this.angle = angle;
		this.type = type;
		this.bounds = bounds;
	}

	public MapText(String text, Point location, double angle, TextType type)
	{
		this(text, location, angle, type, null, null);
	}

	@Override
	public String toString()
	{
		return "MapText [value=" + value + ", type=" + type + ", angle=" + angle + ", location="
				+ location + "]";
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(angle, location, type, value);
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
