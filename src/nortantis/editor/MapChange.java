package nortantis.editor;

import nortantis.MapSettings;
import nortantis.swing.EditorTool;
import nortantis.swing.UpdateType;

public class MapChange
{
	public MapSettings settings;
	public UpdateType updateType;
	public EditorTool toolThatMadeChange;
	public Runnable preRun;

	public MapChange(MapSettings settings, UpdateType updateType, EditorTool toolThatMadeChange, Runnable preRun)
	{
		this.settings = settings;
		this.updateType = updateType;
		this.toolThatMadeChange = toolThatMadeChange;
		this.preRun = preRun;
	}
}
