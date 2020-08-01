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
import nortantis.WorldGraph;
import nortantis.ImagePanel;

@SuppressWarnings("serial")
public class MapEditingPanel extends ImagePanel
{
	private Color highlightColor = new Color(255,227,74);
	private List<Area> areas;
	private Set<Center> highlightedCenters;
	private Set<Center> processingCenters;
	private WorldGraph graph;
	private HighlightMode highlightMode;
	private Collection<Edge> highlightedEdges;
	private Collection<Edge> processingEdges;
	
	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		processingCenters = new HashSet<>();
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
	
	public void addProcessingCenter(Center c)
	{
		processingCenters.add(c);
	}
	
	public void addAllProcessingCenters(Collection<Center> centers)
	{
		processingCenters.addAll(centers);
	}
		
	public void clearProcessingCenters()
	{
		if (processingCenters != null)
			processingCenters.clear();
	}
	
	public void setGraph(WorldGraph graph)
	{
		this.graph = graph;
	}

	public void clearAreasToDraw()
	{
		this.areas = null;
	}
	
	public void setHighlightColor(Color color)
	{
		this.highlightColor = color;
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
			g.setColor(highlightColor);
			drawCenterOutlines(g, highlightedCenters);
			drawEdges(g, highlightedEdges);
			
			g.setColor(Color.green);
			drawCenterOutlines(g, processingCenters);
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
}
