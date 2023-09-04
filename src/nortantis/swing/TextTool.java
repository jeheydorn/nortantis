package nortantis.swing;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultFocusManager;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextType;
import nortantis.editor.MapUpdater;
import nortantis.util.AssetsPath;
import nortantis.util.JComboBoxFixed;
import nortantis.util.Tuple2;

public class TextTool extends EditorTool
{
	private JTextField editTextField;
	private MapText lastSelected;
	private Point mousePressedLocation;
	private JRadioButton editButton;
	private JRadioButton addButton;
	private JRadioButton deleteButton;
	private RowHider textTypeHider;
	private JComboBox<TextType> textTypeComboBox;
	private RowHider editTextFieldHider;
	private RowHider booksHider;
	private JPanel booksPanel;
	private JLabel drawTextDisabledLabel;
	private RowHider drawTextDisabledLabelHider;
	private boolean isRotating;
	private boolean isMoving;
	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private RowHider clearRotationButtonHider;


	public TextTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);

		// Using KeyEventDispatcher instead of KeyListener makes the keys work
		// when any component is focused.
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					handleSelectingTextToEdit(lastSelected, false);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_A) && e.isAltDown())
				{
					addButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_E) && e.isAltDown())
				{
					editButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_D) && e.isAltDown())
				{
					deleteButton.doClick();
				}

				return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);

		parent.addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
			}
		});

	}

	@Override
	protected JPanel createToolsOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		drawTextDisabledLabel = new JLabel("<html>This tool is disabled because drawing text is disabled in the Fonts tab.</html>");
		drawTextDisabledLabelHider = organizer.addLeftAlignedComponent(drawTextDisabledLabel);
		drawTextDisabledLabelHider.setVisible(false);

		{
			ButtonGroup group = new ButtonGroup();
			List<JRadioButton> radioButtons = new ArrayList<>();

			ActionListener listener = new ActionListener()
			{

				@Override
				public void actionPerformed(ActionEvent e)
				{
					handleActionChanged();
				}
			};

			editButton = new JRadioButton("<HTML><U>E</U>dit</HTML>");
			group.add(editButton);
			radioButtons.add(editButton);
			editButton.addActionListener(listener);
			editButton.setToolTipText("Edit text (alt+E)");

			addButton = new JRadioButton("<HTML><U>A</U>dd</HTML>");
			group.add(addButton);
			radioButtons.add(addButton);
			addButton.addActionListener(listener);
			addButton.setToolTipText("Add new text of the selected text type (alt+A)");

			deleteButton = new JRadioButton("<HTML><U>D</U>elete</HTML>");
			group.add(deleteButton);
			radioButtons.add(deleteButton);
			deleteButton.addActionListener(listener);
			deleteButton.setToolTipText("Delete text (alt+D)");

			organizer.addLabelAndComponentsToPanelVertical("Action:", "", radioButtons);
		}

		editTextField = new JTextField();
		editTextField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				if (lastSelected != null)
				{
					handleSelectingTextToEdit(lastSelected, false);
				}
			}
		});
		editTextFieldHider = organizer.addLeftAlignedComponent(editTextField);

		textTypeComboBox = new JComboBoxFixed<>();
		textTypeComboBox.setSelectedItem(TextType.Other_mountains);
		textTypeComboBox.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (editButton.isSelected() && lastSelected != null)
				{
					lastSelected.type = (TextType) textTypeComboBox.getSelectedItem();
					updateTextInBackgroundThread(lastSelected);
				}

			}
		});
		textTypeHider = organizer.addLabelAndComponentToPanel("Text type:", "", textTypeComboBox);

		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);
		}
		
		
		JButton clearRotationButton = new JButton("Rotate to Horizontal");
		clearRotationButton.setToolTipText("Set the rotation angle of the selected text to 0 degrees.");
		TextTool thisTool = this;
		clearRotationButton.addActionListener(new ActionListener()
		{	
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (lastSelected != null)
				{
					lastSelected.angle = 0;
					undoer.setUndoPoint(UpdateType.Text, thisTool);
					updater.createAndShowMapTextChange();
				}
			}
		});
		clearRotationButtonHider = organizer.addLabelAndComponentsToPanelHorizontal("", "", 0, Arrays.asList(clearRotationButton));

		
		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
	    brushSizeComboBox = brushSizeTuple.getFirst();
	    brushSizeHider = brushSizeTuple.getSecond();


		booksPanel = SwingHelper.createBooksPanel(() ->
		{
		});
		booksHider = organizer.addLeftAlignedComponentWithStackedLabel("Books for generating text:",
				"Selected books will be used to generate new names.", booksPanel);

		editButton.doClick();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.6);
		organizer.addVerticalFillerRow();
		return toolOptionsPanel;
	}

	private void handleActionChanged()
	{
		if (editTextFieldHider.isVisible())
		{
			// Keep any text edits that were being done, and hide the edit
			// fields.
			handleSelectingTextToEdit(null, false);
		}

		if (addButton.isSelected() || deleteButton.isSelected())
		{
			lastSelected = null;
		}

		textTypeHider.setVisible(addButton.isSelected());
		booksHider.setVisible(addButton.isSelected());
		editTextFieldHider.setVisible(false);
		clearRotationButtonHider.setVisible(false);
		if (editButton.isSelected() && lastSelected != null)
		{
			editTextField.setText(lastSelected.value);
			editTextField.requestFocus();
		}

		// For some reason this is necessary to prevent the text editing field
		// from flattening sometimes.
		if (getToolOptionsPanel() != null)
		{
			getToolOptionsPanel().revalidate();
			getToolOptionsPanel().repaint();
		}
		
		brushSizeHider.setVisible(deleteButton.isSelected());
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.repaint();
		mapEditingPanel.hideBrush();
	}

	@Override
	public String getToolbarName()
	{
		return "Text";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get(AssetsPath.get(), "internal/Text tool.png").toString();
	}

	@Override
	public void onActivate()
	{
		updater.createAndShowMapTextChange();
		editTextField.requestFocus();
	}

	@Override
	public void onBeforeSaving()
	{
		handleSelectingTextToEdit(lastSelected, false);
	}

	@Override
	protected void onBeforeShowMap()
	{
		if (lastSelected == null)
		{
			mapEditingPanel.clearTextBox();
		}
		else
		{
			mapEditingPanel.setTextBoxToDraw(lastSelected);
		}
		mapEditingPanel.repaint();
		// Tell the scroll pane to update itself.
		mapEditingPanel.revalidate();
	}

	private void updateTextInBackgroundThread(final MapText selectedText)
	{
		updater.createAndShowMapTextChange();
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		isRotating = false;
		isMoving = false;

		if (deleteButton.isSelected())
		{
			List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(e.getPoint());
			for (MapText text : mapTextsSelected)
			{
				text.value = "";
			}
			mapEditingPanel.clearAreasToDraw();
			if (mapTextsSelected.size() > 0)
			{
				updateTextInBackgroundThread(null);
			}
		}
		else if (addButton.isSelected())
		{
			MapText addedText = updater.mapParts.textDrawer.createUserAddedText((TextType) textTypeComboBox.getSelectedItem(),
					getPointOnGraph(e.getPoint()));
			mainWindow.edits.text.add(addedText);

			undoer.setUndoPoint(UpdateType.Text, this);
			
			lastSelected = addedText;
			editButton.setSelected(true);
			handleActionChanged();
			handleSelectingTextToEdit(addedText, true);
			
			updateTextInBackgroundThread(null);
		}
		else if (editButton.isSelected())
		{
			if (lastSelected != null && mapEditingPanel.isInTextRotateTool(e.getPoint()))
			{
				// mousePressedLocation = e.getPoint(); // TODO decide if I need
				// this
				isRotating = true;
			}
			else if (lastSelected != null && mapEditingPanel.isInTextMoveTool(e.getPoint()))
			{
				isMoving = true;
				mousePressedLocation = e.getPoint();
			}
			else
			{
				MapText selectedText = updater.mapParts.textDrawer.findTextPicked(getPointOnGraph(e.getPoint()));
				handleSelectingTextToEdit(selectedText, true);
			}
		}
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (lastSelected != null)
		{
			if (isMoving)
			{
				// The user is dragging a text box.
				nortantis.graph.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				nortantis.graph.geom.Point graphPointMousePressedLocation = getPointOnGraph(mousePressedLocation);

				int deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
				int deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);

				java.awt.Rectangle line1Bounds = new java.awt.Rectangle(lastSelected.line1Bounds.x + deltaX,
						lastSelected.line1Bounds.y + deltaY, lastSelected.line1Bounds.width, lastSelected.line1Bounds.height);
				java.awt.Rectangle line2Bounds = lastSelected.line2Bounds == null ? null
						: new java.awt.Rectangle(lastSelected.line2Bounds.x + deltaX, lastSelected.line2Bounds.y + deltaY,
								lastSelected.line2Bounds.width, lastSelected.line2Bounds.height);
				nortantis.graph.geom.Point location = new nortantis.graph.geom.Point(
						lastSelected.location.x + (deltaX / mainWindow.displayQualityScale),
						lastSelected.location.y + (deltaY / mainWindow.displayQualityScale));
				mapEditingPanel.setTextBoxToDraw(location, line1Bounds, line2Bounds, lastSelected.angle);
				mapEditingPanel.repaint();
			}
			else if (isRotating)
			{
				nortantis.graph.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());

				double centerX = lastSelected.location.x * mainWindow.displayQualityScale;
				double centerY = lastSelected.location.y * mainWindow.displayQualityScale;
				double angle = Math.atan2(graphPointMouseLocation.y - centerY, graphPointMouseLocation.x - centerX);
				mapEditingPanel.setTextBoxToDraw(lastSelected.location, lastSelected.line1Bounds, lastSelected.line2Bounds, angle);
				mapEditingPanel.repaint();
			}
		}
		else if (deleteButton.isSelected())
		{
			List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(e.getPoint());
			for (MapText text : mapTextsSelected)
			{
				text.value = "";
			}
			mapEditingPanel.clearAreasToDraw();
			if (mapTextsSelected.size() > 0)
			{
				updateTextInBackgroundThread(null);
			}
		}
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (lastSelected != null)
		{
			if (isMoving)
			{
				nortantis.graph.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				nortantis.graph.geom.Point graphPointMousePressedLocation = getPointOnGraph(mousePressedLocation);

				// The user dragged and dropped text.
				// Divide the translation by mainWindow.displayQualityScale
				// because MapText locations are stored as if
				// the map is generated at 100% resolution.
				Point translation = new Point(
						(int) ((graphPointMouseLocation.x - graphPointMousePressedLocation.x) / mainWindow.displayQualityScale),
						(int) ((graphPointMouseLocation.y - graphPointMousePressedLocation.y) / mainWindow.displayQualityScale));
				lastSelected.location = new nortantis.graph.geom.Point(lastSelected.location.x + translation.x,
						+lastSelected.location.y + translation.y);
				undoer.setUndoPoint(UpdateType.Text, this);
				updateTextInBackgroundThread(lastSelected);
			}
			else if (isRotating)
			{
				double centerX = lastSelected.location.x;
				double centerY = lastSelected.location.y;
				nortantis.graph.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				// I'm dividing graphPointMouseLocation by
				// mainWindow.displayQualityScale here because
				// lastSelected.location
				// is not multiplied by mainWindow.displayQualityScale. This is
				// because MapTexts are always stored as if
				// the map were generated at 100% resolution.
				double angle = Math.atan2((graphPointMouseLocation.y / mainWindow.displayQualityScale) - centerY,
						(graphPointMouseLocation.x / mainWindow.displayQualityScale) - centerX);
				// No upside-down text.
				if (angle > Math.PI / 2)
				{
					angle -= Math.PI;
				}
				else if (angle < -Math.PI / 2)
				{
					angle += Math.PI;
				}
				lastSelected.angle = angle;
				undoer.setUndoPoint(UpdateType.Text, this);
				updateTextInBackgroundThread(lastSelected);
				isRotating = false;
			}
		}
		
		if (deleteButton.isSelected())
		{
			mapEditingPanel.clearAreasToDraw();
			mapEditingPanel.repaint();
			
			// This won't actually set an undo point unless text was deleted because Undoer is smart enough to discard undo points
			// that didn't change anything.
			undoer.setUndoPoint(UpdateType.Text, this);
		}
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}

	private void handleSelectingTextToEdit(MapText selectedText, boolean grabFocus)
	{
		if (lastSelected != null
				&& !(editTextField.getText().equals(lastSelected.value) && textTypeComboBox.getSelectedItem().equals(lastSelected.type)))
		{
			// The user changed the last selected text. Need to save the change.
			lastSelected.value = editTextField.getText();
			lastSelected.type = (TextType) textTypeComboBox.getSelectedItem();

			// Need to re-draw all of the text.
			undoer.setUndoPoint(UpdateType.Text, this);
			updateTextInBackgroundThread(editButton.isSelected() ? selectedText : null);
		}

		if (selectedText == null)
		{
			mapEditingPanel.clearTextBox();
			editTextField.setText("");
			editTextFieldHider.setVisible(false);
			clearRotationButtonHider.setVisible(false);
			textTypeHider.setVisible(false);
		}
		else
		{
			mapEditingPanel.setTextBoxToDraw(selectedText);
			editTextField.setText(selectedText.value);
			editTextFieldHider.setVisible(true);
			clearRotationButtonHider.setVisible(true);
			if (!editTextField.hasFocus() && grabFocus)
			{
				editTextField.grabFocus();
			}
			// Prevent textTypeComboBox's action listener from doing anything on
			// the next line.
			lastSelected = null;

			textTypeComboBox.setSelectedItem(selectedText.type);
			textTypeHider.setVisible(true);
		}
		mapEditingPanel.repaint();

		lastSelected = selectedText;
	}

	@Override
	public void onSwitchingAway()
	{
		// Keep any text edits being done.
		if (editButton.isSelected())
		{
			handleSelectingTextToEdit(lastSelected, false);
		}
		
		mapEditingPanel.hideBrush();
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.clearTextBox();
		
		updater.createAndShowMapTextChange();
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		if (deleteButton.isSelected())
		{
			List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(e.getPoint());
			
			List<Area> areas = new ArrayList<>();
			for (MapText text : mapTextsSelected)
			{
				if (text.line1Area != null)
				{
					areas.add(text.line1Area);
				}

				if (text.line2Area != null)
				{
					areas.add(text.line2Area);
				}
			}
			mapEditingPanel.setAreasToDraw(areas);
		}
		else
		{
			mapEditingPanel.hideBrush();
			mapEditingPanel.repaint();
		}
	}
	
	private List<MapText> getMapTextsSelectedByCurrentBrushSizeAndShowBrush(java.awt.Point mouseLocation)
	{
		List<MapText> mapTextsSelected = null;
		int brushDiameter = brushSizes.get(brushSizeComboBox.getSelectedIndex());
		if (brushDiameter > 1)
		{
			mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			mapTextsSelected = updater.mapParts.textDrawer.findTextSelectedByBrush(getPointOnGraph(mouseLocation), brushDiameter / mainWindow.zoom);
		}
		else
		{
			mapEditingPanel.hideBrush();
			MapText selected = updater.mapParts.textDrawer.findTextPicked(getPointOnGraph(mouseLocation));
			if (selected != null)
			{
				mapTextsSelected = Collections.singletonList(selected);
			}
		}
		
		mapEditingPanel.repaint();
		return mapTextsSelected == null ? new ArrayList<>() : mapTextsSelected;
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.hideBrush();
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onAfterUndoRedo()
	{
		mapEditingPanel.clearTextBox();
		mapEditingPanel.repaint();
		lastSelected = null;
		editTextField.setText("");

		lastSelected = null;
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange)
	{
		SwingHelper.checkSelectedBooks(booksPanel, settings.books);

		handleEnablingAndDisabling(settings);
		drawTextDisabledLabelHider.setVisible(!settings.drawText);
		if (!settings.drawText)
		{
			if (editButton.isSelected())
			{
				handleSelectingTextToEdit(null, false);
			}
			
			mapEditingPanel.clearTextBox();
			mapEditingPanel.clearAreasToDraw();
			mapEditingPanel.hideBrush();
			mapEditingPanel.repaint();
		}
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.books = SwingHelper.getSelectedBooksFromGUI(booksPanel);
	}

	@Override
	public boolean shouldShowTextWhenTextIsEnabled()
	{
		return true;
	}

	@Override
	public void handleEnablingAndDisabling(MapSettings settings)
	{
		SwingHelper.setEnabled(getToolOptionsPanel(), settings.drawText);
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
		lastSelected = null;
		editTextField.setText("");
	}

}
