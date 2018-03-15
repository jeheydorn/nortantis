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
import nortantis.GraphImpl;
import nortantis.ImagePanel;

@SuppressWarnings("serial")
public class MapEditingPanel extends ImagePanel
{
	private Color highlightColor = new Color(255,227,74);
	private List<Area> areas;
	private Set<Center> highlightedCenters;
	private Set<Center> processingCenters;
	private GraphImpl graph;
	private HighlightMode highlightMode;
	
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
	
	public void setGraph(GraphImpl graph)
	{
		this.graph = graph;
	}

	public void clearAreasToDraw()
	{
		this.areas = null;
	}

	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		processingCenters = new HashSet<>();
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
			
			g.setColor(Color.green);
			drawCenterOutlines(g, processingCenters);
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
}
