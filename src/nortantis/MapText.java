package nortantis;

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
	
	public MapText(int id, String text, List<Area> areas)
	{
		this.id = id;
		this.text = text;
		this.areas = areas;
	}
}
