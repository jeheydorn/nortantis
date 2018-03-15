package nortantis.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;

import nortantis.ImagePanel;
import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.TextDrawer;

public abstract class EditorTool
{
	protected double zoom;
	protected final MapEditingPanel mapEditingPanel;
	BufferedImage placeHolder;
	protected MapSettings settings;
	private JPanel toolOptionsPanel;
	protected MapParts mapParts;
	private EditorDialog parent;
	public static int spaceBetweenRowsOfComponents = 8;
	private JToggleButton toggleButton;
	private boolean mapNeedsRedraw;
	private boolean mapIsBeingDrawn;
	
	public EditorTool(MapSettings settings, EditorDialog parent)
	{
		this.settings = settings;
		this.parent = parent;
		placeHolder = createPlaceholderImage();
		this.mapEditingPanel = parent.mapEditingPanel;
		mapEditingPanel.setImage(placeHolder);
		toolOptionsPanel = createToolsOptionsPanel();
	}

	private BufferedImage createPlaceholderImage()
	{
		String message = "Drawing the map. Some details like borders and grunge are not shown in edit mode.";
		Font font = MapSettings.parseFont("URW Chancery L\t0\t25");
		Point textBounds = TextDrawer.getTextBounds(message, font);
		BufferedImage placeHolder = new BufferedImage(textBounds.x + 10, textBounds.y + 20, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = placeHolder.createGraphics();
		g.setFont(font);
		g.setColor(Color.BLACK);
		g.drawString(message, 8, textBounds.y + 5);
		return placeHolder;
	}
	
	public abstract String getToolbarName();
	
	public ImagePanel getDisplayPanel()
	{
		return mapEditingPanel;
	}
	
	public abstract void onBeforeSaving();
	
	public abstract void onSwitchingAway();
	
	public abstract void onSelected();
	
	protected abstract JPanel createToolsOptionsPanel();
	
	private static final int labelWidth = 80;
	private static final int labelHeight = 20;
	protected static JPanel addLabelAndComponentToPanel(JPanel panelToAddTo, JLabel label, JComponent component)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		int borderWidth = EditorDialog.borderWidthBetweenComponents;
		panel.setBorder(BorderFactory.createEmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth));
		label.setPreferredSize(new Dimension(labelWidth, labelHeight));
		
		JPanel labelPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
		labelPanel.add(label);
		labelPanel.add(Box.createVerticalGlue());
		panel.add(labelPanel);
		
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new BoxLayout(compPanel, BoxLayout.X_AXIS));
		compPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, spaceBetweenRowsOfComponents, 0));
		compPanel.add(component);
		panel.add(compPanel);
		panel.add(Box.createHorizontalGlue());
		panelToAddTo.add(panel);
		
		return panel;
	}
	
	protected static void addLabelAndComponentsToPanel(JPanel panelToAddTo, JLabel label, List<JComponent> components)
	{		
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new BoxLayout(compPanel, BoxLayout.Y_AXIS));
		for (JComponent comp : components)
		{
			compPanel.add(comp);
		}
		
		addLabelAndComponentToPanel(panelToAddTo, label, compPanel);
	}

	
	public JPanel getToolOptionsPanel()
	{
		return toolOptionsPanel;
	}
	
	/**
	 * Handles when zoom level changes in the main display.
	 * @param zoomLevel Between 0.25 and 1.
	 */
	public void handleZoomChange(double zoomLevel)
	{
		zoom = zoomLevel;
		mapEditingPanel.setImage(placeHolder);
		mapEditingPanel.clearAreasToDraw();
		mapParts = null;
		
		mapEditingPanel.repaint();
		createAndShowMap();
	}

	protected abstract void handleMouseClickOnMap(MouseEvent e);
	protected abstract void handleMousePressedOnMap(MouseEvent e);
	protected abstract void handleMouseReleasedOnMap(MouseEvent e);
	protected abstract void handleMouseMovedOnMap(MouseEvent e);
	protected abstract void handleMouseDraggedOnMap(MouseEvent e);
	protected abstract void handleMouseExitedMap(MouseEvent e);

	
	protected abstract void onBeforeCreateMap();
	
	/**
	 * Do any processing to the generated map before displaying it, and return the map to display.
	 * This is also the earliest time when mapParts is initialized.
	 * @param map The generated map
	 * @return The map to display
	 */
	protected abstract BufferedImage onBeforeShowMap(BufferedImage map, boolean mapNeedsRedraw);
	
	public void createAndShowMap()
	{
		if (mapIsBeingDrawn)
		{
			mapNeedsRedraw = true;
			return;
		}
		
		mapIsBeingDrawn = true;
		
		onBeforeCreateMap();

		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() 
	    {
	        @Override
	        public BufferedImage doInBackground() 
	        {	
				try
				{
					if (mapParts == null)
					{
						mapParts = new MapParts();
					}
					BufferedImage map = new MapCreator().createMap(settings, null, mapParts);
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
	            
	            if (map != null)
	            {	
	            	initializeCenterEditsIfEmpty();
	            	initializeRegionEditsIfEmpty();
	            	map = onBeforeShowMap(map, mapNeedsRedraw);
	            	
	            	mapEditingPanel.image = map;
	            	mapEditingPanel.repaint();
	            	// Tell the scroll pane to update itself.
	            	mapEditingPanel.revalidate();
	            }
	            
	            parent.enableOrDisableToolToggleButtons(true);

	            mapIsBeingDrawn = false;
	            
	            if (mapNeedsRedraw)
	            {
	            	createAndShowMap();
	            }
	            
            	mapNeedsRedraw = false;
	        }
	 
	    };
	    worker.execute();
	}
	
	public MapParts getMapParts()
	{
		return mapParts;
	}
	
	public void setMapParts(MapParts parts)
	{
		this.mapParts = parts;
	}
	
	public void setToggled(boolean toggled)
	{
		toggleButton.setSelected(toggled);
	}
	
	public void setToggleButton(JToggleButton toggleButton)
	{
		this.toggleButton = toggleButton;
	}
	
	public void setToggleButtonEnabled(boolean enabled)
	{
		toggleButton.setEnabled(enabled);
	}
	
	private void initializeCenterEditsIfEmpty()
	{
		if (settings.edits.centerEdits.isEmpty())
		{
			settings.edits.initializeCenterEdits(mapParts.graph.centers);			
		}
	}
	
	private void initializeRegionEditsIfEmpty()
	{
		if (settings.edits.regionEdits.isEmpty())
		{
			settings.edits.initializeRegionEdits(mapParts.graph.regions);			
		}
	}


}
