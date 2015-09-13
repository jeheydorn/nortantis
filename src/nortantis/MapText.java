package nortantis;

import hoten.geom.Point;

import java.awt.geom.Area;
import java.io.Serializable;
import java.util.List;

/**
 * Stores a piece of text (and data about it) drawn onto a map.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapText implements Serializable
{
	int id;
	String text;
	/**
	 * The (possibly rotated) bounding boxes of the text. This only has size 2 if the text has 2 lines.
	 */
	transient List<Area> areas;
	
	TextType type;
	/**
	 * If the user has rotated the text, then this stores the angle. null means let the generated determine the angle.
	 * Zero means horizontal.
	 */
	Double angle;
	/**
	 * If the user has moved the text, then this store the location. null means let the generator determine the location.
	 * The text will be draw such that the center of it's bounding box is at this location.
	 */
	Point location;
	
	public MapText(int id, String text, List<Area> areas)
	{
		this.id = id;
		this.text = text;
		this.areas = areas;
	}
}
