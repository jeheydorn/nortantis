package nortantis.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hoten.voronoi.Center;
import hoten.voronoi.Edge;
import nortantis.ImagePanel;
import nortantis.WorldGraph;

@SuppressWarnings("serial")
public class MapEditingPanel extends ImagePanel
{
	private final Color highlightColor = new Color(255,227,74);
	private final Color selectColor = Color.cyan;
	private List<Area> areas;
	private Set<Center> highlightedCenters;
	private Set<Center> selectedCenters;
	private WorldGraph graph;
	private HighlightMode highlightMode;
	private Collection<Edge> highlightedEdges;
	private Collection<Edge> processingEdges;
	private boolean showLakes;
	
	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		selectedCenters = new HashSet<>();
		highlightedEdges = new HashSet<>();
		processingEdges = new HashSet<>();
	}
	
	public void setHighlightedEdges(Collection<Edge> edges)
	{
		this.highlightedEdges = edges;
	}
	
	public void clearHighlightedEdges()
	{
		highlightedEdges.clear();
	}
	
	public void addAllProcessingEdges(Collection<Edge> edges)
	{
		this.processingEdges.addAll(edges);
	}
	
	public void clearProcessingEdges()
	{
		this.processingEdges.clear();
	}
	
	public void setAreasToDraw(List<Area> areas)
	{
		this.areas = areas;
	}
	
	public void addHighlightedCenter(Center c)
	{
		highlightedCenters.add(c);
	}
	
	public void addAllHighlightedCenters(Collection<Center> centers)
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
	
	public void addAllSelectedCenters(Collection<Center> centers)
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
	
	public void setShowLakes(boolean enabled)
	{
		this.showLakes = enabled;
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
			if (showLakes)
			{
				drawLakes(g);
			}
			
			g.setColor(highlightColor);
			drawCenterOutlines(g, highlightedCenters);
			drawEdges(g, highlightedEdges);
			
			g.setColor(selectColor);
			drawCenterOutlines(g, selectedCenters);
			drawEdges(g, processingEdges);
		}
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
		g.setColor(Color.BLUE);
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
}
