package nortantis.editor;

import java.awt.Color;
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
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultFocusManager;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextDrawer;
import nortantis.TextType;
import util.ImageHelper;
import util.JComboBoxFixed;
import util.JTextFieldFixed;

public class TextTool extends EditorTool
{
	private BufferedImage mapWithoutText;
	private JTextField editTextField;
	private MapText lastSelected;
	private JComboBox<ToolType> toolComboBox;
	JComboBox<TextType>textTypeComboBox;
	private Point mousePressedLocation;
	ToolType lastTool;
	

	public TextTool(MapSettings settings, EditorDialog parent)
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
					toolComboBox.setSelectedItem(ToolType.Add);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_E) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Edit);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_R) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Rotate);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_G) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Move);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_D) 
						&& ((e.getModifiers() & (KeyEvent.ALT_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Delete);
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
		
		editTextField = new JTextFieldFixed();
		int borderWidth = EditorDialog.borderWidthBetweenComponents;
		editTextField.setBorder(BorderFactory.createEmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth));
		editTextField.setColumns(18);
		JPanel textFieldPanel = new JPanel();
		textFieldPanel.setLayout(new BoxLayout(textFieldPanel, BoxLayout.X_AXIS));
		textFieldPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, EditorTool.spaceBetweenRowsOfComponents, 0));
		textFieldPanel.add(editTextField);
		toolOptionsPanel.add(textFieldPanel);
		
		JLabel lblTools = new JLabel("Action:");
		
		toolComboBox = new JComboBoxFixed<>();
		toolComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				// Keep any text edits being done.
				if (lastSelected != null && lastTool == ToolType.Edit)
				{
					handleTextEdit(lastSelected);
				}
				
				mapEditingPanel.clearAreasToDraw();
				lastSelected = null;
				mapEditingPanel.repaint();
				textTypeComboBox.setEnabled(toolComboBox.getSelectedItem() == ToolType.Add);
				editTextField.setText("");
				lastTool = (ToolType)toolComboBox.getSelectedItem();
				updateToolText();
			}
		});
		addLabelAndComponentToPanel(toolOptionsPanel, lblTools, toolComboBox);
		toolComboBox.setSelectedItem(ToolType.Edit); 		
		lastTool = (ToolType)toolComboBox.getSelectedItem();
		
		JLabel lblTextType = new JLabel("Text type:");
		
		textTypeComboBox = new JComboBoxFixed<>();
		textTypeComboBox.setEnabled(toolComboBox.getSelectedItem() == ToolType.Add);
		textTypeComboBox.setSelectedItem(TextType.Other_mountains);
		addLabelAndComponentToPanel(toolOptionsPanel, lblTextType, textTypeComboBox);
		
		for (ToolType toolType : ToolType.values())
		{
			toolComboBox.addItem(toolType);
		}
		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);				
		}
		
		toolOptionsPanel.add(Box.createVerticalGlue());
		return toolOptionsPanel;
	}
		
	@Override
	public String getToolbarName()
	{
		return "Text";
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
		settings.oceanEffects = 0;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;
		settings.alwaysCreateTextDrawerAndUpdateLandBackgroundWithOcean = true;
	}
	
	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map, boolean mapNeedsRedraw)
	{
		// Set the MapTexts in the TextDrawer to be the same object as settings.edits.text.
    	// This makes it so that any edits done to the settings will automatically be reflected
    	// in the text drawer. Also, it is necessary because the TextDrawer adds the Areas to the
    	// map texts, which are needed to make them clickable in this editing panel.
		mapParts.textDrawer.setMapTexts(settings.edits.text);
    
    	// Add text to the map
		mapWithoutText = map;
		
    	return drawMapWithText();
	}
	
	private BufferedImage drawMapWithText()
	{		
		BufferedImage mapWithText = ImageHelper.deepCopy(mapWithoutText);
		try
		{
			mapParts.textDrawer.drawText(mapParts.graph, mapWithText, mapParts.landBackground, mapParts.mountainGroups);
		}
		catch (Exception e)
		{
			e.printStackTrace();
	        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
		
		return mapWithText;
	}
	
	enum ToolType
	{
		Edit,
		Move,
		Add,
		Rotate,
		Delete,
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
					//ConcurrencyUtils.shutdownAndAwaitTermination();
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
		if (toolComboBox.getSelectedItem().equals(ToolType.Move))
		{
			// Allow the user to drag and drop a text box to a new location.
			MapText selectedText = mapParts.textDrawer.findTextPicked(e.getPoint());
			if (selectedText != null)
			{
				mousePressedLocation = e.getPoint();
			}
			lastSelected = selectedText;
			mapEditingPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
			mapEditingPanel.repaint();
		}
		else if (toolComboBox.getSelectedItem().equals(ToolType.Rotate))
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
		else if (toolComboBox.getSelectedItem().equals(ToolType.Delete))
		{
			MapText selectedText = mapParts.textDrawer.findTextPicked(e.getPoint());
			if (selectedText != null)
			{
				selectedText.value = "";
				updateTextInBackgroundThread(null);
			}
		}
	}
	
	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (lastSelected != null)
		{
			if (toolComboBox.getSelectedItem().equals(ToolType.Move))
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
			else if (toolComboBox.getSelectedItem().equals(ToolType.Rotate))
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
			if (toolComboBox.getSelectedItem().equals(ToolType.Move))
			{
				// The user dragged and dropped text.
				
				Point translation = new Point((int)((e.getX() - mousePressedLocation.x) * (1.0/zoom)), 
						(int)((e.getY() - mousePressedLocation.y) * (1.0/zoom)));
				lastSelected.location = new hoten.geom.Point(lastSelected.location.x + translation.x,
						+ lastSelected.location.y + translation.y);
				updateTextInBackgroundThread(lastSelected);
			}
			else if (toolComboBox.getSelectedItem().equals(ToolType.Rotate))
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
			if (toolComboBox.getSelectedItem().equals(ToolType.Edit))
			{
				MapText selectedText = mapParts.textDrawer.findTextPicked(e.getPoint());
				handleTextEdit(selectedText);
			}
			else if (toolComboBox.getSelectedItem().equals(ToolType.Add))
			{
				MapText addedText = mapParts.textDrawer.createUserAddedText((TextType)textTypeComboBox.getSelectedItem(), 
						new hoten.geom.Point(e.getPoint().x, e.getPoint().y));
				settings.edits.text.add(addedText);
				
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
			updateTextInBackgroundThread(selectedText);
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
	
	private void updateToolText()
	{
		if (toolComboBox.getSelectedItem() == ToolType.Add)
		{
			toolComboBox.setToolTipText("Add new text of the selected text type (alt+A)");
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Edit)
		{
			toolComboBox.setToolTipText("Edit text (alt+E)");			
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Move)
		{
			toolComboBox.setToolTipText("Move text (alt+G)");			
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Rotate)
		{
			toolComboBox.setToolTipText("Rotate text (alt+R)");			
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Delete)
		{
			toolComboBox.setToolTipText("Delete text (alt+D)");			
		}
	}

	@Override
	public void onSwitchingAway()
	{
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
	}

	@Override
	public void onSelected()
	{
		mapEditingPanel.setHighlightColor(new Color(255,227,74));
		
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{	
	}
	


}
