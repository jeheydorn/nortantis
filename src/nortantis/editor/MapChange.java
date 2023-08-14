package nortantis.editor;

import nortantis.swing.EditorTool;
import nortantis.swing.MapEdits;
import nortantis.swing.UpdateType;

public class MapChange
{
	public MapEdits edits;
	public UpdateType updateType;
	public EditorTool toolThatMadeChange;
	
	public MapChange(MapEdits edits, UpdateType updateType, EditorTool toolThatMadeChange)
	{
		this.edits = edits;
		this.updateType = updateType;
		this.toolThatMadeChange = toolThatMadeChange;
	}
}
