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
	/**
	 * Whether this change applied a theme. Undoing or redoing such a change restores the styles for new text from the restored settings,
	 * while undoing or redoing any other change leaves them as they are.
	 */
	public final boolean isApplyTheme;

	public MapChange(MapSettings settings, UpdateType updateType, EditorTool toolThatMadeChange, Runnable preRun)
	{
		this(settings, updateType, toolThatMadeChange, preRun, false);
	}

	public MapChange(MapSettings settings, UpdateType updateType, EditorTool toolThatMadeChange, Runnable preRun, boolean isApplyTheme)
	{
		this.settings = settings;
		this.updateType = updateType;
		this.toolThatMadeChange = toolThatMadeChange;
		this.preRun = preRun;
		this.isApplyTheme = isApplyTheme;
	}
}
