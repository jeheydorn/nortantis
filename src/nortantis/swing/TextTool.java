package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultFocusManager;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextType;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.JComboBoxFixed;
import nortantis.util.JTextFieldFixed;
import nortantis.util.Tuple2;

public class TextTool extends EditorTool
{
	private BufferedImage mapWithoutText;
	private JTextField editTextField;
	private MapText lastSelected;
	private Point mousePressedLocation;
	private JRadioButton editButton;
	private JRadioButton moveButton;
	private JRadioButton addButton;
	private JRadioButton rotateButton;
	private JRadioButton deleteButton;
	private RowHider textTypeHider;
	private JComboBox<TextType> textTypeComboBox;
	private RowHider editTextFieldHider;
	private RowHider booksHider;
	private JPanel booksPanel;
	private JLabel drawTextDisabledLabel;
	private RowHider drawTextDisabledLabelHider;

	

	public TextTool(MainWindow parent, ToolsPanel toolsPanel)
	{
		super(parent, toolsPanel);

		// Using KeyEventDispatcher instead of KeyListener makes the keys work when any component is focused.
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					handleTextEdit(lastSelected);	
				}
				else if ((e.getKeyCode() == KeyEvent.VK_A) && e.isAltDown())
				{
					addButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_E) && e.isAltDown())
				{
					editButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_R) && e.isAltDown())
				{
					rotateButton.doClick();
				}
				else if (((e.getKeyCode() == KeyEvent.VK_M) || (e.getKeyCode() == KeyEvent.VK_G)) && e.isAltDown())
				{
					moveButton.doClick();
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
		SwingHelper.resetGridY();
		
		JPanel toolOptionsPanel = SwingHelper.createPanelForLabeledComponents();		
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
				
		drawTextDisabledLabel = new JLabel("<html>This tool is disabled because drawing text is disabled in the Fonts tab.</html>");
		drawTextDisabledLabelHider = SwingHelper.addLeftAlignedComponent(toolOptionsPanel, drawTextDisabledLabel);
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
		    
		    moveButton = new JRadioButton("<HTML><U>M</U>ove</HTML>");
		    group.add(moveButton);
		    radioButtons.add(moveButton);
		    moveButton.addActionListener(listener);
		    moveButton.setToolTipText("Move text (alt+M) or (alt+G)");
	
		    addButton = new JRadioButton("<HTML><U>A</U>dd</HTML>");
		    group.add(addButton);
		    radioButtons.add(addButton);
		    addButton.addActionListener(listener);
		    addButton.setToolTipText("Add new text of the selected text type (alt+A)");
	
		    rotateButton = new JRadioButton("<HTML><U>R</U>otate</HTML>");
		    group.add(rotateButton);
		    radioButtons.add(rotateButton);
		    rotateButton.addActionListener(listener);
		    rotateButton.setToolTipText("Rotate text (alt+R) will");	
	
		    deleteButton = new JRadioButton("<HTML><U>D</U>elete</HTML>");
		    group.add(deleteButton);
		    radioButtons.add(deleteButton);
		    deleteButton.addActionListener(listener);
		    deleteButton.setToolTipText("Delete text (alt+D)");	
	
		    SwingHelper.addLabelAndComponentsToPanelVertical(toolOptionsPanel, "Action:", "", radioButtons);
		}
				
		
		editTextField = new JTextFieldFixed();
		editTextFieldHider = SwingHelper.addLeftAlignedComponent(toolOptionsPanel, editTextField);
		
		
		textTypeComboBox = new JComboBoxFixed<>();
		textTypeComboBox.setSelectedItem(TextType.Other_mountains);
		textTypeHider = SwingHelper.addLabelAndComponentToPanel(toolOptionsPanel, "Text type:", "", textTypeComboBox);
		
		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);				
		}
		
				
		booksPanel = SwingHelper.createBooksPanel();
		booksHider = SwingHelper.addLeftAlignedComponentWithStackedLabel(toolOptionsPanel, "Books for generating text:", 
				"Selected books will be used to generate new names.", booksPanel);
		
		
	    editButton.doClick();
	    		
	    
	    SwingHelper.addVerticalFillerRow(toolOptionsPanel);
		return toolOptionsPanel;
	}
	
	private void handleActionChanged()
	{
		// Keep any text edits being done.
		if (editTextFieldHider.isVisible())
		{
			handleTextEdit(lastSelected);
		}
		
		if (addButton.isSelected() || deleteButton.isSelected())
		{
			mapEditingPanel.clearAreasToDraw();
			lastSelected = null;
			mapEditingPanel.repaint();
		}
		textTypeHider.setVisible(addButton.isSelected());
		booksHider.setVisible(addButton.isSelected());
		editTextFieldHider.setVisible(editButton.isSelected());
		if (editButton.isSelected() && lastSelected != null)
		{
			editTextField.setText(lastSelected.value);
			editTextField.requestFocus();
		}
		
		// For some reason this is necessary to prevent the text editing field from flattening sometimes.
		if (getToolOptionsPanel() != null)
		{
			getToolOptionsPanel().revalidate();
			getToolOptionsPanel().repaint();
		}
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
		editTextField.requestFocus();
	}
	
	@Override
	public void onBeforeSaving()
	{
		handleTextEdit(lastSelected);
	}
	
	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map)
	{    
    	// Add text to the map
		mapWithoutText = map;
		
		BufferedImage mapWithText = drawMapWithText();
		
		mapEditingPanel.setAreasToDraw(textToSelectAfterDraw == null ? null : textToSelectAfterDraw.areas);
		mapEditingPanel.repaint();
    	// Tell the scroll pane to update itself.
    	mapEditingPanel.revalidate();	        

		
    	return mapWithText;
	}
	
	private MapText textToSelectAfterDraw;
	private void updateTextInBackgroundThread(final MapText selectedText)
	{
		textToSelectAfterDraw = selectedText;
		mainWindow.createAndShowMapIncrementalUsingCenters(null);
	}
	
	private BufferedImage drawMapWithText()
	{		
		BufferedImage mapWithText = ImageHelper.deepCopy(mapWithoutText);
		try
		{
			mainWindow.mapParts.textDrawer.drawTextFromEdits(mainWindow.mapParts.graph, mapWithText, mainWindow.mapParts.landBackground);
		}
		catch (Exception e)
		{
			e.printStackTrace();
	        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
		
		return mapWithText;
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		if (moveButton.isSelected())
		{
			// Begin a drag and drop of a text box.
			MapText selectedText = mainWindow.mapParts.textDrawer.findTextPicked(getPointOnGraph(e.getPoint()));
			if (selectedText != null)
			{
				mousePressedLocation = e.getPoint();
			}
			lastSelected = selectedText;
			mapEditingPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
			mapEditingPanel.repaint();
		}
		else if (rotateButton.isSelected())
		{
			lastSelected = mainWindow.mapParts.textDrawer.findTextPicked(getPointOnGraph(e.getPoint()));
			if (lastSelected != null)
			{
				// Region and title names cannot be rotated.
				if (lastSelected.type != TextType.Region && lastSelected.type != TextType.Title)
				{
					mapEditingPanel.setAreasToDraw(lastSelected.areas);
				}
				else
				{
					lastSelected = null;				
				}
			}
			else
			{
				mapEditingPanel.setAreasToDraw(null);
			}
			mapEditingPanel.repaint();
		}
		else if (deleteButton.isSelected())
		{
			MapText selectedText = mainWindow.mapParts.textDrawer.findTextPicked(getPointOnGraph(e.getPoint()));
			if (selectedText != null)
			{
				selectedText.value = "";
				undoer.setUndoPoint(this);
				updateTextInBackgroundThread(null);
			}
		}
		else if (addButton.isSelected())
		{
			MapText addedText = mainWindow.mapParts.textDrawer.createUserAddedText((TextType)textTypeComboBox.getSelectedItem(), 
					getPointOnGraph(e.getPoint()));
			mainWindow.edits.text.add(addedText);
			
			undoer.setUndoPoint(this);
			updateTextInBackgroundThread(null);
		}
		else if (editButton.isSelected())
		{
			if (!editTextField.hasFocus())
			{
				editTextField.grabFocus();
			}
			MapText selectedText = mainWindow.mapParts.textDrawer.findTextPicked(getPointOnGraph(e.getPoint()));
			handleTextEdit(selectedText);
		}
	}
	
	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (lastSelected != null)
		{
			if (moveButton.isSelected())
			{
				// The user is dragging a text box.
				List<Area> transformedAreas = new ArrayList<>(lastSelected.areas.size());
				nortantis.graph.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				nortantis.graph.geom.Point graphPointMousePressedLocation = getPointOnGraph(mousePressedLocation);
				for (Area area : lastSelected.areas)
				{
					Area areaCopy = new Area(area);
					AffineTransform t = new AffineTransform();
					t.translate((int)(graphPointMouseLocation.x - graphPointMousePressedLocation.x), 
							(int)(graphPointMouseLocation.y - graphPointMousePressedLocation.y));
					areaCopy.transform(t);
					transformedAreas.add(areaCopy);
				}
				mapEditingPanel.setAreasToDraw(transformedAreas);
				mapEditingPanel.repaint();
			}
			else if (rotateButton.isSelected())
			{
				List<Area> transformedAreas = new ArrayList<>(lastSelected.areas.size());
				nortantis.graph.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());

				for (Area area : lastSelected.areas)
				{
					double centerX = lastSelected.location.x;
					double centerY = lastSelected.location.y;
					Area areaCopy = new Area(area);
					
					// Undo previous rotation.
					AffineTransform t0 = new AffineTransform();
					t0.rotate(-lastSelected.angle, centerX, centerY);
					areaCopy.transform(t0);
					
					// Add new rotation.
					AffineTransform t = new AffineTransform();
					double angle = Math.atan2(graphPointMouseLocation.y - centerY, graphPointMouseLocation.x - centerX);
					t.rotate(angle, centerX, centerY);
					
					areaCopy.transform(t);
					transformedAreas.add(areaCopy);
				}
				mapEditingPanel.setAreasToDraw(transformedAreas);
				mapEditingPanel.repaint();				
			}
		}
	}
	
	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (lastSelected != null)
		{
			if (moveButton.isSelected())
			{
				// The user dragged and dropped text.
				Point translation = new Point((int)((e.getX() - mousePressedLocation.x) * (1.0/mainWindow.zoom)), 
						(int)((e.getY() - mousePressedLocation.y) * (1.0/mainWindow.zoom)));
				lastSelected.location = new nortantis.graph.geom.Point(lastSelected.location.x + translation.x,
						+ lastSelected.location.y + translation.y);
				undoer.setUndoPoint(this);
				updateTextInBackgroundThread(lastSelected);
			}
			else if (rotateButton.isSelected())
			{
				double centerX = lastSelected.location.x / (1.0/mainWindow.zoom);
				double centerY = lastSelected.location.y / (1.0/mainWindow.zoom);
				double angle = Math.atan2(e.getY() - centerY, e.getX() - centerX);
				// No upside-down text.
				if (angle > Math.PI/2)
				{
					angle -= Math.PI;
				}
				else if (angle < -Math.PI/2)
				{
					angle += Math.PI;				
				}
				lastSelected.angle = angle;
				undoer.setUndoPoint(this);
				updateTextInBackgroundThread(lastSelected);
			}
		}
	}
		
	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}
	
	private void handleTextEdit(MapText selectedText)
	{
		if (lastSelected != null && !editTextField.getText().equals(lastSelected.value))
		{
			lastSelected.value = editTextField.getText();

			// Need to re-draw all of the text.
			undoer.setUndoPoint(this);
			updateTextInBackgroundThread(editButton.isSelected() ? selectedText : null);
		}
		else
		{
			// Just a quick highlights update.
			mapEditingPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
			mapEditingPanel.repaint();
		}
		
		if (selectedText == null)
		{
			editTextField.setText("");
		}
		else
		{
			editTextField.setText(selectedText.value);
		}
		
		lastSelected = selectedText;
	}

	@Override
	public void onSwitchingAway()
	{
		// Keep any text edits being done.
		if (editTextFieldHider.isVisible())
		{
			if (lastSelected != null && !editTextField.getText().equals(lastSelected.value))
			{
				lastSelected.value = editTextField.getText();
			}
		}
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{	
	}

	@Override
	protected void onAfterUndoRedo(MapChange change)
	{
		mapEditingPanel.clearAreasToDraw();
		lastSelected = null;
		editTextField.setText("");
		
		if (change.updateType == UpdateType.Full)
		{
			mainWindow.createAndShowMapFull();
		}
		else
		{
			updateTextInBackgroundThread(null);
		}
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings)
	{
		SwingHelper.checkSelectedBooks(booksPanel, settings.books);
		
		// TODO Hide everything in the text tool options and show a message explaining that text drawing is disabled.
		SwingHelper.setEnabled(getToolOptionsPanel(), settings.drawText);
		drawTextDisabledLabelHider.setVisible(!settings.drawText);
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.books = SwingHelper.getSelectedBooksFromGUI(booksPanel);
	}
	


}
