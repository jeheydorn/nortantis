package nortantis.swing;

import java.util.ArrayDeque;
import java.util.Stack;

import nortantis.MapSettings;

public class Undoer
{
	private ArrayDeque<MapChange> undoStack;
	private Stack<MapChange> redoStack;
	private MapSettings settings;
	public MapEdits copyOfEditsWhenEditorWasOpened;
	private MainWindow mainWindow;
	private final float maxUndoLevels = 150;
	private ToolsPanel toolsPanel;

	public Undoer(MapSettings settings, MainWindow mainWindow, ToolsPanel toolsPanel)
	{
		undoStack = new ArrayDeque<>();
		redoStack = new Stack<>();
		this.settings = settings;
		this.mainWindow = mainWindow;
		this.toolsPanel = toolsPanel;
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
		MapEdits prevEdits = undoStack.isEmpty() ? copyOfEditsWhenEditorWasOpened : undoStack.peek().edits;
		if (settings.edits.equals(prevEdits))
		{
			// Don't create an undo point if nothing changed.
			return;
		}
		
		redoStack.clear();
		undoStack.push(new MapChange(settings.edits.deepCopy(), updateType, tool));
		
		// Limit the size of undoStack to prevent memory errors. Each undo point is about 2 MBs.
		while(undoStack.size() > maxUndoLevels)
		{
			undoStack.removeLast();
		}
		
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
		mainWindow.mapParts.textDrawer.setMapTexts(settings.edits.text);
		
		if (change.toolThatMadeChange != null)
		{
			if (toolsPanel.currentTool != change.toolThatMadeChange)
			{
				toolsPanel.handleToolSelected(change.toolThatMadeChange);
			}

			change.toolThatMadeChange.onAfterUndoRedo(change);
		}
		else
		{
			// This happens if you undo a change not associated with any particular tool, such as Clear Entire Map.
			toolsPanel.currentTool.onAfterUndoRedo(change);
		}
		
		//mainWindow.updateEditsPointerInRunSwing(); TODO	
	}
	
	public void redo()
	{
		MapEdits prevEdits = settings.edits;
		MapChange change = redoStack.pop();
		undoStack.push(change);
		settings.edits = undoStack.peek().edits.deepCopy();
		
		// Keep the collection of text edits being drawn in sync with the settings
		mainWindow.mapParts.textDrawer.setMapTexts(settings.edits.text);

		MapChange changeWithPrevEdits = new MapChange(prevEdits, change.updateType, change.toolThatMadeChange);
		if (change.toolThatMadeChange != null)
		{
			// Switch to the tool that made the change.
			if (toolsPanel.currentTool != change.toolThatMadeChange)
			{
				toolsPanel.handleToolSelected(change.toolThatMadeChange);
			}
			
			change.toolThatMadeChange.onAfterUndoRedo(changeWithPrevEdits);
		}
		else
		{
			// This happens if you redo a change not associated with any particular tool, such as Clear Entire Map.
			toolsPanel.currentTool.onAfterUndoRedo(changeWithPrevEdits);
		}
		
		// mainWindow.updateEditsPointerInRunSwing(); TODO
	}
	
	public void updateUndoRedoEnabled()
	{		
		boolean undoEnabled = undoStack.size() > 0;
		mainWindow.undoButton.setEnabled(undoEnabled);
		boolean redoEnabled = redoStack.size() > 0;
		mainWindow.redoButton.setEnabled(redoEnabled);
	}

}
