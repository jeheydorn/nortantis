package nortantis.editor;

public class MapChange
{
	MapEdits edits;
	UpdateType updateType;
	EditorTool toolThatMadeChange; // TODO use this to undo/redo using the correct tool and switch to that tool.
	
	public MapChange(MapEdits edits, UpdateType updateType, EditorTool toolThatMadeChange)
	{
		this.edits = edits;
		this.updateType = updateType;
		this.toolThatMadeChange = toolThatMadeChange;
	}
}
