package nortantis.editor;

import java.awt.Color;
import java.awt.Dimension;
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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultFocusManager;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextType;
import nortantis.util.ImageHelper;
import nortantis.util.JComboBoxFixed;
import nortantis.util.JTextFieldFixed;

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
	private JPanel textTypePanel;
	private JComboBox<TextType>textTypeComboBox;
	private JPanel textFieldPanel;
	

	public TextTool(MapSettings settings, EditorFrame parent)
	{
		super(settings, parent);

		// Using KeyEventDispatcher instead of KeyListener makes the keys work when any component is focused.
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					handleTextEdit(lastSelected);	
				}
				else if ((e.getKeyCode() == KeyEvent.VK_A) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					addButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_E) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					editButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_R) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					rotateButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_M) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					moveButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_D) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
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
		JPanel toolOptionsPanel = new JPanel();
		toolOptionsPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		toolOptionsPanel.setLayout(new BoxLayout(toolOptionsPanel, BoxLayout.Y_AXIS));
				
		{
			JLabel actionsLabel = new JLabel("Action:");
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
		    moveButton.setToolTipText("Move text (alt+M)");
	
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
	
		    EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, actionsLabel, radioButtons);
		}
		
		editTextField = new JTextFieldFixed();
		int borderWidth = EditorFrame.borderWidthBetweenComponents;
		editTextField.setBorder(BorderFactory.createEmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth));
		editTextField.setColumns(18);
		textFieldPanel = new JPanel();
		textFieldPanel.setLayout(new BoxLayout(textFieldPanel, BoxLayout.X_AXIS));
		textFieldPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, EditorTool.spaceBetweenRowsOfComponents, 0));
		textFieldPanel.add(editTextField);
		toolOptionsPanel.add(textFieldPanel);
		
		JLabel lblTextType = new JLabel("Text type:");
		
		textTypeComboBox = new JComboBoxFixed<>();
		textTypeComboBox.setSelectedItem(TextType.Other_mountains);
		textTypePanel = addLabelAndComponentToPanel(toolOptionsPanel, lblTextType, textTypeComboBox);
		
		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);				
		}
		
		// Prevent the panel from shrinking when components are hidden.
		toolOptionsPanel.add(Box.createRigidArea(new Dimension(EditorFrame.toolsPanelWidth - 25, 0)));
		
		toolOptionsPanel.add(Box.createVerticalGlue());
		
	    editButton.doClick(); 		
		return toolOptionsPanel;
	}
	
	private void handleActionChanged()
	{
		// Keep any text edits being done.
		if (textFieldPanel.isVisible())
		{
			handleTextEdit(lastSelected);
		}
		
		mapEditingPanel.clearAreasToDraw();
		lastSelected = null;
		mapEditingPanel.repaint();
		textTypePanel.setVisible(addButton.isSelected());
		editTextField.setText("");
		textFieldPanel.setVisible(editButton.isSelected());
	}
		
	@Override
	public String getToolbarName()
	{
		return "Text";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get("assets/internal/Text tool.png").toString();
	}
	
	@Override
	public void handleZoomChange(double zoomLevel)
	{	
		super.handleZoomChange(zoomLevel);
		editTextField.requestFocus();
	}
	
	@Override
	public void onBeforeSaving()
	{
		handleTextEdit(lastSelected);
	}
	
	@Override
	protected void onBeforeCreateMap()
	{
		// Change a few settings to make map creation faster.
		settings.resolution = zoom;
		settings.landBlur = 0;
		settings.oceanEffectSize = 0;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;
		settings.alwaysCreateTextDrawerAndUpdateLandBackgroundWithOcean = true;
		settings.drawIcons = true;
		settings.drawRivers = true;
	}
	
	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map, boolean isQuickUpdate)
	{
		// Set the MapTexts in the TextDrawer to be the same object as settings.edits.text.
    	// This makes it so that any edits done to the settings will automatically be reflected
    	// in the text drawer. Also, it is necessary because the TextDrawer adds the Areas to the
    	// map texts, which are needed to make them clickable to edit them.
		mapParts.textDrawer.setMapTexts(settings.edits.text);
    
    	// Add text to the map
		mapWithoutText = map;
		
		BufferedImage mapWithText = drawMapWithText();
		
    	return mapWithText;
	}
	
	private BufferedImage drawMapWithText()
	{		
		BufferedImage mapWithText = ImageHelper.deepCopy(mapWithoutText);
		try
		{
			mapParts.textDrawer.drawText(mapParts.graph, mapWithText, mapParts.landBackground, mapParts.mountainGroups, mapParts.cityDrawTasks);
		}
		catch (Exception e)
		{
			e.printStackTrace();
	        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
		
		return mapWithText;
	}

	private void updateTextInBackgroundThread(final MapText selectedText)
	{
	    SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
	    {
	        @Override
	        public BufferedImage doInBackground() 
	        {	
				try
				{
					BufferedImage map = drawMapWithText();
					
					return map;
				} 
				catch (Exception e)
				{
					e.printStackTrace();
			        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				} 
	        	
	        	return null;
	        }
	        
	        @Override
	        public void done()
	        {
	        	BufferedImage map = null;
	            try 
	            {
	            	map = get();
	            } 
	            catch (InterruptedException | java.util.concurrent.ExecutionException e) 
	            {
	                throw new RuntimeException(e);
	            }
	            
              	mapEditingPanel.image = map;
        		mapEditingPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
        		mapEditingPanel.repaint();
            	// Tell the scroll pane to update itself.
            	mapEditingPanel.revalidate();	        
	        }
	    };
	    worker.execute();
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		if (moveButton.isSelected())
		{
			// Begin a drag and drop of a text box.
			MapText selectedText = mapParts.textDrawer.findTextPicked(e.getPoint());
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
			lastSelected = mapParts.textDrawer.findTextPicked(e.getPoint());
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
			MapText selectedText = mapParts.textDrawer.findTextPicked(e.getPoint());
			if (selectedText != null)
			{
				selectedText.value = "";
				setUndoPoint();
				updateTextInBackgroundThread(null);
			}
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
				for (Area area : lastSelected.areas)
				{
					Area areaCopy = new Area(area);
					AffineTransform t = new AffineTransform();
					t.translate(e.getX() - mousePressedLocation.x, e.getY() - mousePressedLocation.y);
					areaCopy.transform(t);
					transformedAreas.add(areaCopy);
				}
				mapEditingPanel.setAreasToDraw(transformedAreas);
				mapEditingPanel.repaint();
			}
			else if (rotateButton.isSelected())
			{
				List<Area> transformedAreas = new ArrayList<>(lastSelected.areas.size());
				for (Area area : lastSelected.areas)
				{
					double centerX = lastSelected.location.x * zoom;
					double centerY = lastSelected.location.y * zoom;
					Area areaCopy = new Area(area);
					
					// Undo previous rotation.
					AffineTransform t0 = new AffineTransform();
					t0.rotate(-lastSelected.angle, centerX, centerY);
					areaCopy.transform(t0);
					
					// Add new rotation.
					AffineTransform t = new AffineTransform();
					double angle = Math.atan2(e.getY() - centerY, e.getX() - centerX);
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
				Point translation = new Point((int)((e.getX() - mousePressedLocation.x) * (1.0/zoom)), 
						(int)((e.getY() - mousePressedLocation.y) * (1.0/zoom)));
				lastSelected.location = new hoten.geom.Point(lastSelected.location.x + translation.x,
						+ lastSelected.location.y + translation.y);
				setUndoPoint();
				updateTextInBackgroundThread(lastSelected);
			}
			else if (rotateButton.isSelected())
			{
				double centerX = lastSelected.location.x / (1.0/zoom);
				double centerY = lastSelected.location.y / (1.0/zoom);
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
				updateTextInBackgroundThread(lastSelected);
			}
		}
	}
		
	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
		// If the map has been drawn...
		if (mapWithoutText != null)
		{
			if (editButton.isSelected())
			{
				if (!editTextField.hasFocus())
				{
					editTextField.grabFocus();
				}
				MapText selectedText = mapParts.textDrawer.findTextPicked(e.getPoint());
				handleTextEdit(selectedText);
			}
			else if (addButton.isSelected())
			{
				MapText addedText = mapParts.textDrawer.createUserAddedText((TextType)textTypeComboBox.getSelectedItem(), 
						new hoten.geom.Point(e.getPoint().x, e.getPoint().y));
				settings.edits.text.add(addedText);
				
				setUndoPoint();
				updateTextInBackgroundThread(null);
			}
		}
	}
	
	private void handleTextEdit(MapText selectedText)
	{
		if (lastSelected != null && !editTextField.getText().equals(lastSelected.value))
		{
			lastSelected.value = editTextField.getText();

			// Need to re-draw all of the text.
			setUndoPoint();
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
		if (textFieldPanel.isVisible())
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
	protected void onAfterUndoRedo(boolean requiresFullRedraw)
	{
		// For why this is needed, see the big comment on the same line in onBeforeShowMap.
		mapParts.textDrawer.setMapTexts(settings.edits.text);
		
		mapEditingPanel.clearAreasToDraw();
		lastSelected = null;
		editTextField.setText("");
		if (requiresFullRedraw)
		{
			createAndShowMap();
		}
		else
		{
			updateTextInBackgroundThread(null);
		}
	}
	


}
