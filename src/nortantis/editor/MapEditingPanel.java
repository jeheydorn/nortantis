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
import nortantis.ImagePanel;

@SuppressWarnings("serial")
public class MapEditingPanel extends ImagePanel
{
	private Color highlightColor = new Color(255,227,74);
	private List<Area> areas;
	private Set<Center> centers;
	
	public void setAreasToDraw(List<Area> areas)
	{
		this.areas = areas;
	}
	
	// TODO remove center stuff
	public void addCenterToDraw(Center c)
	{
		centers.add(c);
	}
	
	public void removeCenterToDraw(Center c)
	{
		centers.remove(c);
	}

	public void clearAreasToDraw()
	{
		this.areas = null;
	}

	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		centers = new HashSet<>();
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
		
	}
}
