package nortantis.swing;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

import nortantis.MapSettings;
import nortantis.editor.EdgeType;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;

public abstract class EditorTool
{
	protected final MapEditingPanel mapEditingPanel;
	private JPanel toolOptionsPanel;
	private JScrollPane toolsOptionsPanelContainer;
	protected MainWindow mainWindow;
	private JToggleButton toggleButton;
	protected Undoer undoer;
	protected ToolsPanel toolsPanel;
	protected List<Integer> brushSizes = Arrays.asList(1, 25, 70, 140);
	protected MapUpdater updater;

	public EditorTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		this.mainWindow = parent;
		this.toolsPanel = toolsPanel;
		mapEditingPanel = parent.mapEditingPanel;
		toolOptionsPanel = createToolOptionsPanel();
		toolsOptionsPanelContainer = new JScrollPane(toolOptionsPanel);
		toolsOptionsPanelContainer.setBorder(BorderFactory.createEmptyBorder());
		undoer = parent.undoer;
		this.updater = mapUpdater;
	}

	public abstract String getToolbarName();

	public abstract int getMnemonic();

	public abstract String getKeyboardShortcutText();

	public abstract String getImageIconFilePath();

	public abstract void onBeforeSaving();

	public void onSwitchingTo()
	{
		// This is needed so that highlights in the overlay tool clear all the way when switching to other tools. I don't know why, maybe a
		// bug in Swing.
		mainWindow.revalidate();
		mainWindow.repaint();
	}

	public abstract void onSwitchingAway();

	protected abstract JPanel createToolOptionsPanel();
	
	public JPanel getToolOptionsPanel()
	{
		return toolOptionsPanel;
	}
	
	public JScrollPane getToolOptionsPane()
	{
		return toolsOptionsPanelContainer;
	}

	protected abstract void handleMouseClickOnMap(MouseEvent e);

	protected abstract void handleMousePressedOnMap(MouseEvent e);

	protected abstract void handleMouseReleasedOnMap(MouseEvent e);

	protected abstract void handleMouseMovedOnMap(MouseEvent e);

	protected abstract void handleMouseDraggedOnMap(MouseEvent e);

	protected abstract void handleMouseExitedMap(MouseEvent e);

	protected abstract void onAfterShowMap();

	public void setToggled(boolean toggled)
	{
		toggleButton.setSelected(toggled);
		updateBorder();
	}

	public void updateBorder()
	{
		final int width = 4;
		
		if (UserPreferences.getInstance().lookAndFeel == LookAndFeel.System)
		{
			toggleButton.setBorder(BorderFactory.createEmptyBorder(width, width, width, width));
			return;
		}
		
		if (toggleButton.isSelected())
		{
			toggleButton.setBorder(BorderFactory.createLineBorder(ToolsPanel.getColorForToggledButtons(), width));
		}
		else
		{
			toggleButton.setBorder(BorderFactory.createEmptyBorder(width, width, width, width));
		}
	}

	public void setToggleButton(JToggleButton toggleButton)
	{
		this.toggleButton = toggleButton;
	}

	protected abstract void onAfterUndoRedo();

	protected abstract void onBeforeUndoRedo();

	public nortantis.geom.Point getPointOnGraph(java.awt.Point pointOnMapEditingPanel)
	{
		if (pointOnMapEditingPanel == null)
		{
			return null;
		}
		
		int borderWidth = updater.mapParts.background.getBorderPaddingScaledByResolution();
		double zoom = mainWindow.zoom;
		double osScale = mapEditingPanel.osScale;
		return new nortantis.geom.Point((((pointOnMapEditingPanel.x - (borderWidth * zoom * (1.0 / osScale))) * (1.0 / zoom) * osScale)),
				(((pointOnMapEditingPanel.y - (borderWidth * zoom) * (1.0 / osScale)) * (1.0 / zoom) * osScale)));
	}

	protected Set<Center> getSelectedCenters(java.awt.Point pointFromMouse, int brushDiameter)
	{
		Set<Center> selected = new HashSet<Center>();
		int brushRadius = (int) ((double) ((brushDiameter / mainWindow.zoom)) * mapEditingPanel.osScale) / 2;

		if (!new RotatedRectangle(updater.mapParts.graph.bounds).overlapsCircle(getPointOnGraph(pointFromMouse), brushRadius))
		{
			// The brush is off the map.
			return selected;
		}

		Center center = updater.mapParts.graph.findClosestCenter(getPointOnGraph(pointFromMouse));
		if (center == null)
		{
			return selected;
		}
		else
		{
			selected.add(center);
		}

		if (brushDiameter <= 1)
		{
			return selected;
		}

		return updater.mapParts.graph.breadthFirstSearch((c) -> isCenterOverlappingCircle(c, getPointOnGraph(pointFromMouse), brushRadius),
				center);
	}

	protected Set<Edge> getSelectedEdges(java.awt.Point pointFromMouse, int brushDiameter, EdgeType edgeType)
	{
		if (brushDiameter <= 1)
		{
			return Collections.singleton(getClosestEdge(getPointOnGraph(pointFromMouse)));
		}
		else
		{
			nortantis.geom.Point graphPoint = getPointOnGraph(pointFromMouse);
			Center closestCenter = updater.mapParts.graph.findClosestCenter(graphPoint);
			Set<Center> overlapping = updater.mapParts.graph
					.breadthFirstSearch((c) -> isCenterOverlappingCircle(c, graphPoint, brushDiameter / mainWindow.zoom), closestCenter);
			Set<Edge> selected = new HashSet<>();
			int brushRadius = (int) ((double) ((brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale)) / 2;
			for (Center center : overlapping)
			{
				for (Edge edge : center.borders)
				{
					if (edgeType == EdgeType.Delaunay)
					{
						if ((edge.d0 != null && edge.d0.loc.distanceTo(graphPoint) <= brushRadius)
								|| edge.d1 != null && edge.d1.loc.distanceTo(graphPoint) <= brushRadius)
						{
							selected.add(edge);
						}
					}
					else
					{
						if ((edge.v0 != null && edge.v0.loc.distanceTo(graphPoint) <= brushRadius)
								|| edge.v1 != null && edge.v1.loc.distanceTo(graphPoint) <= brushRadius)
						{
							selected.add(edge);
						}
					}
					
				}
			}
			return selected;
		}
	}

	private Edge getClosestEdge(nortantis.geom.Point point)
	{
		Center center = updater.mapParts.graph.findClosestCenter(point);
		Edge closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (Edge edge : center.borders)
		{
			nortantis.geom.Point centroid;
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
	 * Determines if a center is overlapping the given circle. Note that this isn't super precise because it doesn't account for the edge of
	 * the circle protruding into the center without overlapping any of the center's corners or centroid.
	 */
	private boolean isCenterOverlappingCircle(Center center, nortantis.geom.Point circleCenter, double radius)
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

	private boolean isPointWithinCircle(double x, double y, nortantis.geom.Point circleCenter, double radius)
	{
		double deltaX = x - circleCenter.x;
		double deltaY = y - circleCenter.y;
		return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY)) <= radius;
	}

	public abstract void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange,
			boolean changeEffectsBackgroundImages, boolean willDoImagesRefresh);

	public abstract void getSettingsFromGUI(MapSettings settings);

	/**
	 * If this tool enables or disables any components, it should be done in this method so that the framework can call it to re-disable
	 * components after enabling everything in the tools options panel when the editor is ready to use.
	 */
	public abstract void handleEnablingAndDisabling(MapSettings settings);

	public abstract void onBeforeLoadingNewMap();

	public void handleImagesRefresh(MapSettings settings)
	{
	}

	public void handleCustomImagesPathChanged(String customImagesPath)
	{

	}
	
	protected boolean isSelected()
	{
		return toggleButton.isSelected();
	}
}
