package nortantis;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints.Key;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;

import javax.swing.JTextField;
import javax.swing.JSeparator;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.Box;

import org.jtransforms.utils.ConcurrencyUtils;

import util.Helper;
import util.ImageHelper;
import util.Logger;
import util.Tuple2;

import javax.swing.JComboBox;
import javax.swing.JLabel;

public class EditTextDialog extends JDialog
{

	private final TextEditingPanel mapDisplayPanel;
	private JTextField editTextField;
	private MapSettings settings;
	private BufferedImage mapWithoutText;
	private MapParts mapParts;
	private Color highlightColor = new Color(255,227,74);
	private MapText lastSelected;
	private double zoom;
	JScrollPane scrollPane;
	private JComboBox<ToolType> toolComboBox;
	JComboBox<TextType>textTypeComboBox;

	/**
	 * Create the dialog.
	 * @throws IOException 
	 */
	public EditTextDialog(final MapSettings settings, final RunSwing runSwing)
	{
		final EditTextDialog thisDialog = this;
		this.settings = settings;
		final BufferedImage placeHolder = ImageHelper.read("assets/drawing_map.png");
		setBounds(100, 100, 935, 584);
		
		mapDisplayPanel = new TextEditingPanel(placeHolder);
		
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
					mapDisplayPanel.clearAreasToDraw();
					lastSelected = null;
					mapDisplayPanel.repaint();
					textTypeComboBox.setEnabled(toolComboBox.getSelectedItem() == ToolType.Add);
					editTextField.setText("");
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
			toolComboBox.setSelectedItem(ToolType.Add); // TODO change to Edit.
			
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
					createMapWithoutTextAndDisplayMapWithText(settings);
				}
			});
			
		}
		
		mapDisplayPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				handleMouseClickOnMap(e);
				runSwing.btnClearTextEdits.setEnabled(!settings.edits.editedText.isEmpty());
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
					handleUpdate(lastSelected);	
				}
				else if ((e.getKeyCode() == KeyEvent.VK_S) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0))
				{
					handleUpdate(lastSelected);	
					
					// Save
					runSwing.saveSettings(mapDisplayPanel);
				}
				
				runSwing.btnClearTextEdits.setEnabled(!settings.edits.editedText.isEmpty());
			}
		});
		
		createMapWithoutTextAndDisplayMapWithText(settings);
	}
	
	/**
	 * Sets the zoom field according to the string selected from the zoom combo box.
	 */
	private void setZoom(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		zoom = 100.0 / zoomPercent;
	}
		
	private void handleMouseClickOnMap(MouseEvent e)
	{
		// If the map has been drawn...
		if (mapParts != null)
		{
			Point clickPoint = e.getPoint();	
			if (toolComboBox.getSelectedItem().equals(ToolType.Edit))
			{
				MapText selectedText = mapParts.textDrawer.findTextPicked(clickPoint);
				handleUpdate(selectedText);
			}
			else if (toolComboBox.getSelectedItem().equals(ToolType.Add))
			{
				MapText addedText = mapParts.textDrawer.createUserAddedText((TextType)textTypeComboBox.getSelectedItem(), 
						new hoten.geom.Point(clickPoint.x, clickPoint.y));
				settings.edits.addedText.put(addedText.id, addedText);
				
				updateTextInBackgroundThread(null);
			}
		}
	}
	
	private void handleUpdate(MapText selectedText)
	{
		mapDisplayPanel.setAreasToDraw(selectedText == null ? null : selectedText.areas);
		if (lastSelected != null && !editTextField.getText().equals(lastSelected.text))
		{
			lastSelected.text = editTextField.getText();
			if (!settings.edits.addedText.containsKey(lastSelected.id))
			{
				// The text was not added by the user, but was generated by TextDrawer.drawText, then edited by the user.
				settings.edits.editedText.put(lastSelected.id, lastSelected);
			}

			// Need to re-draw all of the text.
			updateTextInBackgroundThread(selectedText);
		}
		else
		{
			// Just a quick highlights update.
			mapDisplayPanel.repaint();
		}
		
		if (selectedText == null)
		{
			editTextField.setText("");
		}
		else
		{
			editTextField.setText(selectedText.text);
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
					ConcurrencyUtils.shutdownAndAwaitTermination();
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
              	// TODO Fix bug where updated text still shows old bounding box. I think it needs to be fixed here.
            	mapDisplayPanel.repaint();
            	// Tell the scroll pane to update itself.
            	mapDisplayPanel.revalidate();	        
	        }
	    };
	    worker.execute();
	}
	
	private void createMapWithoutTextAndDisplayMapWithText(MapSettings settings)
	{
		final MapSettings settingsFinal = settings;
		final MapSettings settingsCopy = (MapSettings)Helper.deepCopy(settings);
		settings = null;
		settingsCopy.resolution /= zoom;
		settingsCopy.landBlur = 0;
		settingsCopy.oceanEffects = 0;
		settingsCopy.frayedBorder = false;
		settingsCopy.drawText = false;

		SwingWorker<Tuple2<BufferedImage, MapParts>, Void> worker = new SwingWorker<Tuple2<BufferedImage, MapParts>, Void>() 
	    {
	        @Override
	        public Tuple2<BufferedImage, MapParts> doInBackground() 
	        {	
				try
				{
					MapParts parts = new MapParts();
					BufferedImage map = new MapCreator().createMap(settingsCopy, null, parts);
					return new Tuple2<>(map, parts);
				} 
				catch (Exception e)
				{
					e.printStackTrace();
					ConcurrencyUtils.shutdownAndAwaitTermination();
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
	            	// I need to the mapParts textDrawer to have the original settings object, not a copy,
	            	// so that when the user edits text, the changes are displayed.
	            	mapParts.textDrawer.setSettings(settingsFinal);
	            
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
		mapParts.textDrawer.reset();
		mapParts.textDrawer.drawText(mapParts.graph, mapWithText, mapParts.landBackground, mapParts.mountainGroups);
		return mapWithText;
	}
	
	enum ToolType
	{
		Edit,
		Move,
		Add,
		Rotate
	}

}
