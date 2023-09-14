package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.imgscalr.Scalr.Method;

import nortantis.MapCreator;
import nortantis.MapText;
import nortantis.WorldGraph;
import nortantis.graph.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;

@SuppressWarnings("serial")
public class MapEditingPanel extends UnscaledImagePanel
{
	private final Color highlightColor = new Color(255, 227, 74);
	private final Color selectColor = Color.orange;
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
	private double resolution;
	private int borderWidth;
	private Point textBoxLocation;
	private Rectangle textBoxBoundsLine1;
	private double textBoxAngle;
	private BufferedImage rotateTextIconScaled;
	private Area rotateToolArea;
	private BufferedImage moveTextIconScaled;
	private Area moveToolArea;
	private Rectangle textBoxBoundsLine2;
	private List<Area> areasToDraw;

	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		selectedCenters = new HashSet<>();
		highlightedEdges = new HashSet<>();
		zoom = 1.0;
		resolution = 1.0;
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

	public void setTextBoxToDraw(nortantis.graph.geom.Point location, Rectangle line1Bounds, Rectangle line2Bounds, double angle)
	{
		this.textBoxLocation = location;
		this.textBoxBoundsLine1 = line1Bounds;
		this.textBoxBoundsLine2 = line2Bounds;
		this.textBoxAngle = angle;
	}

	public void setTextBoxToDraw(MapText text)
	{
		this.textBoxLocation = text.location;
		this.textBoxBoundsLine1 = text.line1Bounds;
		this.textBoxBoundsLine2 = text.line2Bounds;
		this.textBoxAngle = text.angle;
	}

	public void clearTextBox()
	{
		this.textBoxLocation = null;
		this.textBoxBoundsLine1 = null;
		this.textBoxBoundsLine2 = null;
		this.textBoxAngle = 0.0;
	}
	
	public void setAreasToDraw(List<Area> areas)
	{
		this.areasToDraw = areas;
	}
	
	public void clearAreasToDraw()
	{
		this.areasToDraw = null;
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

	public void setCenterHighlightMode(HighlightMode mode)
	{
		this.highlightMode = mode;
	}

	@Override
	protected void paintComponent(Graphics g)
	{	
		super.paintComponent(g);

		Graphics2D g2 = ((Graphics2D) g);
		if (brushLocation != null)
		{
			g.setColor(highlightColor);
			drawBrush(g2);
		}


		// Handle zoom and border width. This transform transforms from graph
		// space to image space.
		AffineTransform transform = new AffineTransform();
		transform.scale(zoom, zoom);
		transform.translate(borderWidth, borderWidth);
		((Graphics2D) g).transform(transform);

		// Handle drawing/highlighting
		
		if (textBoxBoundsLine1 != null)
		{
			drawTextBox(((Graphics2D) g));
		}
		
		drawAreas(g);

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

	private void drawTextBox(Graphics2D g)
	{
		Graphics2D g2 = ((Graphics2D) g);
		g.setColor(highlightColor);
		AffineTransform originalTransformCopy = g2.getTransform();

		double centerX = textBoxLocation.x * resolution;
		double centerY = textBoxLocation.y * resolution;

		// Rotate the area
		g2.rotate(textBoxAngle, centerX, centerY);

		int padding = (int) (9 * resolution);
		g2.drawRect(textBoxBoundsLine1.x, textBoxBoundsLine1.y, textBoxBoundsLine1.width, textBoxBoundsLine1.height);
		if (textBoxBoundsLine2 != null)
		{
			g2.drawRect(textBoxBoundsLine2.x, textBoxBoundsLine2.y, textBoxBoundsLine2.width, textBoxBoundsLine2.height);
		}

		// Place the image for the rotation tool.
		{
			int x;
			if (textBoxBoundsLine2 == null)
			{
				x = textBoxBoundsLine1.x + textBoxBoundsLine1.width + padding;
			}
			else
			{
				x = Math.max(textBoxBoundsLine1.x + textBoxBoundsLine1.width, textBoxBoundsLine2.x + textBoxBoundsLine2.width)+ padding;
			}
			int y;
			if (textBoxBoundsLine2 == null)
			{
				y = textBoxBoundsLine1.y + (textBoxBoundsLine1.height / 2) - (rotateTextIconScaled.getHeight() / 2); 
			}
			else
			{
				y = (int)(centerY - (rotateTextIconScaled.getHeight() / 2.0));
			}
			g2.drawImage(rotateTextIconScaled, x, y, null);
			rotateToolArea = new Area(new Ellipse2D.Double(x, y, rotateTextIconScaled.getWidth(), rotateTextIconScaled.getHeight()));
			rotateToolArea.transform(g2.getTransform());
		}

		// Place the image for the move tool.
		{
			int x = textBoxBoundsLine1.x + (int) (Math.round(textBoxBoundsLine1.width / 2.0))
					- (int) (Math.round(moveTextIconScaled.getWidth() / 2.0));
			int y = textBoxBoundsLine1.y - (moveTextIconScaled.getHeight()) - padding;
			g2.drawImage(moveTextIconScaled, x, y, null);
			moveToolArea = new Area(new Ellipse2D.Double(x, y, moveTextIconScaled.getWidth(), moveTextIconScaled.getHeight()));
			moveToolArea.transform(g2.getTransform());
		}

		g2.setTransform(originalTransformCopy);
	}

	public boolean isInTextRotateTool(java.awt.Point point)
	{
		if (rotateToolArea == null)
		{
			return false;
		}

		java.awt.Point tPoint = new java.awt.Point();
		transformWithOsScaling.transform(point, tPoint);
		return rotateToolArea.contains(tPoint);
	}

	public boolean isInTextMoveTool(java.awt.Point point)
	{
		if (moveToolArea == null)
		{
			return false;
		}

		java.awt.Point tPoint = new java.awt.Point();
		transformWithOsScaling.transform(point, tPoint);
		return moveToolArea.contains(tPoint);
	}

	private void drawBrush(Graphics2D g)
	{
		AffineTransform t = g.getTransform();
		g.setTransform(transformWithOsScaling);
		g.drawOval(brushLocation.x - brushDiameter / 2, brushLocation.y - brushDiameter / 2, brushDiameter, brushDiameter);
		g.setTransform(t);
	}
	
	private void drawAreas(Graphics g)
	{
		if (areasToDraw != null)
		{
			g.setColor(highlightColor);
			for (Area area : areasToDraw)
			{
				((Graphics2D)g).draw(area);
			}
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
				graph.drawEdge(((Graphics2D) g), e);
			}
		}
	}

	private void drawEdges(Graphics g, Collection<Edge> edges)
	{
		for (Edge e : edges)
		{
			graph.drawEdge(((Graphics2D) g), e);
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
					graph.drawEdge(((Graphics2D) g), e);
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

	public void setResolution(double resolution)
	{
		this.resolution = resolution;
		
		// Determines the size at which the rotation and move tool icons appear.
		final double iconScale = 0.2;
		
		BufferedImage rotateIcon = ImageHelper.read(Paths.get(AssetsPath.getInstallPath(), "internal", "rotate text.png").toString());
		rotateTextIconScaled = ImageHelper.scaleByWidth(rotateIcon, (int) (rotateIcon.getWidth() * resolution * iconScale),
				Method.ULTRA_QUALITY);
		BufferedImage moveIcon = ImageHelper.read(Paths.get(AssetsPath.getInstallPath(), "internal", "move text.png").toString());
		moveTextIconScaled = ImageHelper.scaleByWidth(moveIcon, (int) (moveIcon.getWidth() * resolution * iconScale), Method.ULTRA_QUALITY);
	}

	public void setBorderWidth(int borderWidth)
	{
		this.borderWidth = borderWidth;
	}

	public void clearAllSelectionsAndHighlights()
	{
		clearTextBox();
		clearSelectedCenters();
		clearHighlightedCenters();
		clearHighlightedEdges();
		hideBrush();
		clearAreasToDraw();
	}
}
