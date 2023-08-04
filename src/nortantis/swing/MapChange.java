package nortantis.swing;

public class MapChange
{
	MapEdits edits;
	UpdateType updateType;
	EditorTool toolThatMadeChange;
	
	public MapChange(MapEdits edits, UpdateType updateType, EditorTool toolThatMadeChange)
	{
		this.edits = edits;
		this.updateType = updateType;
		this.toolThatMadeChange = toolThatMadeChange;
	}
}
