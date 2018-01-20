package nortantis.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.List;

import nortantis.ImagePanel;

@SuppressWarnings("serial")
public class MapEditingPanel extends ImagePanel
{
	private Color highlightColor = new Color(255,227,74);
	private List<Area> areas;
	
	public void setAreasToDraw(List<Area> areas)
	{
		this.areas = areas;
	}

	public void clearAreasToDraw()
	{
		this.areas = null;
	}

	public MapEditingPanel(BufferedImage image)
	{
		super(image);
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
