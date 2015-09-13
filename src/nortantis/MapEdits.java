package nortantis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stores edits made by a user to a map. These are stored as modifications from the generated content.
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapEdits implements Serializable
{
	/**
	 * The ids of text which was not drawn when text was generated because it would have overlapped another
	 * text box on the map. It is necessary to store this to avoid the user seeing new text boxes suddenly appear
	 * when the remove an existing text box.
	 */
	Set<Integer> hiddenTextIds;
	/**
	 * Text the user has edited. The key is the text id.
	 */
	Map<Integer, MapText> editedText;
	
	/**
	 * Text added using the Add tool. The key is the text id.
	 */
	Map<Integer, MapText> addedText;
	
	public MapEdits()
	{
		hiddenTextIds = new TreeSet<>();
		editedText = new TreeMap<>();
		addedText = new TreeMap<>();
	}

}
