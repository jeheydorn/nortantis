package nortantis.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
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
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import nortantis.ImagePanel;
import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextType;
import util.JComboBoxFixed;
import util.JTextFieldFixed;
import util.ImageHelper;
import util.Tuple2;

public class TextTool implements EditorTool
{
	private final TextEditingPanel mapDisplayPanel;
	private JTextField editTextField;
	private BufferedImage mapWithoutText;
	private MapParts mapParts;
	private MapText lastSelected;
	private double zoom;
	private JComboBox<ToolType> toolComboBox;
	JComboBox<TextType>textTypeComboBox;
	private Point mousePressedLocation;
	ToolType lastTool;
	JPanel toolOptionsPanel;
	BufferedImage placeHolder;
	private MapSettings settings;
	

	public TextTool(JDialog parent, MapSettings settings)
	{
		this.settings = settings;
		placeHolder = ImageHelper.read("assets/drawing_map.png");
		mapDisplayPanel = new TextEditingPanel(placeHolder);
		mapDisplayPanel.setLayout(new BorderLayout());

		createToolsOptionsPanel();

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
		
		mapDisplayPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				handleMouseClickOnMap(e);
			}
			
			@Override
			public void mousePressed(MouseEvent e)
			{
				handleMousePressedOnMap(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				handleMouseReleasedOnMap(e);
			}
		});
		
		mapDisplayPanel.addMouseMotionListener(new MouseMotionListener()
		{
			
			@Override
			public void mouseMoved(MouseEvent e)
			{
			}
			
			@Override
			public void mouseDragged(MouseEvent e)
			{
				handleMouseDraggedOnMap(e);
			}
		});

	}
	
	private void createToolsOptionsPanel()
	{
		toolOptionsPanel = new JPanel();
		toolOptionsPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		toolOptionsPanel.setLayout(new BoxLayout(toolOptionsPanel, BoxLayout.Y_AXIS));
		
		editTextField = new JTextFieldFixed();
		int borderWidth = EditorDialog.borderWidthBetweenComponents;
		editTextField.setBorder(BorderFactory.createEmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth));
		toolOptionsPanel.add(editTextField);
		editTextField.setColumns(20);
		
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
				
				mapDisplayPanel.clearAreasToDraw();
				lastSelected = null;
				mapDisplayPanel.repaint();
				textTypeComboBox.setEnabled(toolComboBox.getSelectedItem() == ToolType.Add);
				editTextField.setText("");
				lastTool = (ToolType)toolComboBox.getSelectedItem();
				updateToolText();
			}
		});
		addLabelAndComponentToToolsOptionsPanel(lblTools, toolComboBox);
		toolComboBox.setSelectedItem(ToolType.Edit); 		
		lastTool = (ToolType)toolComboBox.getSelectedItem();
		
		JLabel lblTextType = new JLabel("Text type:");
		
		textTypeComboBox = new JComboBoxFixed<>();
		textTypeComboBox.setEnabled(toolComboBox.getSelectedItem() == ToolType.Add);
		textTypeComboBox.setSelectedItem(TextType.Other_mountains);
		addLabelAndComponentToToolsOptionsPanel(lblTextType, textTypeComboBox);
		
		for (ToolType toolType : ToolType.values())
		{
			toolComboBox.addItem(toolType);
		}
		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);				
		}
		
		toolOptionsPanel.add(Box.createVerticalGlue());
	}
	
	private void addLabelAndComponentToToolsOptionsPanel(JLabel label, JComponent component)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		int borderWidth = EditorDialog.borderWidthBetweenComponents;
		panel.setBorder(BorderFactory.createEmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth));
		label.setPreferredSize(new Dimension(80, 20));
		panel.add(label);
		panel.add(component);
		panel.add(Box.createHorizontalGlue());
		toolOptionsPanel.add(panel);
	}
	
	@Override
	public String getToolbarName()
	{
		return "Text";
	}

	@Override
	public JPanel getToolOptionsPanel()
	{
		return toolOptionsPanel;
	}

	@Override
	public ImagePanel getDisplayPanel()
	{
		return mapDisplayPanel;
	}
	
	@Override
	public void handleZoomChange(double zoomLevel)
	{
		zoom = zoomLevel;
		mapDisplayPanel.setImage(placeHolder);
		mapDisplayPanel.clearAreasToDraw();
		mapParts = null;
		
		mapDisplayPanel.repaint();
		createAndShowMap();
		editTextField.requestFocus();

	}
	
	@Override
	public void onBeforeSaving()
	{
		handleTextEdit(lastSelected);
	}
	
	private void createAndShowMap()
	{
		// Change a few settings to make map creation faster.
		settings.resolution = zoom;
		settings.landBlur = 0;
		settings.oceanEffects = 0;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;

		SwingWorker<Tuple2<BufferedImage, MapParts>, Void> worker = new SwingWorker<Tuple2<BufferedImage, MapParts>, Void>() 
	    {
	        @Override
	        public Tuple2<BufferedImage, MapParts> doInBackground() 
	        {	
				try
				{
					MapParts parts = new MapParts();
					BufferedImage map = new MapCreator().createMap(settings, null, parts);
					return new Tuple2<>(map, parts);
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
	        	Tuple2<BufferedImage, MapParts> tuple = null;
	            try 
	            {
	                tuple = get();
	            } 
	            catch (InterruptedException | java.util.concurrent.ExecutionException e) 
	            {
	                throw new RuntimeException(e);
	            }
	            
	            if (tuple != null)
	            {
	            	mapWithoutText = tuple.getFirst();
	            	mapParts = tuple.getSecond();
	            	// I need the textDrawer to have the original settings object, not a copy,
	            	// so that when the user edits text, the changes are displayed.
	            	//mapParts.textDrawer.setSettings(settings);
	            	
	            	// Set the MapTexts in the TextDrawer to be the same object as settings.edits.text.
	            	// This makes it so that any edits done to the settings will automatically be reflected
	            	// in the text drawer. Also, it is necessary because the TextDrawer adds the Areas to the
	            	// map texts, which are needed to make them clickable in this editing panel.
            		mapParts.textDrawer.setMapTexts(settings.edits.text);
	            
	            	// Display the map with text.
	            	BufferedImage mapWithText = drawMapWithText();
	            	
	            	mapDisplayPanel.image = mapWithText;
	            	mapDisplayPanel.repaint();
	            	// Tell the scroll pane to update itself.
	            	mapDisplayPanel.revalidate();
	            }
	        }
	 
	    };
	    worker.execute();

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

	/**
	 * 
	 * @param clickLoc The location the user clicked relative the the map image.
	 */
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
	            
              	mapDisplayPanel.image = map;
        		mapDisplayPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
        		mapDisplayPanel.repaint();
            	// Tell the scroll pane to update itself.
            	mapDisplayPanel.revalidate();	        
	        }
	    };
	    worker.execute();
	}

	private void handleMousePressedOnMap(MouseEvent e)
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
			mapDisplayPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
			mapDisplayPanel.repaint();
		}
		else if (toolComboBox.getSelectedItem().equals(ToolType.Rotate))
		{
			lastSelected = mapParts.textDrawer.findTextPicked(e.getPoint());
			if (lastSelected != null)
			{
				// Region and title names cannot be rotated.
				if (lastSelected.type != TextType.Region && lastSelected.type != TextType.Title)
				{
					mapDisplayPanel.setAreasToDraw(lastSelected.areas);
				}
				else
				{
					lastSelected = null;				
				}
			}
			else
			{
				mapDisplayPanel.setAreasToDraw(null);
			}
			mapDisplayPanel.repaint();
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
	
	private void handleMouseDraggedOnMap(MouseEvent e)
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
				mapDisplayPanel.setAreasToDraw(transformedAreas);
				mapDisplayPanel.repaint();
			}
			else if (toolComboBox.getSelectedItem().equals(ToolType.Rotate))
			{
				List<Area> transformedAreas = new ArrayList<>(lastSelected.areas.size());
				for (Area area : lastSelected.areas)
				{
					double centerX = lastSelected.location.x / zoom;
					double centerY = lastSelected.location.y / zoom;
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
				mapDisplayPanel.setAreasToDraw(transformedAreas);
				mapDisplayPanel.repaint();				
			}
		}
	}
	
	private void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (lastSelected != null)
		{
			if (toolComboBox.getSelectedItem().equals(ToolType.Move))
			{
				// The user dragged and dropped text.
				
				Point translation = new Point((int)((e.getX() - mousePressedLocation.x) * zoom), 
						(int)((e.getY() - mousePressedLocation.y) * zoom));
				lastSelected.location = new hoten.geom.Point(lastSelected.location.x + translation.x,
						+ lastSelected.location.y + translation.y);
				updateTextInBackgroundThread(lastSelected);
			}
			else if (toolComboBox.getSelectedItem().equals(ToolType.Rotate))
			{
				double centerX = lastSelected.location.x / zoom;
				double centerY = lastSelected.location.y / zoom;
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
		
	private void handleMouseClickOnMap(MouseEvent e)
	{
		// If the map has been drawn...
		if (mapParts != null)
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
			mapDisplayPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
			mapDisplayPanel.repaint();
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
	


}
