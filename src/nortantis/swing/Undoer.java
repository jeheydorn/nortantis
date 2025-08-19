package nortantis.swing;

import java.util.ArrayDeque;
import java.util.Stack;

import nortantis.MapSettings;
import nortantis.editor.MapChange;

public class Undoer
{
	private ArrayDeque<MapChange> undoStack;
	private Stack<MapChange> redoStack;
	private MapSettings copyOfSettingsWhenEditorWasOpened;
	private MainWindow mainWindow;
	private final float maxUndoLevels = 200;
	boolean enabled;

	public Undoer(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;
	}

	public boolean isInitialized()
	{
		return copyOfSettingsWhenEditorWasOpened != null;
	}

	public void initialize(MapSettings settings)
	{
		undoStack = new ArrayDeque<>();
		redoStack = new Stack<>();
		copyOfSettingsWhenEditorWasOpened = settings.deepCopy();
	}

	public void reset()
	{
		undoStack = null;
		redoStack = null;
		copyOfSettingsWhenEditorWasOpened = null;
	}

	public void setUndoPoint(UpdateType updateType, EditorTool tool)
	{
		setUndoPoint(updateType, tool, null);
	}

	/***
	 * Sets a point to which the user can undo changes.
	 * 
	 * @param updateType
	 *            The type of update that was last made.
	 * @param tool
	 *            The tool that is setting the undo point.
	 * @param preRun
	 *            Code to run in the foreground thread before drawing if this change undone or redone.
	 */
	public void setUndoPoint(UpdateType updateType, EditorTool tool, Runnable preRun)
	{
		if (!enabled)
		{
			return;
		}

		if (undoStack == null)
		{
			// This can happen even when not enabled if a map fails to draw and so reset has been called but initialize has not,
			// because the editor allows user to change settings so they can fix the issue that caused the map to fail to draw.
			// It will mean that the undo stack will not contain their change, but I'm okay with that.
			return;
		}

		MapSettings prevSettings = undoStack.isEmpty() ? copyOfSettingsWhenEditorWasOpened : undoStack.peek().settings;
		MapSettings currentSettings = mainWindow.getSettingsFromGUI(true);
		if (currentSettings.equals(prevSettings))
		{
			// Don't create an undo point if nothing changed.
			return;
		}

		redoStack.clear();
		undoStack.push(new MapChange(currentSettings, updateType, tool, preRun));

		// Limit the size of undoStack to prevent memory errors. Each undo point is about 2 MBs.
		while (undoStack.size() > maxUndoLevels)
		{
			undoStack.removeLast();
		}

		updateUndoRedoEnabled();
	}

	public void undo()
	{
		if (!enabled)
		{
			return;
		}

		if (undoStack == null)
		{
			return;
		}

		if (undoStack.size() == 0)
		{
			return;
		}

		mainWindow.toolsPanel.currentTool.onBeforeUndoRedo();

		MapChange changeToUndo = undoStack.pop();

		// The change to undo should use the latest settings rather than what came from undo stack so that we catch any
		// changes made after the latest undo point.
		changeToUndo.settings = mainWindow.getSettingsFromGUI(true);

		redoStack.push(changeToUndo);
		MapSettings settings;
		if (undoStack.isEmpty())
		{
			// This should not happen because the undoer should not be initialized until the edits are created.
			assert copyOfSettingsWhenEditorWasOpened.edits.isInitialized();

			settings = copyOfSettingsWhenEditorWasOpened.deepCopy();
			mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(settings, true, false);
		}
		else
		{
			settings = undoStack.peek().settings.deepCopy();
			mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(settings, true, false);
		}

		if (changeToUndo.toolThatMadeChange != null)
		{
			if (mainWindow.toolsPanel.currentTool == changeToUndo.toolThatMadeChange)
			{
				changeToUndo.toolThatMadeChange.onAfterUndoRedo();
			}
		}
		else
		{
			// This happens if you undo a change not associated with any particular tool, such as Clear Entire Map.
			mainWindow.toolsPanel.currentTool.onAfterUndoRedo();
		}

		if (changeToUndo.preRun != null)
		{
			changeToUndo.preRun.run();
		}
		mainWindow.updater.createAndShowMapFromChange(changeToUndo);
		mainWindow.updater.dowWhenMapIsNotDrawing(() -> mainWindow.updater.createAndShowLowPriorityChanges());
		updateUndoRedoEnabled();
	}

	public void redo()
	{
		if (!enabled)
		{
			return;
		}

		if (redoStack == null)
		{
			return;
		}

		if (redoStack.size() == 0)
		{
			return;
		}

		MapSettings currentSettings = undoStack.isEmpty() ? copyOfSettingsWhenEditorWasOpened.deepCopy() : undoStack.peek().settings;
		MapChange changeToRedo = redoStack.pop();
		undoStack.push(changeToRedo);
		MapSettings newSettings = changeToRedo.settings.deepCopy();
		mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(newSettings, true, false);

		if (changeToRedo.toolThatMadeChange != null)
		{
			// Switch to the tool that made the change.
			if (mainWindow.toolsPanel.currentTool == changeToRedo.toolThatMadeChange)
			{
				changeToRedo.toolThatMadeChange.onAfterUndoRedo();
			}
		}
		else
		{
			// This happens if you redo a change not associated with any particular tool, such as Clear Entire Map or the Theme panel.
			mainWindow.toolsPanel.currentTool.onAfterUndoRedo();
		}

		MapChange changeWithPrevSettings = new MapChange(currentSettings, changeToRedo.updateType, changeToRedo.toolThatMadeChange,
				changeToRedo.preRun);
		if (changeToRedo.preRun != null)
		{
			changeToRedo.preRun.run();
		}
		mainWindow.updater.createAndShowMapFromChange(changeWithPrevSettings);
		mainWindow.updater.dowWhenMapIsNotDrawing(() -> mainWindow.updater.createAndShowLowPriorityChanges());
		updateUndoRedoEnabled();
	}

	public void updateUndoRedoEnabled()
	{
		boolean undoEnabled = enabled && undoStack != null && undoStack.size() > 0;
		mainWindow.undoButton.setEnabled(undoEnabled);
		boolean redoEnabled = enabled && redoStack != null && redoStack.size() > 0;
		mainWindow.redoButton.setEnabled(redoEnabled);
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}
}
