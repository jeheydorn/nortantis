package nortantis.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import nortantis.ImagePanel;
import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import util.ImageHelper;
import util.Tuple2;

public abstract class EditorTool
{
	protected double zoom;
	protected final MapEditingPanel mapEditingPanel;
	BufferedImage placeHolder;
	protected MapSettings settings;
	private JPanel toolOptionsPanel;
	protected MapParts mapParts;
	
	public EditorTool(MapSettings settings)
	{
		this.settings = settings;
		placeHolder = ImageHelper.read("assets/drawing_map.png");
		mapEditingPanel = new MapEditingPanel(placeHolder);
		mapEditingPanel.setLayout(new BorderLayout());
		toolOptionsPanel = createToolsOptionsPanel();
		
		mapEditingPanel.addMouseListener(new MouseAdapter()
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
		
		mapEditingPanel.addMouseMotionListener(new MouseMotionListener()
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

	
	public abstract String getToolbarName();
	
	public abstract ImagePanel getDisplayPanel();
	
	public abstract void onBeforeSaving();
	
	protected abstract JPanel createToolsOptionsPanel();
	
	protected static void addLabelAndComponentToPanel(JPanel panelToAddTo, JLabel label, JComponent component)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		int borderWidth = EditorDialog.borderWidthBetweenComponents;
		panel.setBorder(BorderFactory.createEmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth));
		label.setPreferredSize(new Dimension(80, 20));
		panel.add(label);
		panel.add(component);
		panel.add(Box.createHorizontalGlue());
		panelToAddTo.add(panel);
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
	protected abstract void handleMouseDraggedOnMap(MouseEvent e);
	
	protected abstract void onBeforeCreateMap();
	protected abstract BufferedImage onBeforeShowMap(BufferedImage map);
	
	private void createAndShowMap()
	{
		onBeforeCreateMap();

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
	            	BufferedImage map = tuple.getFirst();
	            	mapParts = tuple.getSecond();
	            	
	            	map = onBeforeShowMap(map);
	            	
	            	mapEditingPanel.image = map;
	            	mapEditingPanel.repaint();
	            	// Tell the scroll pane to update itself.
	            	mapEditingPanel.revalidate();
	            }
	        }
	 
	    };
	    worker.execute();

	}

}
