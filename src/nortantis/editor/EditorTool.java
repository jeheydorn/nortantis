package nortantis.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.imgscalr.Scalr.Method;

import hoten.voronoi.Center;
import hoten.voronoi.Corner;
import hoten.voronoi.Edge;
import nortantis.MapSettings;
import nortantis.TextDrawer;
import nortantis.util.ImageHelper;

public abstract class EditorTool
{
	protected final MapEditingPanel mapEditingPanel;
	BufferedImage placeHolder;
	private JPanel toolOptionsPanel;
	protected EditorFrame parent;
	public static int spaceBetweenRowsOfComponents = 8;
	private JToggleButton toggleButton;
	protected List<Integer> brushSizes = Arrays.asList(1, 25, 70);
	protected Undoer undoer;
	
	public EditorTool(EditorFrame parent)
	{
		this.parent = parent;
		placeHolder = createPlaceholderImage();
		this.mapEditingPanel = parent.mapEditingPanel;
		mapEditingPanel.setImage(placeHolder);
		toolOptionsPanel = createToolsOptionsPanel();
		this.undoer = parent.undoer;
	}

	private BufferedImage createPlaceholderImage()
	{
		String message = "Drawing the map. Some details like borders and grunge are not shown in edit mode.";
		final int scale = 2;
		Font font = MapSettings.parseFont("URW Chancery L\t0\t" + 30 * scale);
		Point textBounds = TextDrawer.getTextBounds(message, font);
		BufferedImage placeHolder = new BufferedImage((textBounds.x + 10) * scale, (textBounds.y + 20) * scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = placeHolder.createGraphics();
		g.setFont(font);
		g.setColor(new Color(168, 168, 168));
		g.drawString(message, 8, textBounds.y + 5);
		return ImageHelper.scaleByWidth(placeHolder, placeHolder.getWidth() / scale, Method.QUALITY);
	}
	
	public abstract String getToolbarName();
	
	public abstract String getImageIconFilePath();
	
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
	
	public hoten.geom.Point getPointOnGraph(java.awt.Point point)
	{
		return new hoten.geom.Point(point.x * (1.0 / parent.zoom), point.y * (1.0 / parent.zoom));
	}
	
	protected Set<Center> getSelectedCenters(java.awt.Point pointFromMouse, int brushDiameter)
	{
		Set<Center> selected = new HashSet<Center>();
		
		Center center = parent.mapParts.graph.findClosestCenter(getPointOnGraph(pointFromMouse));
		if (center != null)
		{
			selected.add(center);
		}
			
		if (brushDiameter <= 1)
		{
			return selected;
		}
		
		int brushRadius = (int)((double)(brushDiameter / parent.zoom)) / 2;
		
		// Add any polygons within the brush that were too small (< 1 pixel) to be picked up before.
		return parent.mapParts.graph.breadthFirstSearch((c) -> isCenterOverlappingCircle(c, getPointOnGraph(pointFromMouse), brushRadius), center);
	}
	
	
	protected Set<Edge> getSelectedEdges(java.awt.Point pointFromMouse, int brushDiameter)
	{
		if (brushDiameter <= 1)
		{
			return Collections.singleton(getClosestEdge(getPointOnGraph(pointFromMouse)));
		}
		else
		{
			hoten.geom.Point graphPoint = getPointOnGraph(pointFromMouse);
			Center closestCenter = parent.mapParts.graph.findClosestCenter(graphPoint);
			Set<Center> overlapping = parent.mapParts.graph.breadthFirstSearch(
					(c) -> isCenterOverlappingCircle(c, graphPoint, brushDiameter / parent.zoom), closestCenter);
			Set<Edge> selected = new HashSet<>();
			int brushRadius = (int)((double)(brushDiameter / parent.zoom)) / 2;
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
	
	private Edge getClosestEdge(hoten.geom.Point point)
	{
		Center center = parent.mapParts.graph.findClosestCenter(point);
		Edge closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (Edge edge : center.borders)
		{
			hoten.geom.Point centroid;
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
	private boolean isCenterOverlappingCircle(Center center, hoten.geom.Point circleCenter, double radius)
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
	
	private boolean isPointWithinCircle(double x, double y, hoten.geom.Point circleCenter, double radius)
	{
		double deltaX = x - circleCenter.x;
		double deltaY = y - circleCenter.y;
		return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY)) <= radius;
	}
}
