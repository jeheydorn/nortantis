package nortantis.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.imgscalr.Scalr.Method;

import nortantis.FreeIconCollection;
import nortantis.IconDrawTask;
import nortantis.IconDrawer;
import nortantis.MapText;
import nortantis.WorldGraph;
import nortantis.editor.EdgeType;
import nortantis.editor.FreeIcon;
import nortantis.geom.Point;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.util.ImageHelper;
import nortantis.util.ImageHelper.ColorifyAlgorithm;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class MapEditingPanel extends UnscaledImagePanel
{
	private final Color highlightEditColor = new Color(255, 227, 74);
	private final Color waterHighlightColor = new Color(0, 193, 245);
	private final Color artPackHighlightColor = Color.CYAN;
	private final Color processingColor = Color.orange;
	private final Color selectColor = Color.orange;
	private Set<Center> highlightedCenters;
	private Set<Center> selectedCenters;
	private WorldGraph graph;
	private HighlightMode highlightMode;
	private Collection<Edge> highlightedEdges;
	private List<List<Point>> polylinesToHighlight;
	private EdgeType edgeTypeToHighlight;
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
	private nortantis.geom.Rectangle iconToEditBounds;
	private boolean isIconToEditInAValidPosition;
	private BufferedImage rotateIconScaled;
	private Area rotateToolArea;
	private Area scaleToolArea;
	private BufferedImage moveIconScaled;
	private BufferedImage scaleIconScaled;
	private BufferedImage redMoveIconScaled;
	private BufferedImage redScaleIconScaled;
	private Area moveToolArea;
	private Rectangle textBoxBoundsLine2;
	private Set<Area> highlightedAreas;
	private Set<RotatedRectangle> processingAreas;
	private Set<String> artPacksToHighlight;
	private FreeIconCollection freeIcons;
	private IconDrawer iconDrawer;
	IconEditToolsMode imageEditMode;
	private BufferedImage moveIconScaledLarge;
	private BufferedImage scaleIconScaledLarge;
	private boolean useLargeIconEditIcons;

	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		selectedCenters = new HashSet<>();
		highlightedEdges = new HashSet<>();
		highlightedAreas = new HashSet<>();
		processingAreas = new HashSet<>();
		artPacksToHighlight = new TreeSet<>();
		zoom = 1.0;
		resolution = 0.0;
		polylinesToHighlight = new ArrayList<>();
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

	public void addHighlightedEdges(Collection<Edge> edges, EdgeType edgeType)
	{
		highlightedEdges.addAll(edges);
		edgeTypeToHighlight = edgeType;
	}

	public void addHighlightedEdge(Edge edge, EdgeType edgeType)
	{
		highlightedEdges.add(edge);
		edgeTypeToHighlight = edgeType;
	}
	
	public void clearHighlightedEdges()
	{
		highlightedEdges.clear();
	}
	
	public void addPolylinesToHighlight(List<Point> lines)
	{
		polylinesToHighlight.add(lines);
	}

	public void clearHighlightedPolylines()
	{
		polylinesToHighlight.clear();
	}

	public void setTextBoxToDraw(nortantis.geom.Point location, nortantis.geom.Rectangle line1Bounds, nortantis.geom.Rectangle line2Bounds,
			double angle)
	{
		this.textBoxLocation = location == null ? null : new nortantis.geom.Point(location);
		this.textBoxBoundsLine1 = line1Bounds == null ? null : AwtFactory.toAwtRectangle(line1Bounds.scaleAboutOrigin(resolution));
		this.textBoxBoundsLine2 = line2Bounds == null ? null : AwtFactory.toAwtRectangle(line2Bounds.scaleAboutOrigin(resolution));
		this.textBoxAngle = angle;
	}

	public void setTextBoxToDraw(MapText text)
	{
		this.textBoxLocation = text.location == null ? null : new nortantis.geom.Point(text.location);
		this.textBoxBoundsLine1 = text.line1Bounds == null ? null : AwtFactory.toAwtRectangle(text.line1Bounds.scaleAboutOrigin(resolution));
		this.textBoxBoundsLine2 = text.line2Bounds == null ? null : AwtFactory.toAwtRectangle(text.line2Bounds.scaleAboutOrigin(resolution));
		this.textBoxAngle = text.angle;
	}

	public void clearTextBox()
	{
		this.textBoxLocation = null;
		this.textBoxBoundsLine1 = null;
		this.textBoxBoundsLine2 = null;
		this.textBoxAngle = 0.0;
	}

	public void showIconEditToolsAt(FreeIcon icon, IconEditToolsMode toolsLocation, boolean useLargeIcons)
	{
		assert iconDrawer != null;
		if (iconDrawer == null)
		{
			return;
		}

		showIconEditToolsAt(iconDrawer.toIconDrawTask(icon).createBounds(), true, toolsLocation, useLargeIcons);
	}

	public void showIconEditToolsAt(nortantis.geom.Rectangle rectangle, boolean isValidPosition, IconEditToolsMode imageEditMode,
			boolean useLargeIcons)
	{
		iconToEditBounds = rectangle == null ? null : rectangle.scaleAboutOrigin(1.0 / resolution);
		this.isIconToEditInAValidPosition = isValidPosition;
		this.imageEditMode = imageEditMode;
		this.useLargeIconEditIcons = useLargeIcons;
	}

	public enum IconEditToolsMode
	{
		Icon, Overlay
	}

	public void clearIconEditTools()
	{
		iconToEditBounds = null;
		scaleToolArea = null;
		moveToolArea = null;
		imageEditMode = null;
	}

	public void setHighlightedAreasFromTexts(List<MapText> texts, boolean isErasing)
	{
		highlightedAreas.clear();
		for (MapText text : texts)
		{
			if (text.line1Area != null)
			{
				highlightedAreas.add(AwtFactory.toAwtArea(text.line1Area));
			}

			if (text.line2Area != null)
			{
				highlightedAreas.add(AwtFactory.toAwtArea(text.line2Area));
			}
		}
	}

	public void setHighlightedAreasFromIcons(List<FreeIcon> icons, boolean isErasing)
	{
		assert iconDrawer != null;
		if (iconDrawer == null)
		{
			return;
		}

		highlightedAreas.clear();
		for (FreeIcon icon : icons)
		{
			IconDrawTask task = iconDrawer.toIconDrawTask(icon);
			if (task != null)
			{
				nortantis.geom.Rectangle bounds = task.createBounds();
				highlightedAreas.add(AwtFactory.toAwtArea(bounds));
			}
		}
	}

	public void clearHighlightedAreas()
	{
		highlightedAreas.clear();
	}

	public void addProcessingAreasFromTexts(List<MapText> texts)
	{
		for (MapText text : texts)
		{
			if (text.line1Area != null)
			{
				processingAreas.add(text.line1Area);
			}

			if (text.line2Area != null)
			{
				processingAreas.add(text.line2Area);
			}
		}
	}

	public void addProcessingAreas(Set<RotatedRectangle> areas)
	{
		processingAreas.addAll(areas);
	}

	public void removeProcessingAreas(Set<RotatedRectangle> areasToRemove)
	{
		for (RotatedRectangle area : areasToRemove)
		{
			processingAreas.remove(area);
		}
	}

	public void clearProcessingAreas()
	{
		processingAreas.clear();
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

	public void setArtPacksToHighlight(Set<String> artPacksToHighlight)
	{
		this.artPacksToHighlight = artPacksToHighlight == null ? new TreeSet<>() : artPacksToHighlight;
	}

	public void setFreeIcons(FreeIconCollection freeIcons)
	{
		this.freeIcons = freeIcons;
	}

	public void setIconDrawer(IconDrawer iconDrawer)
	{
		this.iconDrawer = iconDrawer;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D g2 = ((Graphics2D) g);

		if (brushLocation != null)
		{
			g.setColor(getHighlightColor());
			drawBrush(g2);
		}

		// Handle zoom and border width. This transform transforms from graph
		// space to image space.
		AffineTransform transform = new AffineTransform();
		transform.scale(zoom, zoom);
		transform.translate(borderWidth, borderWidth);
		((Graphics2D) g).transform(transform);

		// Handle drawing/highlighting

		highlightArtPacksIfNeeded(g2);

		if (textBoxBoundsLine1 != null)
		{
			drawTextBox(((Graphics2D) g));
		}

		if (iconToEditBounds != null)
		{
			if (imageEditMode == IconEditToolsMode.Overlay)
			{
				((Graphics2D) g).translate(-borderWidth, -borderWidth);
			}

			drawIconEditBox(((Graphics2D) g));

			if (imageEditMode == IconEditToolsMode.Overlay)
			{
				((Graphics2D) g).translate(borderWidth, borderWidth);
			}
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

			g.setColor(highlightEditColor);
			drawCenterOutlines(g, highlightedCenters);
			g.setColor(getHighlightColor());
			drawEdges(g, highlightedEdges);
			drawPolylines(g);

			g.setColor(selectColor);
			drawCenterOutlines(g, selectedCenters);
		}
	}

	private void highlightArtPacksIfNeeded(Graphics2D g)
	{
		if (freeIcons != null && artPacksToHighlight != null && !artPacksToHighlight.isEmpty())
		{
			g.setColor(artPackHighlightColor);
			for (FreeIcon icon : freeIcons)
			{
				if (artPacksToHighlight.contains(icon.artPack))
				{
					IconDrawTask task = iconDrawer.toIconDrawTask(icon);
					if (task != null)
					{
						nortantis.geom.Rectangle bounds = task.createBounds();
						((Graphics2D) g).draw(AwtFactory.toAwtArea(bounds));
					}
				}
			}
		}
	}

	private void drawIconEditBox(Graphics2D g)
	{
		if (isIconToEditInAValidPosition)
		{
			g.setColor(highlightEditColor);
		}
		else
		{
			g.setColor(getInvalidPositionColor());
		}
		Rectangle editBounds = AwtFactory.toAwtRectangle(iconToEditBounds.scaleAboutOrigin(resolution));

		int padding = (int) (9 * resolution);
		g.drawRect(editBounds.x, editBounds.y, editBounds.width, editBounds.height);

		if (!isIconToEditInAValidPosition)
		{
			final int inset = (int) (10 * resolution);
			if (inset > 0 && editBounds.width > inset * 2 && editBounds.height > inset * 2)
			{
				Stroke prevStroke = g.getStroke();
				RenderingHints hints = g.getRenderingHints();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setStroke(new BasicStroke((float) Math.max(2.0 * resolution, 1.0)));

				if (editBounds.width > editBounds.height)
				{
					int additionalWidthInset = (editBounds.width - editBounds.height) / 2;
					g.drawLine(editBounds.x + inset + additionalWidthInset, editBounds.y + inset,
							editBounds.x + editBounds.width - (inset + additionalWidthInset), editBounds.y + editBounds.height - inset);
					g.drawLine(editBounds.x + inset + additionalWidthInset, editBounds.y + editBounds.height - inset,
							editBounds.x + editBounds.width - (inset + additionalWidthInset), editBounds.y + inset);
				}
				else
				{
					int additionalHeightInset = (editBounds.height - editBounds.width) / 2;
					g.drawLine(editBounds.x + inset, editBounds.y + inset + additionalHeightInset, editBounds.x + editBounds.width - inset,
							editBounds.y + editBounds.height - (inset + additionalHeightInset));
					g.drawLine(editBounds.x + inset, editBounds.y + editBounds.height - (inset + additionalHeightInset),
							editBounds.x + editBounds.width - inset, editBounds.y + inset + additionalHeightInset);
				}

				g.setStroke(prevStroke);
				g.setRenderingHints(hints);
			}
		}

		// Place the image for the scale tool.
		{
			BufferedImage toolIcon;
			if (useLargeIconEditIcons)
			{
				toolIcon = scaleIconScaledLarge;
			}
			else
			{
				toolIcon = isIconToEditInAValidPosition ? scaleIconScaled : redScaleIconScaled;
			}

			int x, y;
			if (imageEditMode == IconEditToolsMode.Icon)
			{
				// Draw edit tools outside box
				x = editBounds.x + editBounds.width + padding;
				y = editBounds.y - (toolIcon.getHeight()) - padding;
			}
			else
			{
				// Draw edit tools inside box because overlays often reach to top of the map, and you wouldn't be able to get to the tools
				// if they were drawn outside the map.
				x = editBounds.x + editBounds.width - toolIcon.getWidth() - padding;
				y = editBounds.y + padding;
			}

			g.drawImage(toolIcon, x, y, null);
			scaleToolArea = new Area(new Ellipse2D.Double(x, y, toolIcon.getWidth(), toolIcon.getHeight()));
			scaleToolArea.transform(g.getTransform());
		}

		// Place the image for the move tool.
		{
			BufferedImage toolIcon;
			if (useLargeIconEditIcons)
			{
				toolIcon = moveIconScaledLarge;
			}
			else
			{
				toolIcon = isIconToEditInAValidPosition ? moveIconScaled : redMoveIconScaled;
			}

			int x = editBounds.x + (int) (Math.round(editBounds.width / 2.0)) - (int) (Math.round(toolIcon.getWidth() / 2.0));
			int y;
			if (imageEditMode == IconEditToolsMode.Icon)
			{
				y = editBounds.y - (toolIcon.getHeight()) - padding;
			}
			else
			{
				y = editBounds.y + padding;
			}
			g.drawImage(toolIcon, x, y, null);
			moveToolArea = new Area(new Ellipse2D.Double(x, y, toolIcon.getWidth(), toolIcon.getHeight()));
			moveToolArea.transform(g.getTransform());
		}
	}

	private void drawTextBox(Graphics2D g2)
	{
		g2.setColor(highlightEditColor);
		AffineTransform originalTransformCopy = g2.getTransform();

		double centerX = textBoxLocation.x * resolution;
		double centerY = textBoxLocation.y * resolution;

		// Rotate the area
		g2.rotate(textBoxAngle, centerX, centerY);

		int padding = (int) (9 * resolution);
		g2.drawRect((int) (textBoxBoundsLine1.x + centerX), (int) (textBoxBoundsLine1.y + centerY), textBoxBoundsLine1.width,
				textBoxBoundsLine1.height);
		if (textBoxBoundsLine2 != null)
		{
			g2.drawRect((int) (textBoxBoundsLine2.x + centerX), (int) (textBoxBoundsLine2.y + centerY), textBoxBoundsLine2.width,
					textBoxBoundsLine2.height);
		}

		// Place the image for the rotation tool.
		{
			int x;
			if (textBoxBoundsLine2 == null)
			{
				x = (int) (textBoxBoundsLine1.x + centerX) + textBoxBoundsLine1.width + padding;
			}
			else
			{
				x = Math.max((int) (textBoxBoundsLine1.x + centerX) + textBoxBoundsLine1.width,
						(int) (textBoxBoundsLine2.x + centerX) + textBoxBoundsLine2.width) + padding;
			}
			int y;
			if (textBoxBoundsLine2 == null)
			{
				y = (int) (textBoxBoundsLine1.y + centerY) + (textBoxBoundsLine1.height / 2) - (rotateIconScaled.getHeight() / 2);
			}
			else
			{
				y = (int) (centerY - (rotateIconScaled.getHeight() / 2.0));
			}
			g2.drawImage(rotateIconScaled, x, y, null);
			rotateToolArea = new Area(new Ellipse2D.Double(x, y, rotateIconScaled.getWidth(), rotateIconScaled.getHeight()));
			rotateToolArea.transform(g2.getTransform());
		}

		// Place the image for the move tool.
		{
			int x = (int) (textBoxBoundsLine1.x + centerX) + (int) (Math.round(textBoxBoundsLine1.width / 2.0))
					- (int) (Math.round(moveIconScaled.getWidth() / 2.0));
			int y = (int) (textBoxBoundsLine1.y + centerY) - (moveIconScaled.getHeight()) - padding;
			g2.drawImage(moveIconScaled, x, y, null);
			moveToolArea = new Area(new Ellipse2D.Double(x, y, moveIconScaled.getWidth(), moveIconScaled.getHeight()));
			moveToolArea.transform(g2.getTransform());
		}

		g2.setTransform(originalTransformCopy);
	}

	public boolean isInRotateTool(java.awt.Point point)
	{
		if (rotateToolArea == null)
		{
			return false;
		}

		java.awt.Point tPoint = new java.awt.Point();
		transformWithOsScaling.transform(point, tPoint);
		return rotateToolArea.contains(tPoint);
	}

	public boolean isInMoveTool(java.awt.Point point)
	{
		if (moveToolArea == null)
		{
			return false;
		}

		java.awt.Point tPoint = new java.awt.Point();
		transformWithOsScaling.transform(point, tPoint);
		return moveToolArea.contains(tPoint);
	}

	public boolean isInScaleTool(java.awt.Point point)
	{
		if (scaleToolArea == null)
		{
			return false;
		}

		java.awt.Point tPoint = new java.awt.Point();
		transformWithOsScaling.transform(point, tPoint);
		return scaleToolArea.contains(tPoint);
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
		if (!highlightedAreas.isEmpty())
		{
			g.setColor(getHighlightColor());
			for (Area area : highlightedAreas)
			{
				((Graphics2D) g).draw(area);
			}
		}

		if (!processingAreas.isEmpty())
		{
			g.setColor(processingColor);
			for (RotatedRectangle rect : processingAreas)
			{
				Area area = AwtFactory.toAwtArea(rect);
				((Graphics2D) g).draw(area);
			}
		}

	}

	private Color getHighlightColor()
	{
		return highlightEditColor;
	}

	private Color getInvalidPositionColor()
	{
		return Color.red;
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
				graph.drawEdge(AwtFactory.wrap((Graphics2D) g), e);
			}
		}
	}

	private void drawEdges(Graphics g, Collection<Edge> edges)
	{
		for (Edge e : edges)
		{
			if (edgeTypeToHighlight == EdgeType.Delaunay)
			{
				graph.drawEdgeDeluanay(AwtFactory.wrap((Graphics2D) g), e);
			}
			else
			{
				graph.drawEdge(AwtFactory.wrap((Graphics2D) g), e);
			}
		}

	}
	
	private void drawPolylines(Graphics g)
	{
		for (List<Point> line : polylinesToHighlight)
		{
			drawPolyline(g, line);
		}
	}
	
	private void drawPolyline(Graphics g, List<Point> points)
	{
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			xPoints[i] = (int) points.get(i).x;
			yPoints[i] = (int) points.get(i).y;
		}
		g.drawPolyline(xPoints, yPoints, xPoints.length);
	}

	private void drawLakes(Graphics g)
	{
		g.setColor(waterHighlightColor);
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
					graph.drawEdge(AwtFactory.wrap((Graphics2D) g), e);
				}
			}
		}
	}

	private void drawRivers(Graphics g)
	{
		graph.drawRivers(AwtFactory.wrap((Graphics2D) g), null, null, AwtFactory.wrap(waterHighlightColor), false, null);
	}

	public void setZoom(double zoom)
	{
		this.zoom = zoom;
	}

	public void setResolution(double resolution)
	{
		if (this.resolution == 0.0 || this.resolution != resolution)
		{
			this.resolution = resolution;

			// Determines the size at which the rotation and move tool icons appear.
			final double smallIconScale = 0.2;
			final double largeIconScale = 0.6;

			BufferedImage rotateIcon = AwtFactory
					.unwrap(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "rotate text.png").toString()));
			rotateIconScaled = AwtFactory.unwrap(ImageHelper.scaleByWidth(AwtFactory.wrap(rotateIcon),
					(int) (rotateIcon.getWidth() * resolution * smallIconScale), Method.ULTRA_QUALITY));

			{
				Image moveIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "move text.png").toString());
				Image moveIconScaledWrapped = ImageHelper.scaleByWidth(moveIcon, (int) (moveIcon.getWidth() * resolution * smallIconScale),
						Method.ULTRA_QUALITY);
				moveIconScaled = AwtFactory.unwrap(moveIconScaledWrapped);
				redMoveIconScaled = AwtFactory
						.unwrap(ImageHelper.copyAlphaTo(ImageHelper.colorify(ImageHelper.convertToGrayscale(moveIconScaledWrapped),
								AwtFactory.wrap(getInvalidPositionColor()), ColorifyAlgorithm.algorithm2), moveIconScaledWrapped));
			}

			{
				BufferedImage scaleIcon = AwtFactory
						.unwrap(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "scale.png").toString()));
				Image scaleIconScaledWrapped = ImageHelper.scaleByWidth(AwtFactory.wrap(scaleIcon),
						(int) (scaleIcon.getWidth() * resolution * smallIconScale), Method.ULTRA_QUALITY);
				scaleIconScaled = AwtFactory.unwrap(scaleIconScaledWrapped);
				redScaleIconScaled = AwtFactory
						.unwrap(ImageHelper.copyAlphaTo(ImageHelper.colorify(ImageHelper.convertToGrayscale(scaleIconScaledWrapped),
								AwtFactory.wrap(getInvalidPositionColor()), ColorifyAlgorithm.algorithm2), scaleIconScaledWrapped));
			}

			{
				Image moveIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "move text.png").toString());
				Image moveIconScaledWrapped = ImageHelper.scaleByWidth(moveIcon, (int) (moveIcon.getWidth() * resolution * largeIconScale),
						Method.ULTRA_QUALITY);
				moveIconScaledLarge = AwtFactory.unwrap(moveIconScaledWrapped);
			}

			{
				BufferedImage scaleIcon = AwtFactory
						.unwrap(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "scale.png").toString()));
				Image scaleIconScaledWrapped = ImageHelper.scaleByWidth(AwtFactory.wrap(scaleIcon),
						(int) (scaleIcon.getWidth() * resolution * largeIconScale), Method.ULTRA_QUALITY);
				scaleIconScaledLarge = AwtFactory.unwrap(scaleIconScaledWrapped);
			}
		}
	}

	public void setBorderWidth(int borderWidth)
	{
		this.borderWidth = borderWidth;
	}

	public int getBorderWidth()
	{
		return borderWidth;
	}

	public void clearAllSelectionsAndHighlights()
	{
		clearAllToolSpecificSelectionsAndHighlights();
		setHighlightRivers(false);
		setHighlightLakes(false);
		freeIcons = null;
		repaint();
	}

	public void clearAllToolSpecificSelectionsAndHighlights()
	{
		clearTextBox();
		clearIconEditTools();
		clearSelectedCenters();
		clearHighlightedCenters();
		clearHighlightedEdges();
		clearHighlightedPolylines();
		hideBrush();
		clearHighlightedAreas();
		clearProcessingAreas();
		repaint();
	}

}
