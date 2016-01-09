package nortantis;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import util.Helper;
import util.ImageHelper;
import util.Tuple2;

@SuppressWarnings("serial")
public class EditTextDialog extends JDialog
{
	private final TextEditingPanel mapDisplayPanel;
	private JTextField editTextField;
	private final MapSettings settings;
	private BufferedImage mapWithoutText;
	private MapParts mapParts;
	private MapText lastSelected;
	private double zoom;
	private double initialResolution;
	JScrollPane scrollPane;
	private JComboBox<ToolType> toolComboBox;
	JComboBox<TextType>textTypeComboBox;
	private Point mousePressedLocation;
	ToolType lastTool;

	/**
	 * Creates a dialog for editing text.
	 * @param settings Settings for the map. The user's edits will be stored in settigns.edits.
	 * Other fields in settings may be modified in the editing process.
	 * @throws IOException 
	 */
	public EditTextDialog(final MapSettings settings, final RunSwing runSwing)
	{
		final EditTextDialog thisDialog = this;
		this.settings = settings;
		this.initialResolution = settings.resolution;
		final BufferedImage placeHolder = ImageHelper.read("assets/drawing_map.png");
		setBounds(100, 100, 935, 584);
		
		mapDisplayPanel = new TextEditingPanel(placeHolder);
		
		runSwing.btnClearTextEdits.setEnabled(true);

		getContentPane().setLayout(new BorderLayout());
		mapDisplayPanel.setLayout(new BorderLayout());
		mapDisplayPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		scrollPane = new JScrollPane(mapDisplayPanel);
		// Speed up the scroll speed.
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		
		getContentPane().add(scrollPane);
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton doneButton = new JButton("Done");
				doneButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e){
						thisDialog.dispatchEvent(new WindowEvent(
			                    thisDialog, WindowEvent.WINDOW_CLOSING));
					}
				});
				buttonPane.setLayout(new BorderLayout(0, 0));

				editTextField = new JTextField();
				buttonPane.add(editTextField, BorderLayout.WEST);
				editTextField.setColumns(20);
				buttonPane.add(doneButton, BorderLayout.EAST);
				
			}
			
			JPanel panel = new JPanel();
			buttonPane.add(panel, BorderLayout.CENTER);
			
			JLabel lblTools = new JLabel("Tool:");
			panel.add(lblTools);
			
			toolComboBox = new JComboBox<>();
			for (ToolType toolType : ToolType.values())
			{
				toolComboBox.addItem(toolType);
			}
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
			panel.add(toolComboBox);
			
			JLabel lblTextType = new JLabel("Text type:");
			panel.add(lblTextType);
			
			textTypeComboBox= new JComboBox<>();
			for (TextType type : TextType.values())
			{
				textTypeComboBox.addItem(type);				
			}
			textTypeComboBox.setSelectedItem(TextType.Other_mountains);
			panel.add(textTypeComboBox);
			textTypeComboBox.setEnabled(toolComboBox.getSelectedItem() == ToolType.Add);
			toolComboBox.setSelectedItem(ToolType.Edit); 		
			lastTool = (ToolType)toolComboBox.getSelectedItem();
			JLabel lblZoom = new JLabel("Zoom:");
			panel.add(lblZoom);
			
			final JComboBox<String> zoomComboBox = new JComboBox<>();
			zoomComboBox.addItem("25%");
			zoomComboBox.addItem("50%");
			zoomComboBox.addItem("75%");
			zoomComboBox.addItem("100%");
			zoomComboBox.setSelectedItem("50%");
			panel.add(zoomComboBox);
			setZoom((String)zoomComboBox.getSelectedItem());
			zoomComboBox.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					setZoom((String)zoomComboBox.getSelectedItem());
					mapDisplayPanel.setImage(placeHolder);
					mapDisplayPanel.clearAreasToDraw();
					mapParts = null;
					
					mapDisplayPanel.repaint();
					createAndShowMap();
				}
			});
			
		}
		
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
		
		editTextField.addKeyListener(new KeyListener()
		{	
			@Override
			public void keyTyped(KeyEvent e)
			{
			}
			
			@Override
			public void keyReleased(KeyEvent e)
			{
			}
			
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					handleTextEdit(lastSelected);	
				}
				else if ((e.getKeyCode() == KeyEvent.VK_S) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0))
				{
					handleTextEdit(lastSelected);	
					
					// Save
					runSwing.saveSettings(mapDisplayPanel);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_A) 
						&& ((e.getModifiers() & (KeyEvent.CTRL_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Add);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_E) 
						&& ((e.getModifiers() & (KeyEvent.CTRL_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Edit);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_R) 
						&& ((e.getModifiers() & (KeyEvent.CTRL_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Rotate);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_G) 
						&& ((e.getModifiers() & (KeyEvent.CTRL_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Move);
				}
				else if ((e.getKeyCode() == KeyEvent.VK_D) 
						&& ((e.getModifiers() & (KeyEvent.CTRL_MASK)) != 0))
				{
					toolComboBox.setSelectedItem(ToolType.Delete);
				}
				
			}
		});
		
		createAndShowMap();
	}
	
	private void updateToolText()
	{
		if (toolComboBox.getSelectedItem() == ToolType.Add)
		{
			toolComboBox.setToolTipText("Add new text of the selected text type (ctrl+A)");
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Edit)
		{
			toolComboBox.setToolTipText("Edit text (ctrl+E)");			
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Move)
		{
			toolComboBox.setToolTipText("Move text (ctrl+G)");			
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Rotate)
		{
			toolComboBox.setToolTipText("Rotate text (ctrl+R)");			
		}
		else if (toolComboBox.getSelectedItem() == ToolType.Delete)
		{
			toolComboBox.setToolTipText("Delete text (ctrl+D)");			
		}
	}
	
	/**
	 * Sets the zoom field according to the string selected from the zoom combo box.
	 */
	private void setZoom(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		zoom = 100.0 / zoomPercent;
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
	
	private void createAndShowMap()
	{
		// Change a few settings to make map creation faster.
		settings.resolution = initialResolution / zoom;
		settings.landBlur = 0;
		settings.oceanEffects = 0;
		settings.frayedBorder = false;
		settings.drawText = false;

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
					//ConcurrencyUtils.shutdownAndAwaitTermination();
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
		mapParts.textDrawer.drawText(mapParts.graph, mapWithText, mapParts.landBackground, mapParts.mountainGroups);
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

}
