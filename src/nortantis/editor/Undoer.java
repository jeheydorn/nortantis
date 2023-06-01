package nortantis.editor;

import java.util.Stack;

import nortantis.MapSettings;

public class Undoer
{
	private Stack<MapChange> undoStack;
	private Stack<MapChange> redoStack;
	private MapSettings settings;
	public MapEdits copyOfEditsWhenEditorWasOpened;
	private EditorFrame editorFrame;

	public Undoer(MapSettings settings, EditorFrame editorFrame)
	{
		undoStack = new Stack<>();
		redoStack = new Stack<>();
		this.settings = settings;
		this.editorFrame = editorFrame;
	}
	
	/***
	 * Sets a point to which the user can undo changes. Assumes the last change was made using an incremental update.
	 * @param tool The tool that is setting the undo point.
	 */
	protected void setUndoPoint(EditorTool tool)
	{
		setUndoPoint(UpdateType.Incremental, tool);
	}
	
	/***
	 * Sets a point to which the user can undo changes. 
	 * @param updateType The type of update that was last made. 
	 * @param tool The tool that is setting the undo point.
	 */
	protected void setUndoPoint(UpdateType updateType, EditorTool tool)
	{
		redoStack.clear();
		undoStack.push(new MapChange(settings.edits.deepCopy(), updateType, tool));
		updateUndoRedoEnabled();
	}
	
	public void undo()
	{
		MapChange change = undoStack.pop();
		redoStack.push(change);
		if (undoStack.isEmpty())
		{
			settings.edits = copyOfEditsWhenEditorWasOpened.deepCopy();
		}
		else
		{
			settings.edits = undoStack.peek().edits.deepCopy();	
		}
		
		// Keep the collection of text edits being drawn in sync with the settings
		editorFrame.mapParts.textDrawer.setMapTexts(settings.edits.text);
		
		if (change.toolThatMadeChange != null)
		{
			if (editorFrame.currentTool != change.toolThatMadeChange)
			{
				editorFrame.handleToolSelected(change.toolThatMadeChange);
			}

			change.toolThatMadeChange.onAfterUndoRedo(change.edits);
		}
	}
	
	public void redo()
	{
		MapEdits prevEdits = settings.edits;
		MapChange change = redoStack.pop();
		undoStack.push(change);
		settings.edits = undoStack.peek().edits.deepCopy();
		
		// Keep the collection of text edits being drawn in sync with the settings
		editorFrame.mapParts.textDrawer.setMapTexts(settings.edits.text);

		// TODO switch to the tool that made the change.
		if (change.toolThatMadeChange != null)
		{
			if (editorFrame.currentTool != change.toolThatMadeChange)
			{
				editorFrame.handleToolSelected(change.toolThatMadeChange);
			}
			
			change.toolThatMadeChange.onAfterUndoRedo(prevEdits);
		}
	}
	
	public void updateUndoRedoEnabled()
	{		
		boolean undoEnabled = undoStack.size() > 0;
		editorFrame.undoButton.setEnabled(undoEnabled);
		boolean redoEnabled = redoStack.size() > 0;
		editorFrame.redoButton.setEnabled(redoEnabled);
	}

}
