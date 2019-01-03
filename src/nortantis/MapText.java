package nortantis;

import java.awt.geom.Area;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import hoten.geom.Point;

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
	 * The (possibly rotated) bounding boxes of the text. This only has size 2 areas if the text has 2 lines.
	 */
	public transient List<Area> areas;
	
	public TextType type;
	/**
	 * If the user has rotated the text, then this stores the angle. 0 means horizontal.
	 */
	public double angle;
	/**
	 * If the user has moved the text, then this store the location. null means let the generator determine the location.
	 * For text that can be rotated, the text will be draw such that the center of it's bounding box is at this location.
	 * For text that cannot be rotated (title and region names), the bounding box of the text will be determined by
	 * font metrics added to this location.
	 */
	public Point location;
	
	public MapText(String text, Point location, double angle, TextType type, List<Area> areas)
	{
		this.value = text;
		this.areas = areas;
		this.location = location;
		this.angle = angle;
		this.type = type;
	}

	public MapText(String text, Point location, double angle, TextType type)
	{
		this(text, location, angle, type, new ArrayList<Area>(0));
	}

	@Override
	public String toString()
	{
		return "MapText [value=" + value + ", type=" + type + ", angle=" + angle + ", location="
				+ location + "]";
	}

}
