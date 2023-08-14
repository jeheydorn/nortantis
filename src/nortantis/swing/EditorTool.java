package nortantis.swing;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;

import nortantis.MapSettings;
import nortantis.editor.MapChange;
import nortantis.editor.MapUpdater;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;

public abstract class EditorTool
{
	protected final MapEditingPanel mapEditingPanel;
	private JPanel toolOptionsPanel;
	protected MainWindow mainWindow;
	private JToggleButton toggleButton;
	protected Undoer undoer;
	protected ToolsPanel toolsPanel;
	protected List<Integer> brushSizes = Arrays.asList(1, 25, 70);
	protected MapUpdater mapUpdater;
	
	
	public EditorTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		this.mainWindow = parent;
		this.toolsPanel = toolsPanel;
		mapEditingPanel = parent.mapEditingPanel;
		toolOptionsPanel = createToolsOptionsPanel();
		undoer = parent.undoer;
		this.mapUpdater = mapUpdater;
	}
	
	public abstract String getToolbarName();
	
	public abstract String getImageIconFilePath();
	
	public abstract void onBeforeSaving();
	
	public abstract void onSwitchingAway();
	
	public abstract void onActivate();
	
	protected abstract JPanel createToolsOptionsPanel();
	


	
	public JPanel getToolOptionsPanel()
	{
		return toolOptionsPanel;
	}

	protected abstract void handleMouseClickOnMap(MouseEvent e);
	protected abstract void handleMousePressedOnMap(MouseEvent e);
	protected abstract void handleMouseReleasedOnMap(MouseEvent e);
	protected abstract void handleMouseMovedOnMap(MouseEvent e);
	protected abstract void handleMouseDraggedOnMap(MouseEvent e);
	protected abstract void handleMouseExitedMap(MouseEvent e);
	
	/**
	 * Do any processing to the generated map before displaying it, and return the map to display.
	 * This is also the earliest time when mapParts is initialized.
	 * @param map The generated map
	 * @return The map to display
	 */
	protected abstract BufferedImage onBeforeShowMap(BufferedImage map);
	

	public void setToggled(boolean toggled)
	{
		toggleButton.setSelected(toggled);
		updateBorder();
	}
	
	public void updateBorder()
	{
		if (UIManager.getLookAndFeel() instanceof FlatDarkLaf)
		{
			final int width = 4;
			if (toggleButton.isSelected())
			{
				int shade = 140;
				toggleButton.setBorder(BorderFactory.createLineBorder(new Color(shade, shade, shade), width));
			}
			else
			{
				toggleButton.setBorder(BorderFactory.createEmptyBorder(width, width, width, width));
			}
		}
	}
	
	public void setToggleButton(JToggleButton toggleButton)
	{
		this.toggleButton = toggleButton;
	}
	
	public void setToggleButtonEnabled(boolean enabled)
	{
		toggleButton.setEnabled(enabled);
	}
	
	protected abstract void onAfterUndoRedo(MapChange change);
	
	public nortantis.graph.geom.Point getPointOnGraph(java.awt.Point point)
	{
		return new nortantis.graph.geom.Point(point.x * (1.0 / mainWindow.zoom), point.y * (1.0 / mainWindow.zoom));
	}
	
	protected Set<Center> getSelectedCenters(java.awt.Point pointFromMouse, int brushDiameter)
	{
		Set<Center> selected = new HashSet<Center>();
		
		Center center = mapUpdater.mapParts.graph.findClosestCenter(getPointOnGraph(pointFromMouse));
		if (center != null)
		{
			selected.add(center);
		}
			
		if (brushDiameter <= 1)
		{
			return selected;
		}
		
		int brushRadius = (int)((double)(brushDiameter / mainWindow.zoom)) / 2;
		
		// Add any polygons within the brush that were too small (< 1 pixel) to be picked up before.
		return mapUpdater.mapParts.graph.breadthFirstSearch((c) -> isCenterOverlappingCircle(c, getPointOnGraph(pointFromMouse), brushRadius), center);
	}
	
	
	protected Set<Edge> getSelectedEdges(java.awt.Point pointFromMouse, int brushDiameter)
	{
		if (brushDiameter <= 1)
		{
			return Collections.singleton(getClosestEdge(getPointOnGraph(pointFromMouse)));
		}
		else
		{
			nortantis.graph.geom.Point graphPoint = getPointOnGraph(pointFromMouse);
			Center closestCenter = mapUpdater.mapParts.graph.findClosestCenter(graphPoint);
			Set<Center> overlapping = mapUpdater.mapParts.graph.breadthFirstSearch(
					(c) -> isCenterOverlappingCircle(c, graphPoint, brushDiameter / mainWindow.zoom), closestCenter);
			Set<Edge> selected = new HashSet<>();
			int brushRadius = (int)((double)(brushDiameter / mainWindow.zoom)) / 2;
			for (Center center : overlapping)
			{
				for (Edge edge : center.borders)
				{
					if ((edge.v0 != null && edge.v0.loc.distanceTo(graphPoint) <= brushRadius)
							|| edge.v1 != null && edge.v1.loc.distanceTo(graphPoint) <= brushRadius)
					{
						selected.add(edge);
					}
				}
			}
			return selected;
		}
	}
	
	private Edge getClosestEdge(nortantis.graph.geom.Point point)
	{
		Center center = mapUpdater.mapParts.graph.findClosestCenter(point);
		Edge closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (Edge edge : center.borders)
		{
			nortantis.graph.geom.Point centroid;
			if (edge.v0 == null && edge.v1 != null)
			{
				centroid = edge.v1.loc;
			}
			else if (edge.v1 == null && edge.v0 != null)
			{
				centroid = edge.v0.loc;
			}
			else if (edge.v0 == null && edge.v1 == null)
			{
				continue;
			}
			else
			{
				centroid = edge.v0.loc.add(edge.v1.loc).mult(0.5);	
			}
			
			if (centroid == null)
			{
				continue;
			}
			
			if (closest == null)
			{
				closest = edge;
				if (centroid != null)
				{
					closestDistance = centroid.distanceTo(point);
				}
				continue;
			}
			else
			{
				if (centroid != null)
				{
					double distance = centroid.distanceTo(point);
					if (distance < closestDistance)
					{
						closest = edge;
						closestDistance = distance;
					}
				}
				
			}
		}
		return closest;
	}
	
	/**
	 * Determines if a center is overlapping the given circle. 
	 * Note that this isn't super precise because it doesn't account for the edge of the circle protruding into the center without overlapping
	 * any of the center's corners or centroid.
	 */
	private boolean isCenterOverlappingCircle(Center center, nortantis.graph.geom.Point circleCenter, double radius)
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
	
	private boolean isPointWithinCircle(double x, double y, nortantis.graph.geom.Point circleCenter, double radius)
	{
		double deltaX = x - circleCenter.x;
		double deltaY = y - circleCenter.y;
		return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY)) <= radius;
	}
	
	public abstract void loadSettingsIntoGUI(MapSettings settings);
	
	public abstract void getSettingsFromGUI(MapSettings settings);
}
