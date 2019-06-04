package nortantis.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;

import hoten.voronoi.Center;
import hoten.voronoi.Edge;
import hoten.voronoi.VoronoiGraph;
import nortantis.ImageCache;
import nortantis.ImagePanel;
import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextDrawer;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.Range;

public abstract class EditorTool
{
	protected double zoom;
	protected final MapEditingPanel mapEditingPanel;
	BufferedImage placeHolder;
	protected MapSettings settings;
	private JPanel toolOptionsPanel;
	protected MapParts mapParts;
	protected EditorFrame parent;
	public static int spaceBetweenRowsOfComponents = 8;
	private JToggleButton toggleButton;
	private boolean mapNeedsRedraw;
	private boolean mapNeedsQuickUpdate;
	private boolean mapIsBeingDrawn;
	private ReentrantLock drawLock;
	Stack<MapEdits> undoStack;
	Stack<MapEdits> redoStack;
	protected MapEdits copyOfEditsWhenToolWasSelected;
	protected List<Integer> brushSizes = Arrays.asList(1, 25, 70);
	protected boolean isMapVisible;
	
	public EditorTool(MapSettings settings, EditorFrame parent)
	{
		this.settings = settings;
		this.parent = parent;
		placeHolder = createPlaceholderImage();
		this.mapEditingPanel = parent.mapEditingPanel;
		mapEditingPanel.setImage(placeHolder);
		toolOptionsPanel = createToolsOptionsPanel();
		undoStack = new Stack<>();
		redoStack = new Stack<>();
		drawLock  = new ReentrantLock();
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
	
	public abstract String getImageIconFilePath();
	
	public ImagePanel getDisplayPanel()
	{
		return mapEditingPanel;
	}
	
	public abstract void onBeforeSaving();
	
	public abstract void onSwitchingAway();
	
	protected abstract JPanel createToolsOptionsPanel();
	
	private static final int labelWidth = 80;
	private static final int labelHeight = 20;
	protected static JPanel addLabelAndComponentToPanel(JPanel panelToAddTo, JLabel label, JComponent component)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		int borderWidth = EditorFrame.borderWidthBetweenComponents;
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
	
	protected static <T extends JComponent> JPanel addLabelAndComponentsToPanel(JPanel panelToAddTo, JLabel label, List<T> components)
	{		
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new BoxLayout(compPanel, BoxLayout.Y_AXIS));
		for (JComponent comp : components)
		{
			compPanel.add(comp);
		}
		
		return addLabelAndComponentToPanel(panelToAddTo, label, compPanel);
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
		isMapVisible = false;
		zoom = zoomLevel;
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
	protected abstract BufferedImage onBeforeShowMap(BufferedImage map, boolean isQuickUpdate);
	
	public void createAndShowMap()
	{
		createAndShowMap(false);
	}
	
	/**
	 * Redraws the map, then displays it
	 * @param quickUpdate If true, only a quick update will be done instead of redrawing the entire map. 
	 * 					  To use this, a child class must override drawMapQuickUpdate.
	 */
	public void createAndShowMap(boolean quickUpdate)
	{
		if (mapIsBeingDrawn)
		{
			if (quickUpdate)
			{
				mapNeedsQuickUpdate = true;
			}
			else
			{
				mapNeedsRedraw = true;
			}
			return;
		}
		
		mapIsBeingDrawn = true;
		parent.enableOrDisableToolToggleButtonsAndZoom(false);
		
		onBeforeCreateMap();

		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() 
	    {
	        @Override
	        public BufferedImage doInBackground() 
	        {	
	        	drawLock.lock();
				try
				{	
					if (quickUpdate)
					{
						return drawMapQuickUpdate();
					}
					
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
				finally
				{
					drawLock.unlock();
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
	            catch (InterruptedException ex) 
	            {
	                throw new RuntimeException(ex);
	            }
	            catch (ExecutionException ex)
	            {
	            	Throwable cause = ex.getCause();
	            	cause.printStackTrace();
	            	if (cause instanceof OutOfMemoryError)
	            	{
	            		JOptionPane.showMessageDialog(null, "Out of memory. Try allocating more memory to the Java heap space.", "Error", JOptionPane.ERROR_MESSAGE);
	            	}
	            	else
	            	{
	            		JOptionPane.showMessageDialog(null, ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	            	}
	            }
	            
	            if (map != null)
	            {	
					mapEditingPanel.setGraph(mapParts.graph);

	            	initializeCenterEditsIfEmpty();
	            	initializeRegionEditsIfEmpty();
	            	initializeEdgeEditsIfEmpty();
	            	
	            	if (copyOfEditsWhenToolWasSelected == null)  
	            	{
	            		copyOfEditsWhenToolWasSelected = deepCopyMapEdits(settings.edits);
	            	}
	            	map = onBeforeShowMap(map, quickUpdate);
	            	
	            	mapEditingPanel.image = map; 
	            	parent.enableOrDisableToolToggleButtonsAndZoom(true);

	            	mapIsBeingDrawn = false;
		            if (mapNeedsRedraw || mapNeedsQuickUpdate)
		            {
		            	createAndShowMap(!mapNeedsRedraw);
		            }
		            else
		            {
		         		mapEditingPanel.clearProcessingCenters();
		         		mapEditingPanel.clearProcessingEdges();
		            }
		            
		            mapNeedsQuickUpdate = false;
		            if (!quickUpdate)
		            {
		            	mapNeedsRedraw = false;
		            }
	             		     
		            mapEditingPanel.repaint();
		            // Tell the scroll pane to update itself.
		            mapEditingPanel.revalidate();   
		            isMapVisible = true;
	            }
	        }
	 
	    };
	    worker.execute();
	}
	
	protected BufferedImage drawMapQuickUpdate()
	{
		throw new IllegalStateException("This editor hasn't implemented quick updates");
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
			settings.edits.initializeCenterEdits(mapParts.graph.centers, mapParts.iconDrawer);			
		}
	}
	
	private void initializeEdgeEditsIfEmpty()
	{
		if (settings.edits.edgeEdits.isEmpty())
		{
			settings.edits.initializeEdgeEdits(mapParts.graph.edges);
		}
	}
	
	private void initializeRegionEditsIfEmpty()
	{
		if (settings.edits.regionEdits.isEmpty())
		{
			settings.edits.initializeRegionEdits(mapParts.graph.regions);			
		}
	}
	
	protected void setUndoPoint()
	{
		redoStack.clear();
		undoStack.push(deepCopyMapEdits(settings.edits));
		parent.updateUndoRedoEnabled();
	}
	
	public void undo()
	{
		redoStack.push(undoStack.pop());
		if (undoStack.isEmpty())
		{
			settings.edits = deepCopyMapEdits(copyOfEditsWhenToolWasSelected);
		}
		else
		{
			settings.edits = deepCopyMapEdits(undoStack.peek());	
		}
		onAfterUndoRedo();
	}
	
	public void redo()
	{
		undoStack.push(redoStack.pop());
		settings.edits = deepCopyMapEdits(undoStack.peek());
		onAfterUndoRedo();
	}
	
	protected MapEdits deepCopyMapEdits(MapEdits edits)
	{
		MapEdits copy = Helper.deepCopy(edits);
		// Explicitly copy edits.text.areas because it isn't serializable. 
		if (edits.text != null)
		{
			for (int i : new Range(edits.text.size()))
			{
				MapText otherText = edits.text.get(i);
				MapText resultText = copy.text.get(i);
				if (otherText.areas != null)
				{
					resultText.areas = new ArrayList<Area>(otherText.areas.size());
					for (Area area : otherText.areas)
					{
						resultText.areas.add(new Area(area));
					}
				}
			}
		}
		return copy;
	}
	
	protected abstract void onAfterUndoRedo();

	public void clearUndoRedoStacks()
	{
		undoStack.clear();
		redoStack.clear();
	}

	protected Set<Center> getSelectedCenters(java.awt.Point point, int brushDiameter)
	{
		Set<Center> selected = new HashSet<Center>();
		
		if (brushDiameter <= 1)
		{
			Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(point.getX(), point.getY()));
			if (center != null)
			{
				selected.add(center);
			}
			return selected;
		}
		
		int brushRadius = brushDiameter/2;
		for (int x = point.x - brushRadius; x < point.x + brushRadius; x++)
		{
			for (int y = point.y - brushRadius; y < point.y + brushRadius; y++)
			{
				float deltaX = (float)(point.x - x);
				float deltaXSquared = deltaX * deltaX;
				float deltaY = (float)(point.y - y);
				float deltaYSquared = deltaY * deltaY;
				if (Math.sqrt(deltaXSquared + deltaYSquared) <= brushRadius)
				{
					Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(x, y));
					if (center != null)
					{
						selected.add(center);
					}
				}
			}
		}
		return selected;
	}
	
	public void clearEntireMap()
	{
		if (mapParts == null || mapParts.graph == null)
		{
			return;
		}
		
		// Erase text
		if (mapParts.textDrawer == null)
		{
			// The text tool has not been opened. Draw the text once so we can erase it.
			mapParts.textDrawer = new TextDrawer(settings, MapCreator.calcSizeMultiplyer(mapParts.graph.getWidth()));	
			// Set the MapTexts in the TextDrawer to be the same object as settings.edits.text.
	    	// This makes it so that any edits done to the settings will automatically be reflected
	    	// in the text drawer. Also, it is necessary because the TextDrawer adds the Areas to the
	    	// map texts, which are needed to make them clickable to edit them.
			mapParts.textDrawer.setMapTexts(settings.edits.text);
			mapParts.textDrawer.drawText(mapParts.graph, ImageHelper.deepCopy(mapParts.landBackground), mapParts.landBackground, mapParts.mountainGroups, mapParts.cityDrawTasks);
		}
		for (MapText text : settings.edits.text)
		{
			text.value = "";
		}
		
		for (Center center : mapParts.graph.centers)
		{
			// Change land to ocean
			settings.edits.centerEdits.get(center.index).isWater = true;

			// Erase icons
			settings.edits.centerEdits.get(center.index).trees = null;
			settings.edits.centerEdits.get(center.index).icon = null;
			
			// Erase rivers
			for (Edge edge : center.borders)
			{
				EdgeEdit eEdit = settings.edits.edgeEdits.get(edge.index);
				if (eEdit.riverLevel >= VoronoiGraph.riversThinnerThanThisWillNotBeDrawn)
				{
					eEdit.riverLevel = 0;
				}
			}
		}
		
		setUndoPoint();
		createAndShowMap(false);
	}
}
