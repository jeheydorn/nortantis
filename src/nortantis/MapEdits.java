package nortantis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores edits made by a user to a map. These are stored as modifications from the generated content.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapEdits implements Serializable
{
	/**
	 * Text the user has edited, added, moved, or rotated. The key is the text id.
	 */
	List<MapText> text; 
		
	public MapEdits()
	{
		text = new ArrayList<>();
	}

}
