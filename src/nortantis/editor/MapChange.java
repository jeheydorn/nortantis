package nortantis.editor;

import nortantis.MapSettings;
import nortantis.swing.EditorTool;
import nortantis.swing.UpdateType;

public class MapChange
{
	public MapSettings settings;
	public UpdateType updateType;
	public EditorTool toolThatMadeChange;
	
	public MapChange(MapSettings settings, UpdateType updateType, EditorTool toolThatMadeChange)
	{
		this.settings = settings;
		this.updateType = updateType;
		this.toolThatMadeChange = toolThatMadeChange;
	}
}
