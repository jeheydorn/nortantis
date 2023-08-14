package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nortantis.MapCreator;
import nortantis.WorldGraph;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;

@SuppressWarnings("serial")
public class MapEditingPanel extends ImagePanel
{
	private final Color highlightColor = new Color(255,227,74);
	private final Color selectColor = Color.orange;
	private List<Area> areas;
	private Set<Center> highlightedCenters;
	private Set<Center> selectedCenters;
	private WorldGraph graph;
	private HighlightMode highlightMode;
	private Collection<Edge> highlightedEdges;
	private boolean highlightLakes;
	private boolean highlightRivers;
	public BufferedImage mapFromMapCreator;
	private java.awt.Point brushLocation;
	private int brushDiameter;
	private double zoom;
	
	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		selectedCenters = new HashSet<>();
		highlightedEdges = new HashSet<>();
		zoom = 1.0;

		// TODO Remove this line if it isn't necessary. If it is necessary, then maybe move it to ImagePanel.
		setLayout(new BorderLayout()); 
	}
	
	public void showBrush(java.awt.Point location, int brushDiameter)
	{
		brushLocation = location;
		this.brushDiameter = brushDiameter;
	}
	
	public void hideBrush()
	{
		brushLocation = null;
		brushDiameter = 0;
	}
	
	public void addHighlightedEdges(Collection<Edge> edges)
	{
		highlightedEdges.addAll(edges);
	}
	
	public void addHighlightedEdge(Edge edge)
	{
		highlightedEdges.add(edge);
	}
	
	public void clearHighlightedEdges()
	{
		highlightedEdges.clear();
	}
	
	public void setAreasToDraw(List<Area> areas)
	{
		this.areas = areas;
	}
	
	public void addHighlightedCenter(Center c)
	{
		highlightedCenters.add(c);
	}
	
	public void addHighlightedCenters(Collection<Center> centers)
	{
		highlightedCenters.addAll(centers);
	}
	
	public void clearHighlightedCenters()
	{
		if (highlightedCenters != null)
			highlightedCenters.clear();
	}
	
	public void addSelectedCenter(Center c)
	{
		selectedCenters.add(c);
	}
	
	public void addSelectedCenters(Collection<Center> centers)
	{
		selectedCenters.addAll(centers);
	}
		
	public void clearSelectedCenters()
	{
		if (selectedCenters != null)
		{
			selectedCenters.clear();
		}
	}
	
	public void setHighlightLakes(boolean enabled)
	{
		this.highlightLakes = enabled;
	}
	
	public void setHighlightRivers(boolean enabled)
	{
		this.highlightRivers = enabled;
	}
	
	public void setGraph(WorldGraph graph)
	{
		this.graph = graph;
	}

	public void clearAreasToDraw()
	{
		this.areas = null;
	}
	
	public void setCenterHighlightMode(HighlightMode mode)
	{
		this.highlightMode = mode;
	}
	
	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		if (brushLocation != null)
		{
			g.setColor(highlightColor);
			drawBrush(g);
		}
		
		// Handle zoom
		AffineTransform transform = new AffineTransform();
		transform.scale(zoom, zoom);
		((Graphics2D)g).transform(transform);
				
		// Handle drawing/highlighting
		if (areas != null)
		{
			g.setColor(highlightColor);
			for (Area a : areas)
			{
				((Graphics2D)g).draw(a);
			}
		}
		
		if (graph != null)
		{
			if (highlightLakes)
			{
				drawLakes(g);
			}
			
			if (highlightRivers)
			{
				drawRivers(g);
			}
			
			g.setColor(highlightColor);
			drawCenterOutlines(g, highlightedCenters);
			drawEdges(g, highlightedEdges);
			
			g.setColor(selectColor);
			drawCenterOutlines(g, selectedCenters);
		}
	}
	
	private void drawBrush(Graphics g)
	{
		g.drawOval(brushLocation.x - brushDiameter/2, brushLocation.y - brushDiameter/2, brushDiameter, brushDiameter);
	}
	
	private void drawCenterOutlines(Graphics g, Set<Center> centers)
	{
		for (Center c : centers)
		{
			for (Edge e : c.borders)
			{
				if (highlightMode == HighlightMode.outlineGroup)
				{
					if (e.d0 != null && e.d1 != null && centers.contains(e.d0) && centers.contains(e.d1))
					{
						// c is not on the edge of the group
						continue;
					}
				}
				graph.drawEdge(((Graphics2D)g), e);
			}
		}
	}
	
	private void drawEdges(Graphics g, Collection<Edge> edges)
	{
		for (Edge e : edges)
		{
			graph.drawEdge(((Graphics2D)g), e);
		}

	}
	
	private void drawLakes(Graphics g)
	{
		g.setColor(new Color(0, 130, 230));
		for (Center c : graph.centers)
		{
			if (c.isLake)
			{
				for (Edge e : c.borders)
				{
					if (e.d0 != null && e.d1 != null && e.d0.isLake && e.d1.isLake)
					{
						// c is not on the edge of the group
						continue;
					}
					graph.drawEdge(((Graphics2D)g), e);
				}
			}
		}
	}
	
	private void drawRivers(Graphics g)
	{
		g.setColor(new Color(0, 130, 230));
		graph.drawRivers((Graphics2D) g, MapCreator.calcSizeMultiplier(graph.getWidth()), null, null);
	}
	
	public void setZoom(double zoom)
	{
		this.zoom = zoom;
	}
}
