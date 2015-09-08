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

import javax.swing.Box;

import org.jtransforms.utils.ConcurrencyUtils;

import util.Helper;
import util.ImageHelper;
import util.Logger;
import util.Tuple2;

public class EditTextDialog extends JDialog
{

	private final ImagePanel mapDisplayPanel;
	private JTextField editTextField;
	private MapSettings settings;
	private BufferedImage mapWithoutText;
	private MapParts mapParts;
	private Color highlightColor = new Color(255,227,74);
	private MapText lastSelected;

	/**
	 * Create the dialog.
	 * @throws IOException 
	 */
	public EditTextDialog(MapSettings settings, final RunSwing runSwing)
	{
		final EditTextDialog thisDialog = this;
		this.settings = settings;
		BufferedImage placeHolder = ImageHelper.read("/home/joseph/Documents/FantasyMapCreator/workspace/nortantis/assets/drawing_map.jpg");
		setBounds(100, 100, placeHolder.getWidth(), placeHolder.getHeight());
		
		mapDisplayPanel = new ImagePanel(placeHolder);
		//contentPanel.setPreferredSize(new Dimension(map.getWidth(), map.getHeight()));
		createMapWithoutTextAndDisplayMapWithText(settings);
		
		getContentPane().setLayout(new BorderLayout());
		mapDisplayPanel.setLayout(new BorderLayout());
		mapDisplayPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		JScrollPane scrollPane = new JScrollPane(mapDisplayPanel);
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
		}
		
		mapDisplayPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				handleMouseClickOnMap(e);
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
			}
		});
		
	}
		
	private void handleMouseClickOnMap(MouseEvent e)
	{
		// If the map has been drawn...
		if (mapParts != null)
		{
			//long startTime = System.currentTimeMillis();
			
			Point imagePoint = mapDisplayPanel.toImageContext(e.getPoint());			
			MapText selectedText = mapParts.textDrawer.findTextPicked(imagePoint);
			handleUpdate(selectedText);
			
			//double elapsedTime = System.currentTimeMillis() - startTime;
			//System.out.println("Total time to update text (in seconds): " + elapsedTime / 1000.0);
		}
	}
	
	private void handleUpdate(MapText selectedText)
	{
		
		if (lastSelected != null && !editTextField.getText().equals(lastSelected.text))
		{
			lastSelected.text = editTextField.getText();
			settings.edits.editedText.put(lastSelected.id, lastSelected);
		}
		
		if (selectedText == null)
		{
			editTextField.setText("");
		}
		else
		{
			editTextField.setText(selectedText.text);
		}
		
		updateTextInBackgroundThread(selectedText);
		
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
					
					if (selectedText != null)
					{
						// Highlight the text the click was on.
						Graphics2D g = map.createGraphics();
						g.setColor(highlightColor);
						for (Area a : selectedText.areas)
							g.draw(a);
					
					}
					
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
            	mapDisplayPanel.setPreferredSize(new Dimension(map.getWidth(), map.getHeight()));
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
		settingsCopy.resolution /= 2.0; // TODO make this an option.
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

}
