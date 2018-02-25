package nortantis.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
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
	
	public void setAreasToDraw(List<Area> areas)
	{
		this.areas = areas;
	}
	
	public void addHighlightedCenter(Center c)
	{
		highlightedCenters.add(c);
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
			for (Center c : highlightedCenters)
			{
				for (Edge e : c.borders)
				{
					graph.drawEdge(((Graphics2D)g), e);
				}
			}
			
			g.setColor(Color.green);
			for (Center c : processingCenters)
			{
				for (Edge e : c.borders)
				{
					graph.drawEdge(((Graphics2D)g), e);
				}
			}
		}
		
	}
}
