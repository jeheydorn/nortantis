package nortantis.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

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
import hoten.voronoi.Corner;
import hoten.voronoi.Edge;
import hoten.voronoi.VoronoiGraph;
import nortantis.ImagePanel;
import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextDrawer;
import nortantis.util.ImageHelper;

public abstract class EditorTool
{
	protected final MapEditingPanel mapEditingPanel;
	BufferedImage placeHolder;
	protected MapSettings settings;
	private JPanel toolOptionsPanel;
	protected EditorFrame parent;
	public static int spaceBetweenRowsOfComponents = 8;
	private JToggleButton toggleButton;
	private boolean mapNeedsFullRedraw;
	private ArrayDeque<IncrementalUpdate> incrementalUpdatesToDraw;
	private boolean mapIsBeingDrawn;
	private ReentrantLock drawLock;
	protected List<Integer> brushSizes = Arrays.asList(1, 25, 70);
	protected Undoer undoer;
	
	public EditorTool(MapSettings settings, EditorFrame parent)
	{
		this.settings = settings;
		this.parent = parent;
		placeHolder = createPlaceholderImage();
		this.mapEditingPanel = parent.mapEditingPanel;
		mapEditingPanel.setImage(placeHolder);
		toolOptionsPanel = createToolsOptionsPanel();
		drawLock  = new ReentrantLock();
		incrementalUpdatesToDraw = new ArrayDeque<>();
		this.undoer = parent.undoer;
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
	
	public abstract void onActivate();
	
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
	 * Handles when zoom level changes in the display.
	 */
	public void handleZoomChange()
	{
		parent.isMapReadyForInteractions = false;
		mapEditingPanel.clearAreasToDraw();
		
		mapEditingPanel.repaint();
		createAndShowMapFull();
	}

	protected abstract void handleMouseClickOnMap(MouseEvent e);
	protected abstract void handleMousePressedOnMap(MouseEvent e);
	protected abstract void handleMouseReleasedOnMap(MouseEvent e);
	protected abstract void handleMouseMovedOnMap(MouseEvent e);
	protected abstract void handleMouseDraggedOnMap(MouseEvent e);
	protected abstract void handleMouseExitedMap(MouseEvent e);

	
	protected void onBeforeCreateMapFull()
	{
		settings.resolution = parent.zoom;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;
		settings.alwaysUpdateLandBackgroundWithOcean = true;
	}
	
	/**
	 * Do any processing to the generated map before displaying it, and return the map to display.
	 * This is also the earliest time when mapParts is initialized.
	 * @param map The generated map
	 * @return The map to display
	 */
	protected abstract BufferedImage onBeforeShowMap(BufferedImage map);
	
	public void updateChangedCentersOnMap(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null);
	}
	
	public void updateChangedEdgesOnMap(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged);
	}
	
	/**
	 * Redraws the map, then displays it. Use only with UpdateType.Full and UpdateType.Quick.
	 */
	public void createAndShowMapFull()
	{		
		createAndShowMap(UpdateType.Full, null, null);
	}
	
	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null);
	}
	
	public void createAndShowMapIncrementalUsingEdges(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged);
	}
	
	public void createAndShowMapFromChange(MapEdits change)
	{		
		Set<Center> centersChanged = getCentersWithChangesInEdits(change);
		Set<Edge> edgesChanged = null;
		// Currently createAndShowMap doesn't support drawing both center edits and edge edits at the same time, so there is no
		// need to find edges changed if centers were changed.
		if (centersChanged.size() == 0)
		{
			edgesChanged = getEdgesWithChangesInEdits(change);
		}
		createAndShowMap(UpdateType.Incremental, centersChanged, edgesChanged);
	}
	
	private Set<Center> getCentersWithChangesInEdits(MapEdits changeEdits)
	{
		return settings.edits.centerEdits.stream().filter(cEdit -> !cEdit.equals(changeEdits.centerEdits.get(cEdit.index)))
		.map(cEdit -> parent.mapParts.graph.centers.get(cEdit.index))
		.collect(Collectors.toSet());
	}
	
	private Set<Edge> getEdgesWithChangesInEdits(MapEdits changeEdits)
	{
		return settings.edits.edgeEdits.stream().filter(eEdit -> !eEdit.equals(changeEdits.edgeEdits.get(eEdit.index)))
		.map(eEdit -> parent.mapParts.graph.edges.get(eEdit.index))
		.collect(Collectors.toSet());
	}
	
	/**
	 * Redraws the map, then displays it
	 */
	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged)
	{
		if (mapIsBeingDrawn)
		{
			if (updateType == UpdateType.Full)
			{
				mapNeedsFullRedraw = true;
				incrementalUpdatesToDraw.clear();
			}
			else if (updateType == UpdateType.Incremental)
			{
				if (centersChanged == null && edgesChanged == null)
				{
					throw new IllegalArgumentException("Either centersChanged or edgesChagned must be passed in.");
				}

				incrementalUpdatesToDraw.add(new IncrementalUpdate(centersChanged, edgesChanged));
			}
			return;
		}
		
		mapIsBeingDrawn = true;
		parent.enableOrDisableToolToggleButtonsAndZoom(false);
		
		if (updateType == UpdateType.Full)
		{
			onBeforeCreateMapFull();
		}
		
		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() 
	    {
	        @Override
	        public BufferedImage doInBackground() throws IOException 
	        {	
	        	drawLock.lock();
				try
				{	
					if (updateType == UpdateType.Full)
					{
						if (parent.mapParts == null)
						{
							parent.mapParts = new MapParts();
						}
						BufferedImage map = new MapCreator().createMap(settings, null, parent.mapParts);	
						System.gc();
						return map;
					}
					else
					{
						BufferedImage map = mapEditingPanel.mapFromMapCreator;
						// Incremental update
						if (centersChanged != null)
						{
							new MapCreator().incrementalUpdateCenters(settings, parent.mapParts, map, centersChanged);
							return map;
						}
						else if (edgesChanged != null)
						{
							new MapCreator().incrementalUpdateEdges(settings, parent.mapParts, map, edgesChanged);
							return map;
						}
						throw new IllegalStateException("Map cannot be re-drawn incremental without passing in what changed.");
					}
				} 
				finally
				{
					drawLock.unlock();
				}
	        }
	        
	        @Override
	        public void done()
	        {
	            try 
	            {
	            	mapEditingPanel.mapFromMapCreator = get();
	            } 
	            catch (InterruptedException ex) 
	            {
	                throw new RuntimeException(ex);
	            }
	            catch (Exception ex)
	            {
	            	if (isCausedByOutOfMemoryError(ex))
	            	{
	            		String outOfMemoryMessage =  "Out of memory. Try lowering the zoom or allocating more memory to the Java heap space.";
	            		JOptionPane.showMessageDialog(null, outOfMemoryMessage, "Error", JOptionPane.ERROR_MESSAGE);
	            		ex.printStackTrace();
	            	}
	            	else
	            	{
	            		JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	            		ex.printStackTrace();
	            	}
	            }
	            
	            if (mapEditingPanel.mapFromMapCreator != null)
	            {	
					mapEditingPanel.setGraph(parent.mapParts.graph);

	            	initializeCenterEditsIfEmpty();
	            	initializeRegionEditsIfEmpty();
	            	initializeEdgeEditsIfEmpty();
	            	
	            	if (undoer.copyOfEditsWhenEditorWasOpened == null)  
	            	{
	            		// This has to be done after the map is drawn rather than when the editor frame is first created because
	            		// the first time the map is drawn is when the edits are created.
	            		undoer.copyOfEditsWhenEditorWasOpened = settings.edits.deepCopy();
	            	}
	            	
	            	mapEditingPanel.image = onBeforeShowMap(mapEditingPanel.mapFromMapCreator);
	            	
	            	parent.enableOrDisableToolToggleButtonsAndZoom(true);

	            	mapIsBeingDrawn = false;
		            if (mapNeedsFullRedraw)
		            {
		            	createAndShowMapFull();
		            }
		            else if (updateType == UpdateType.Incremental && incrementalUpdatesToDraw.size() > 0)
		            {
	            		IncrementalUpdate incrementalUpdate = combineAndGetNextIncrementalUpdateToDraw();
	            		createAndShowMap(UpdateType.Incremental, incrementalUpdate.centersChanged, incrementalUpdate.edgesChanged);
	            	}
		            else
	            	{
		         		mapEditingPanel.clearProcessingEdges();
		         		// Add back the centers and edges not yet processed.
		         		for (IncrementalUpdate incrementalUpdate : incrementalUpdatesToDraw)
		         		{
		         			if (incrementalUpdate.centersChanged != null)
		         			{
		         				mapEditingPanel.addAllSelectedCenters(incrementalUpdate.centersChanged);
		         			}
		         			if (incrementalUpdate.edgesChanged != null)
		         			{
		         				mapEditingPanel.addAllProcessingEdges(edgesChanged);
		         			}
		         		}
	            	}
		            
		            if (updateType == UpdateType.Full)
		            {
		            	mapNeedsFullRedraw = false;
		            }
	             		     
		            mapEditingPanel.repaint();
		            // Tell the scroll pane to update itself.
		            mapEditingPanel.revalidate();   
		            parent.isMapReadyForInteractions = true;
	            }
	            else
	            {
	            	parent.enableOrDisableToolToggleButtonsAndZoom(true);
	            	mapEditingPanel.clearSelectedCenters();
	         		mapEditingPanel.clearProcessingEdges();
	         		mapIsBeingDrawn = false;
	            }
	        }
	 
	    };
	    worker.execute();
	}
	
	/**
	 * Combines the incremental updates in incrementalUpdatesToDraw so they can be drawn together. Clears out incrementalUpdatesToDraw.
	 * @return The combined update to draw
	 */
	private IncrementalUpdate combineAndGetNextIncrementalUpdateToDraw()
	{
		if (incrementalUpdatesToDraw.size() == 0)
		{
			return null;
		}
		
		IncrementalUpdate result = incrementalUpdatesToDraw.pop();
		if (incrementalUpdatesToDraw.size() == 1)
		{
			return result;
		}
		
		while (incrementalUpdatesToDraw.size() > 0)
		{
			IncrementalUpdate next = incrementalUpdatesToDraw.pop();
			result.add(next);
		}
		return result;
	}
	
	private boolean isCausedByOutOfMemoryError(Throwable ex)
	{
		if (ex == null)
		{
			return false;
		}
		
		if (ex instanceof OutOfMemoryError)
		{
			return true;
		}
		
		return isCausedByOutOfMemoryError(ex.getCause());
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
			settings.edits.initializeCenterEdits(parent.mapParts.graph.centers, parent.mapParts.iconDrawer);			
		}
	}
	
	private void initializeEdgeEditsIfEmpty()
	{
		if (settings.edits.edgeEdits.isEmpty())
		{
			settings.edits.initializeEdgeEdits(parent.mapParts.graph.edges);
		}
	}
	
	private void initializeRegionEditsIfEmpty()
	{
		if (settings.edits.regionEdits.isEmpty())
		{
			settings.edits.initializeRegionEdits(parent.mapParts.graph.regions.values());			
		}
	}
	
	protected abstract void onAfterUndoRedo(MapEdits changeEdits);
	
	protected Set<Center> getSelectedCenters(java.awt.Point point, int brushDiameter)
	{
		Set<Center> selected = new HashSet<Center>();
		
		Center center = parent.mapParts.graph.findClosestCenter(new hoten.geom.Point(point.getX(), point.getY()));
		if (center != null)
		{
			selected.add(center);
		}
			
		if (brushDiameter <= 1)
		{
			return selected;
		}
		
		int brushRadius = brushDiameter/2;
		
		// Add any polygons within the brush that were too small (< 1 pixel) to be picked up before.
		return parent.mapParts.graph.breadthFirstSearch((c) -> isCenterOverlappingCircle(c, point, brushRadius), center);
	}
	
	/**
	 * Determines if a center is overlapping the given circle. 
	 * Note that this isn't super precise because it doesn't account for the edge of the circle protruding into the center without overlapping
	 * any of the center's corners or centroid.
	 */
	private boolean isCenterOverlappingCircle(Center center, Point circleCenter, double radius)
	{
		for (Corner corner : center.corners)
		{
			if (isPointWithinCircle(corner.loc.x, corner.loc.y, circleCenter, radius))
			{
				return true;
			}
		}
		
		return isPointWithinCircle(center.loc.x, center.loc.y, circleCenter, radius);
	}
	
	private boolean isPointWithinCircle(double x, double y, Point circleCenter, double radius)
	{
		double deltaX = x - circleCenter.x;
		double deltaY = y - circleCenter.y;
		return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY)) <= radius;
	}
	
	public void clearEntireMap()
	{
		if (parent.mapParts == null || parent.mapParts.graph == null)
		{
			return;
		}
		
		// Erase text
		if (parent.mapParts.textDrawer == null)
		{
			// The text tool has not been opened. Draw the text once so we can erase it.
			parent.mapParts.textDrawer = new TextDrawer(settings, MapCreator.calcSizeMultiplier(parent.mapParts.graph.getWidth()));	
			parent.mapParts.textDrawer.drawText(parent.mapParts.graph, 
					ImageHelper.deepCopy(parent.mapParts.landBackground), parent.mapParts.landBackground, parent.mapParts.mountainGroups, parent.mapParts.cityDrawTasks);
		}
		for (MapText text : settings.edits.text)
		{
			text.value = "";
		}
		
		for (Center center : parent.mapParts.graph.centers)
		{
			// Change land to ocean
			settings.edits.centerEdits.get(center.index).isWater = true;
			settings.edits.centerEdits.get(center.index).isLake = false;

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
		
		undoer.setUndoPoint(UpdateType.Full, null);
		createAndShowMapFull();
	}
	
	private class IncrementalUpdate
	{
		public IncrementalUpdate(Set<Center> centersChanged, Set<Edge> edgesChanged)
		{
			if (centersChanged != null)
			{
				this.centersChanged = new HashSet<Center>(centersChanged);
			}
			if (edgesChanged != null)
			{
				this.edgesChanged = new HashSet<Edge>(edgesChanged);
			}
		}
		
		Set<Center> centersChanged;
		Set<Edge> edgesChanged;
		
		public void add(IncrementalUpdate other)
		{
			if (other == null)
			{
				return;
			}
			
			if (centersChanged != null && other.centersChanged != null)
			{
				centersChanged.addAll(other.centersChanged);
			}
			else if (centersChanged == null && other.centersChanged != null)
			{
				centersChanged = new HashSet<>(other.centersChanged);
			}
			
			if (edgesChanged != null && other.edgesChanged != null)
			{
				edgesChanged.addAll(other.edgesChanged);
			}
			else if (edgesChanged == null && other.edgesChanged != null)
			{
				edgesChanged = new HashSet<>(other.edgesChanged);
			}
		}
	}
}
