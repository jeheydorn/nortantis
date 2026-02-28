package nortantis.swing;

import nortantis.*;
import nortantis.editor.EdgeType;
import nortantis.editor.FreeIcon;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.platform.ImageHelper;
import nortantis.platform.ImageHelper.ColorizeAlgorithm;
import nortantis.util.Range;
import org.imgscalr.Scalr.Method;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

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
	public Image mapFromMapCreator;
	private java.awt.Point brushLocation;
	private int brushDiameter;
	private double zoom;
	private double resolution;
	private int borderPadding;
	private nortantis.geom.Rectangle iconToEditBounds;
	private boolean isIconToEditInAValidPosition;
	private BufferedImage rotateIconScaled;
	private Area rotateToolArea;
	private Area scaleToolArea;
	private BufferedImage moveIconScaledSmall;
	private BufferedImage scaleIconScaledSmall;
	private BufferedImage redMoveIconScaledSmall;
	private BufferedImage redScaleIconScaledSmall;
	private Area moveToolArea;
	private Set<Area> highlightedAreas;
	private Set<Area> redHighlightedAreas;
	private Set<RotatedRectangle> processingAreas;
	private Set<String> artPacksToHighlight;
	private FreeIconCollection freeIcons;
	private IconDrawer iconDrawer;
	IconEditToolsLocation toolsLocation;
	private BufferedImage moveIconScaledMedium;
	private BufferedImage scaleIconScaledMedium;
	private BufferedImage redMoveIconScaledMedium;
	private BufferedImage redScaleIconScaledMedium;
	private BufferedImage moveIconScaledLarge;
	private BufferedImage scaleIconScaledLarge;
	private IconEditToolsSize editToolsSize;
	private RotatedRectangle textBoxBounds;
	private boolean showEditBox;
	private boolean editBoxIsInMapSpace;
	private final double smallIconScale = 0.2;
	private final double mediumIconScale = 0.4;
	private final double largeIconScale = 0.6;
	private Runnable selectionBoxChangeListener;
	/**
	 * The rectangular selection box if currently shown, resolution invariant.
	 */
	private nortantis.geom.Rectangle selectionBoxRI;
	// Selection box drag state
	private BoxSelectHandle selectionBoxDraggedHandle;
	private nortantis.geom.Point selectionBoxDragStartRI;
	private nortantis.geom.Point selectionBoxDragOffset;
	private nortantis.geom.Rectangle selectionBoxRIAtDragStart;
	/**
	 * Optional bounding rectangle (RI coords) that constrains the selection box. Null means no constraint.
	 */
	private nortantis.geom.Rectangle selectionBoxConstraintsRI;
	/**
	 * Locked aspect ratio for the selection box (width / height). 0 means no lock.
	 */
	private double selectionBoxLockedAspectRatio;

	public MapEditingPanel(BufferedImage image)
	{
		super(image);
		highlightedCenters = new HashSet<>();
		selectedCenters = new HashSet<>();
		highlightedEdges = new HashSet<>();
		highlightedAreas = new HashSet<>();
		redHighlightedAreas = new HashSet<>();
		processingAreas = new HashSet<>();
		artPacksToHighlight = new TreeSet<>();
		zoom = 1.0;
		resolution = 0.0;
		polylinesToHighlight = new ArrayList<>();

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onSelectionBoxMousePressed(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				onSelectionBoxMouseReleased(e);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				onSelectionBoxMouseClicked(e);
			}
		});
		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				if (selectionBoxRI != null)
				{
					repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				onSelectionBoxMouseDragged(e);
			}
		});
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

	public void setTextBoxToDraw(nortantis.geom.RotatedRectangle line1Bounds, nortantis.geom.RotatedRectangle line2Bounds)
	{
		this.textBoxBounds = line1Bounds.addRotatedRectangleThatHasTheSameAngleAndPivot(line2Bounds);
	}

	public void setTextBoxToDraw(MapText text)
	{
		if (text.line1Bounds != null)
		{
			this.textBoxBounds = text.line1Bounds.addRotatedRectangleThatHasTheSameAngleAndPivot(text.line2Bounds);
		}
	}

	public void clearTextBox()
	{
		this.textBoxBounds = null;
	}

	public void showIconEditToolsAt(Collection<FreeIcon> icons, boolean isValidPosition)
	{
		assert iconDrawer != null;
		if (iconDrawer == null)
		{
			return;
		}

		if (icons.size() == 0)
		{
			hideIconEditTools();
			return;
		}

		if (icons.size() == 1)
		{
			showIconEditToolsAt(icons.iterator().next(), isValidPosition, IconEditToolsLocation.OutsideBox, IconEditToolsSize.Small, true);
			return;
		}

		// Multiple icons. Show the edit box around them.

		nortantis.geom.Rectangle bounds = getIconEditBounds(icons);

		assert bounds != null;
		if (bounds == null)
		{
			return;
		}

		IconEditToolsLocation toolsLocation = IconEditToolsLocation.OutsideBox;
		showIconEditToolsAt(bounds, isValidPosition, toolsLocation, IconEditToolsSize.Medium, true, true);
	}

	public nortantis.geom.Rectangle getIconEditBounds(Collection<FreeIcon> icons)
	{
		nortantis.geom.Rectangle bounds = null;
		nortantis.geom.Dimension averageSize = null;
		int n = 0;
		for (FreeIcon icon : icons)
		{
			nortantis.geom.Rectangle iconBounds = iconDrawer.toIconDrawTask(icon).getOrCreateContentBoundsPadded();
			if (bounds == null)
			{
				bounds = iconBounds;
			}
			else
			{
				bounds = bounds.add(iconBounds);
			}

			n++;
			if (averageSize == null)
			{
				averageSize = iconBounds.size();
			}
			else
			{
				averageSize = new nortantis.geom.Dimension((iconBounds.size().width + averageSize.width * (n - 1)) / n, (iconBounds.size().height + averageSize.height * (n - 1)) / n);
			}
		}
		return bounds;
	}

	private void showIconEditToolsAt(FreeIcon icon, boolean isValidPosition, IconEditToolsLocation toolsLocation, IconEditToolsSize editToolsSize, boolean showEditBox)
	{
		assert iconDrawer != null;
		if (iconDrawer == null)
		{
			return;
		}

		showIconEditToolsAt(iconDrawer.toIconDrawTask(icon).getOrCreateContentBoundsPadded(), isValidPosition, toolsLocation, editToolsSize, showEditBox, true);
	}

	public void showIconEditToolsAt(nortantis.geom.Rectangle rectangle, boolean isValidPosition, IconEditToolsLocation toolsLocation, IconEditToolsSize editToolsSize, boolean showEditBox,
			boolean editBoxIsInMapSpace)
	{
		iconToEditBounds = rectangle == null ? null : rectangle.scaleAboutOrigin(1.0 / resolution);
		this.isIconToEditInAValidPosition = isValidPosition;
		this.toolsLocation = toolsLocation;
		this.editToolsSize = editToolsSize;
		this.showEditBox = showEditBox;
		this.editBoxIsInMapSpace = editBoxIsInMapSpace;
	}

	public enum IconEditToolsLocation
	{
		OutsideBox, InsideBox
	}

	public enum IconEditToolsSize
	{
		Small, Medium, Large
	}

	public void hideIconEditTools()
	{
		iconToEditBounds = null;
		scaleToolArea = null;
		moveToolArea = null;
		toolsLocation = null;
		editToolsSize = IconEditToolsSize.Small;
		showEditBox = false;
		editBoxIsInMapSpace = false;
	}

	public final double calcMinWidthForIconEditToolsOnInside(IconEditToolsSize size)
	{
		final double toolImageSize = 256;
		if (size == IconEditToolsSize.Large)
		{
			return (toolImageSize * largeIconScale * 2 + 69) * resolution;
		}
		else if (size == IconEditToolsSize.Medium)
		{
			return (toolImageSize * largeIconScale * 2 + 45) * resolution;
		}
		return (toolImageSize * smallIconScale * 2 + 20) * resolution;
	}

	public void setHighlightedAreasFromTexts(List<MapText> texts)
	{
		highlightedAreas.clear();
		for (MapText text : texts)
		{
			if (text.line1Bounds != null)
			{
				highlightedAreas.add(AwtFactory.toAwtArea(text.line1Bounds.addRotatedRectangleThatHasTheSameAngleAndPivot(text.line2Bounds)));
			}
		}
	}

	public void setHighlightedAreasFromIcons(Collection<FreeIcon> icons, IconDrawer iconDrawer, boolean assumeValid)
	{
		List<FreeIcon> valid = new ArrayList<>();
		List<FreeIcon> invalid = new ArrayList<>();
		if (assumeValid)
		{
			valid.addAll(icons);
		}
		else
		{
			for (FreeIcon icon : icons)
			{
				boolean isValidPosition = icon.type == IconType.decorations || !iconDrawer.isContentBottomTouchingWater(icon);
				if (isValidPosition)
				{
					valid.add(icon);
				}
				else
				{
					invalid.add(icon);
				}
			}
		}

		setHighlightedAreasFromIconsValidAndInvalid(valid, invalid);
	}

	private void setHighlightedAreasFromIconsValidAndInvalid(List<FreeIcon> validIcons, List<FreeIcon> invalidIcons)
	{
		assert iconDrawer != null;
		if (iconDrawer == null)
		{
			return;
		}

		highlightedAreas.clear();
		if (validIcons != null)
		{
			for (FreeIcon icon : validIcons)
			{
				IconDrawTask task = iconDrawer.toIconDrawTask(icon);
				if (task != null)
				{
					nortantis.geom.Rectangle bounds = task.getOrCreateContentBoundsPadded();
					highlightedAreas.add(AwtFactory.toAwtArea(bounds));
				}
			}
		}

		redHighlightedAreas.clear();
		if (invalidIcons != null)
		{
			for (FreeIcon icon : invalidIcons)
			{
				IconDrawTask task = iconDrawer.toIconDrawTask(icon);
				if (task != null)
				{
					nortantis.geom.Rectangle bounds = task.getOrCreateContentBoundsPadded();
					redHighlightedAreas.add(AwtFactory.toAwtArea(bounds));
				}
			}
		}
	}

	public void clearHighlightedAreas()
	{
		highlightedAreas.clear();
		redHighlightedAreas.clear();
	}

	public void addProcessingAreasFromTexts(List<MapText> texts)
	{
		for (MapText text : texts)
		{
			if (text.line1Bounds != null)
			{
				processingAreas.add(text.line1Bounds);
			}

			if (text.line2Bounds != null)
			{
				processingAreas.add(text.line2Bounds);
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

	public void addHighlightedCenters(Collection<Center> centers)
	{
		highlightedCenters.addAll(centers);
	}

	public void clearHighlightedCenters()
	{
		if (highlightedCenters != null)
			highlightedCenters.clear();
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
		transform.translate(borderPadding, borderPadding);
		((Graphics2D) g).transform(transform);

		// Handle drawing/highlighting

		highlightArtPacksIfNeeded(g2);

		if (textBoxBounds != null)
		{
			drawTextBox(((Graphics2D) g));
		}

		if (iconToEditBounds != null)
		{
			if (!editBoxIsInMapSpace)
			{
				((Graphics2D) g).translate(-borderPadding, -borderPadding);
			}

			drawIconEditBox(((Graphics2D) g));

			if (!editBoxIsInMapSpace)
			{
				((Graphics2D) g).translate(borderPadding, borderPadding);
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

		drawSelectionBox((Graphics2D) g);
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
						nortantis.geom.Rectangle bounds = task.getOrCreateContentBoundsPadded();
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

		if (showEditBox)
		{
			if (highlightedAreas != null && highlightedAreas.size() != 1)
			{
				g.drawRect(editBounds.x, editBounds.y, editBounds.width, editBounds.height);
			}
		}

		if (!isIconToEditInAValidPosition)
		{
			final int inset = (int) (10 * resolution);
			final int minXSize = 8;
			if (inset > 0 && editBounds.width > inset * 2 + minXSize && editBounds.height > inset * 2 + minXSize)
			{
				Stroke prevStroke = g.getStroke();
				RenderingHints hints = g.getRenderingHints();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setStroke(new BasicStroke((float) Math.max(2.0 * resolution, 1.0)));

				if (editBounds.width > editBounds.height)
				{
					int additionalWidthInset = (editBounds.width - editBounds.height) / 2;
					g.drawLine(editBounds.x + inset + additionalWidthInset, editBounds.y + inset, editBounds.x + editBounds.width - (inset + additionalWidthInset),
							editBounds.y + editBounds.height - inset);
					g.drawLine(editBounds.x + inset + additionalWidthInset, editBounds.y + editBounds.height - inset, editBounds.x + editBounds.width - (inset + additionalWidthInset),
							editBounds.y + inset);
				}
				else
				{
					int additionalHeightInset = (editBounds.height - editBounds.width) / 2;
					g.drawLine(editBounds.x + inset, editBounds.y + inset + additionalHeightInset, editBounds.x + editBounds.width - inset,
							editBounds.y + editBounds.height - (inset + additionalHeightInset));
					g.drawLine(editBounds.x + inset, editBounds.y + editBounds.height - (inset + additionalHeightInset), editBounds.x + editBounds.width - inset,
							editBounds.y + inset + additionalHeightInset);
				}

				g.setStroke(prevStroke);
				g.setRenderingHints(hints);
			}
		}

		int padding = (int) (9 * resolution);
		// Place the image for the scale tool.
		{
			BufferedImage scaleIcon;
			if (editToolsSize == IconEditToolsSize.Large)
			{
				scaleIcon = scaleIconScaledLarge;
			}
			else if (editToolsSize == IconEditToolsSize.Medium)
			{
				scaleIcon = isIconToEditInAValidPosition ? scaleIconScaledMedium : redScaleIconScaledMedium;
			}
			else
			{
				scaleIcon = isIconToEditInAValidPosition ? scaleIconScaledSmall : redScaleIconScaledSmall;
			}

			int x, y;
			if (toolsLocation == IconEditToolsLocation.OutsideBox)
			{
				// Draw edit tools outside box
				x = editBounds.x + editBounds.width + padding;
				y = editBounds.y - (scaleIcon.getHeight()) - padding;
			}
			else
			{
				// Draw edit tools inside box because overlays often reach to top of the map, and you wouldn't be able to get to the tools
				// if they were drawn outside the map.
				x = editBounds.x + editBounds.width - scaleIcon.getWidth() - padding;
				y = editBounds.y + padding;
			}

			g.drawImage(scaleIcon, x, y, null);
			scaleToolArea = new Area(new Ellipse2D.Double(x, y, scaleIcon.getWidth(), scaleIcon.getHeight()));
			scaleToolArea.transform(g.getTransform());
		}

		// Place the image for the move tool.
		{
			BufferedImage moveIcon;
			if (editToolsSize == IconEditToolsSize.Large)
			{
				moveIcon = moveIconScaledLarge;
			}
			else if (editToolsSize == IconEditToolsSize.Medium)
			{
				moveIcon = isIconToEditInAValidPosition ? moveIconScaledMedium : redMoveIconScaledMedium;
			}
			else
			{
				moveIcon = isIconToEditInAValidPosition ? moveIconScaledSmall : redMoveIconScaledSmall;
			}

			int x = editBounds.x + (int) (Math.round(editBounds.width / 2.0)) - (int) (Math.round(moveIcon.getWidth() / 2.0));
			int y;
			if (toolsLocation == IconEditToolsLocation.OutsideBox)
			{
				y = editBounds.y - (moveIcon.getHeight()) - padding;
			}
			else
			{
				y = editBounds.y + padding;
			}

			g.drawImage(moveIcon, x, y, null);
			moveToolArea = new Area(new Ellipse2D.Double(x, y, moveIcon.getWidth(), moveIcon.getHeight()));
			moveToolArea.transform(g.getTransform());

		}
	}

	private void drawTextBox(Graphics2D g2)
	{
		g2.setColor(highlightEditColor);
		AffineTransform originalTransformCopy = g2.getTransform();

		int padding = (int) (9 * resolution);
		g2.draw(AwtFactory.toAwtArea(textBoxBounds));

		g2.rotate(textBoxBounds.angle, textBoxBounds.pivotX, textBoxBounds.pivotY);
		// Place the image for the rotation tool.
		{
			int x = (int) ((textBoxBounds.x) + textBoxBounds.width + padding);

			int y = (int) (textBoxBounds.y + (textBoxBounds.height / 2)) - (rotateIconScaled.getHeight() / 2);

			g2.drawImage(rotateIconScaled, x, y, null);
			rotateToolArea = new Area(new Ellipse2D.Double(x, y, rotateIconScaled.getWidth(), rotateIconScaled.getHeight()));
			rotateToolArea.transform(g2.getTransform());
		}

		// Place the image for the move tool.
		{
			int x = (int) (textBoxBounds.x) + (int) (Math.round(textBoxBounds.width / 2.0)) - (int) (Math.round(moveIconScaledSmall.getWidth() / 2.0));
			int y = (int) (textBoxBounds.y) - (moveIconScaledSmall.getHeight()) - padding;
			g2.drawImage(moveIconScaledSmall, x, y, null);
			moveToolArea = new Area(new Ellipse2D.Double(x, y, moveIconScaledSmall.getWidth(), moveIconScaledSmall.getHeight()));
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

		if (!redHighlightedAreas.isEmpty())
		{
			g.setColor(getInvalidPositionColor());
			for (Area area : redHighlightedAreas)
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
		graph.drawRivers(AwtFactory.wrap((Graphics2D) g), null, null, AwtBridge.fromAwtColor(waterHighlightColor));
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

			BufferedImage rotateIcon = AwtBridge.toBufferedImage(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "rotate text.png").toString()));
			rotateIconScaled = AwtBridge.toBufferedImage(
					ImageHelper.getInstance().scaleByWidth(AwtBridge.fromBufferedImage(rotateIcon), (int) (rotateIcon.getWidth() * resolution * smallIconScale), Method.ULTRA_QUALITY));

			try (Image moveIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "move text.png").toString());
					Image moveIconScaledWrapped = ImageHelper.getInstance().scaleByWidth(moveIcon, (int) (moveIcon.getWidth() * resolution * smallIconScale), Method.ULTRA_QUALITY))
			{
				moveIconScaledSmall = AwtBridge.toBufferedImage(moveIconScaledWrapped);
				redMoveIconScaledSmall = AwtBridge
						.toBufferedImage(ImageHelper.getInstance().copyAlphaTo(ImageHelper.getInstance().colorize(ImageHelper.getInstance().convertToGrayscale(moveIconScaledWrapped),
								AwtBridge.fromAwtColor(getInvalidPositionColor()), ColorizeAlgorithm.algorithm2), moveIconScaledWrapped));
			}

			try (Image scaleIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "scale.png").toString());
					Image scaleIconScaledWrapped = ImageHelper.getInstance().scaleByWidth(scaleIcon, (int) (scaleIcon.getWidth() * resolution * smallIconScale), Method.ULTRA_QUALITY))
			{
				scaleIconScaledSmall = AwtBridge.toBufferedImage(scaleIconScaledWrapped);
				redScaleIconScaledSmall = AwtBridge
						.toBufferedImage(ImageHelper.getInstance().copyAlphaTo(ImageHelper.getInstance().colorize(ImageHelper.getInstance().convertToGrayscale(scaleIconScaledWrapped),
								AwtBridge.fromAwtColor(getInvalidPositionColor()), ColorizeAlgorithm.algorithm2), scaleIconScaledWrapped));
			}

			try (Image moveIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "move text.png").toString());
					Image moveIconScaledWrapped = ImageHelper.getInstance().scaleByWidth(moveIcon, (int) (moveIcon.getWidth() * resolution * mediumIconScale), Method.ULTRA_QUALITY))
			{
				moveIconScaledMedium = AwtBridge.toBufferedImage(moveIconScaledWrapped);
				redMoveIconScaledMedium = AwtBridge
						.toBufferedImage(ImageHelper.getInstance().copyAlphaTo(ImageHelper.getInstance().colorize(ImageHelper.getInstance().convertToGrayscale(moveIconScaledWrapped),
								AwtBridge.fromAwtColor(getInvalidPositionColor()), ColorizeAlgorithm.algorithm2), moveIconScaledWrapped));
			}

			try (Image scaleIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "scale.png").toString());
					Image scaleIconScaledWrapped = ImageHelper.getInstance().scaleByWidth(scaleIcon, (int) (scaleIcon.getWidth() * resolution * mediumIconScale), Method.ULTRA_QUALITY))
			{
				scaleIconScaledMedium = AwtBridge.toBufferedImage(scaleIconScaledWrapped);
				redScaleIconScaledMedium = AwtBridge
						.toBufferedImage(ImageHelper.getInstance().copyAlphaTo(ImageHelper.getInstance().colorize(ImageHelper.getInstance().convertToGrayscale(scaleIconScaledWrapped),
								AwtBridge.fromAwtColor(getInvalidPositionColor()), ColorizeAlgorithm.algorithm2), scaleIconScaledWrapped));
			}

			try (Image moveIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "move text.png").toString());
					Image moveIconScaledWrapped = ImageHelper.getInstance().scaleByWidth(moveIcon, (int) (moveIcon.getWidth() * resolution * largeIconScale), Method.ULTRA_QUALITY))
			{
				moveIconScaledLarge = AwtBridge.toBufferedImage(moveIconScaledWrapped);
			}

			try (Image scaleIcon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "scale.png").toString());
					Image scaleIconScaledWrapped = ImageHelper.getInstance().scaleByWidth(scaleIcon, (int) (scaleIcon.getWidth() * resolution * largeIconScale), Method.ULTRA_QUALITY))
			{
				scaleIconScaledLarge = AwtBridge.toBufferedImage(scaleIconScaledWrapped);
			}
		}
	}

	public void setBorderPadding(int borderPadding)
	{
		this.borderPadding = borderPadding;
	}

	public int getBorderPadding()
	{
		return borderPadding;
	}

	/**
	 * Converts a mouse screen point to resolution-invariant coordinates.
	 */
	public nortantis.geom.Point screenToRI(java.awt.Point screenPoint)
	{
		double graphX = screenPoint.x * osScale / zoom - borderPadding;
		double graphY = screenPoint.y * osScale / zoom - borderPadding;
		double res = resolution > 0 ? resolution : 1.0;
		return new nortantis.geom.Point(graphX / res, graphY / res);
	}

	/**
	 * Enables selection box mode. The listener is called whenever the selection box is created or modified by the user.
	 */
	public void enableSelectionBox(Runnable onChange)
	{
		this.selectionBoxChangeListener = onChange;
	}

	public boolean isSelectionBoxActive()
	{
		return selectionBoxChangeListener != null;
	}

	public void clearSelectionBox()
	{
		this.selectionBoxChangeListener = null;
		this.selectionBoxRI = null;
		this.selectionBoxDraggedHandle = null;
		this.selectionBoxDragStartRI = null;
		this.selectionBoxRIAtDragStart = null;
		this.selectionBoxDragOffset = null;
		this.selectionBoxConstraintsRI = null;
		this.selectionBoxLockedAspectRatio = 0;
		repaint();
	}

	/**
	 * Sets an optional bounding rectangle (in RI coordinates) that the selection box is clamped to. Pass null to remove the constraint.
	 */
	public void setSelectionBoxConstraints(nortantis.geom.Rectangle constraints)
	{
		this.selectionBoxConstraintsRI = constraints;
	}

	/**
	 * Locks the selection box to a specific aspect ratio (width / height). Pass 0 to unlock.
	 */
	public void setSelectionBoxLockedAspectRatio(double ratio)
	{
		this.selectionBoxLockedAspectRatio = ratio;
	}

	public void setSelectionBoxRI(nortantis.geom.Rectangle rect)
	{
		this.selectionBoxRI = rect;
		repaint();
	}

	public nortantis.geom.Rectangle getSelectionBoxRI()
	{
		return selectionBoxRI;
	}

	private void onSelectionBoxMousePressed(MouseEvent e)
	{
		if (selectionBoxChangeListener == null || !SwingUtilities.isLeftMouseButton(e))
		{
			return;
		}
		selectionBoxDragStartRI = screenToRI(e.getPoint());
		selectionBoxRIAtDragStart = selectionBoxRI;
		selectionBoxDraggedHandle = selectionBoxRI != null ? getSelectionBoxHandleMouseIsIn() : BoxSelectHandle.NONE;

		// Compute the offset from the click point to the edge(s) being moved,
		// so dragging doesn't snap the edge to the cursor on the first move event.
		double offsetX = 0, offsetY = 0;
		if (selectionBoxRIAtDragStart != null && selectionBoxDraggedHandle != BoxSelectHandle.NONE
				&& selectionBoxDraggedHandle != BoxSelectHandle.CENTER)
		{
			double left = selectionBoxRIAtDragStart.x;
			double top = selectionBoxRIAtDragStart.y;
			double right = left + selectionBoxRIAtDragStart.width;
			double bottom = top + selectionBoxRIAtDragStart.height;
			BoxSelectHandle h = selectionBoxDraggedHandle;
			if (h == BoxSelectHandle.LEFT || h == BoxSelectHandle.UPPER_LEFT || h == BoxSelectHandle.LOWER_LEFT)
				offsetX = left - selectionBoxDragStartRI.x;
			if (h == BoxSelectHandle.RIGHT || h == BoxSelectHandle.UPPER_RIGHT || h == BoxSelectHandle.LOWER_RIGHT)
				offsetX = right - selectionBoxDragStartRI.x;
			if (h == BoxSelectHandle.TOP || h == BoxSelectHandle.UPPER_LEFT || h == BoxSelectHandle.UPPER_RIGHT)
				offsetY = top - selectionBoxDragStartRI.y;
			if (h == BoxSelectHandle.BOTTOM || h == BoxSelectHandle.LOWER_LEFT || h == BoxSelectHandle.LOWER_RIGHT)
				offsetY = bottom - selectionBoxDragStartRI.y;
		}
		selectionBoxDragOffset = new nortantis.geom.Point(offsetX, offsetY);
	}

	private void onSelectionBoxMouseDragged(MouseEvent e)
	{
		if (selectionBoxChangeListener == null || selectionBoxDragStartRI == null || !SwingUtilities.isLeftMouseButton(e))
		{
			return;
		}
		nortantis.geom.Point currentRI = screenToRI(e.getPoint());
		nortantis.geom.Rectangle newBox = computeNewSelectionBox(selectionBoxDraggedHandle, currentRI);
		if (newBox != null && newBox.width > 0 && newBox.height > 0)
		{
			selectionBoxRI = newBox;
			repaint();
			selectionBoxChangeListener.run();
		}
	}

	private void onSelectionBoxMouseReleased(MouseEvent e)
	{
		if (!SwingUtilities.isLeftMouseButton(e))
		{
			return;
		}
		selectionBoxDraggedHandle = null;
		selectionBoxDragStartRI = null;
		selectionBoxRIAtDragStart = null;
		selectionBoxDragOffset = null;
	}

	private void onSelectionBoxMouseClicked(MouseEvent e)
	{
		if (selectionBoxChangeListener == null || !SwingUtilities.isLeftMouseButton(e) || selectionBoxRI == null)
		{
			return;
		}
		nortantis.geom.Point clickRI = screenToRI(e.getPoint());
		if (!selectionBoxRI.contains(clickRI.x, clickRI.y))
		{
			selectionBoxRI = null;
			repaint();
			selectionBoxChangeListener.run();
		}
	}

	/**
	 * Computes the new selection box rectangle given the handle being dragged and the current mouse position in RI coordinates. Applies aspect
	 * ratio and bounds constraints. Returns null if the resulting box is degenerate.
	 */
	private nortantis.geom.Rectangle computeNewSelectionBox(BoxSelectHandle handle, nortantis.geom.Point currentRI)
	{
		// When aspect ratio is locked, edge handles act like CENTER (move the whole box).
		if (selectionBoxLockedAspectRatio > 0 && handle != null && handle != BoxSelectHandle.NONE && handle != BoxSelectHandle.CENTER
				&& !handle.isCorner())
		{
			handle = BoxSelectHandle.CENTER;
		}

		if (handle == null || handle == BoxSelectHandle.NONE)
		{
			// Draw a new box from scratch.
			nortantis.geom.Rectangle box;
			if (selectionBoxLockedAspectRatio > 0)
			{
				box = applyAspectRatioConstraint(selectionBoxDragStartRI.x, selectionBoxDragStartRI.y, currentRI.x, currentRI.y);
			}
			else
			{
				box = nortantis.geom.Rectangle.fromCorners(selectionBoxDragStartRI.x, selectionBoxDragStartRI.y, currentRI.x, currentRI.y);
			}
			return clampResizeToConstraints(box);
		}

		if (handle == BoxSelectHandle.CENTER)
		{
			// Translate the whole box; keep it fully inside the constraints.
			double dx = currentRI.x - selectionBoxDragStartRI.x;
			double dy = currentRI.y - selectionBoxDragStartRI.y;
			nortantis.geom.Rectangle moved = new nortantis.geom.Rectangle(selectionBoxRIAtDragStart.x + dx, selectionBoxRIAtDragStart.y + dy,
					selectionBoxRIAtDragStart.width, selectionBoxRIAtDragStart.height);
			return clampMoveToConstraints(moved);
		}

		// Edge and corner handles: resize.
		double left = selectionBoxRIAtDragStart.x;
		double top = selectionBoxRIAtDragStart.y;
		double right = left + selectionBoxRIAtDragStart.width;
		double bottom = top + selectionBoxRIAtDragStart.height;

		nortantis.geom.Rectangle result;
		if (selectionBoxLockedAspectRatio > 0)
		{
			// Aspect-ratio-constrained corner resize.
			double movingX = currentRI.x + selectionBoxDragOffset.x;
			double movingY = currentRI.y + selectionBoxDragOffset.y;
			double fixedX, fixedY;
			if (handle == BoxSelectHandle.UPPER_LEFT)
			{
				fixedX = right;
				fixedY = bottom;
			}
			else if (handle == BoxSelectHandle.UPPER_RIGHT)
			{
				fixedX = left;
				fixedY = bottom;
			}
			else if (handle == BoxSelectHandle.LOWER_LEFT)
			{
				fixedX = right;
				fixedY = top;
			}
			else if (handle == BoxSelectHandle.LOWER_RIGHT)
			{
				fixedX = left;
				fixedY = top;
			}
			else
			{
				return null;
			}
			result = applyAspectRatioConstraint(fixedX, fixedY, movingX, movingY);
		}
		else
		{
			if (handle == BoxSelectHandle.UPPER_LEFT || handle == BoxSelectHandle.LEFT || handle == BoxSelectHandle.LOWER_LEFT)
				left = currentRI.x + selectionBoxDragOffset.x;
			if (handle == BoxSelectHandle.UPPER_RIGHT || handle == BoxSelectHandle.RIGHT || handle == BoxSelectHandle.LOWER_RIGHT)
				right = currentRI.x + selectionBoxDragOffset.x;
			if (handle == BoxSelectHandle.UPPER_LEFT || handle == BoxSelectHandle.TOP || handle == BoxSelectHandle.UPPER_RIGHT)
				top = currentRI.y + selectionBoxDragOffset.y;
			if (handle == BoxSelectHandle.LOWER_LEFT || handle == BoxSelectHandle.BOTTOM || handle == BoxSelectHandle.LOWER_RIGHT)
				bottom = currentRI.y + selectionBoxDragOffset.y;
			result = nortantis.geom.Rectangle.fromCorners(left, top, right, bottom);
		}

		return clampResizeToConstraints(result);
	}

	/**
	 * Returns a new rectangle with corners at (fixedX, fixedY) and (movingX, movingY) adjusted so that width/height equals
	 * selectionBoxLockedAspectRatio. Uses whichever axis has the larger displacement.
	 */
	private nortantis.geom.Rectangle applyAspectRatioConstraint(double fixedX, double fixedY, double movingX, double movingY)
	{
		double dx = movingX - fixedX;
		double dy = movingY - fixedY;
		double ratio = selectionBoxLockedAspectRatio;
		double signX = dx >= 0 ? 1 : -1;
		double signY = dy >= 0 ? 1 : -1;
		double absDx = Math.abs(dx);
		double absDy = Math.abs(dy);
		if (absDx > absDy * ratio)
		{
			// X-axis dominates: derive height from width.
			movingY = fixedY + signY * (absDx / ratio);
		}
		else
		{
			// Y-axis dominates: derive width from height.
			movingX = fixedX + signX * (absDy * ratio);
		}
		return nortantis.geom.Rectangle.fromCorners(fixedX, fixedY, movingX, movingY);
	}

	/**
	 * Clips a resized/newly-drawn selection box to selectionBoxConstraintsRI. Returns null if the intersection is empty.
	 */
	private nortantis.geom.Rectangle clampResizeToConstraints(nortantis.geom.Rectangle box)
	{
		if (selectionBoxConstraintsRI == null || box == null)
		{
			return box;
		}
		nortantis.geom.Rectangle c = selectionBoxConstraintsRI;
		double left = Math.max(c.x, box.x);
		double top = Math.max(c.y, box.y);
		double right = Math.min(c.x + c.width, box.x + box.width);
		double bottom = Math.min(c.y + c.height, box.y + box.height);
		if (right > left && bottom > top)
		{
			return new nortantis.geom.Rectangle(left, top, right - left, bottom - top);
		}
		return null;
	}

	/**
	 * Translates a moved selection box so it stays fully inside selectionBoxConstraintsRI, without changing its size.
	 */
	private nortantis.geom.Rectangle clampMoveToConstraints(nortantis.geom.Rectangle box)
	{
		if (selectionBoxConstraintsRI == null || box == null)
		{
			return box;
		}
		nortantis.geom.Rectangle c = selectionBoxConstraintsRI;
		double x = Math.max(c.x, Math.min(c.x + c.width - box.width, box.x));
		double y = Math.max(c.y, Math.min(c.y + c.height - box.height, box.y));
		return new nortantis.geom.Rectangle(x, y, box.width, box.height);
	}

	final double boxSelectHandleSizePercent = 0.2;
	private void drawSelectionBox(Graphics2D g2)
	{
		if (selectionBoxRI == null)
		{
			return;
		}
		IntRectangle selectionBoxScaled = getSelectionBoxScaledByResolution();
		int x = selectionBoxScaled.x;
		int y = selectionBoxScaled.y;
		int width = selectionBoxScaled.width;
		int height = selectionBoxScaled.height;

		// Draw corner/edge handles.
		g2.setColor(processingColor);
		g2.setStroke(new BasicStroke(1.5f));
		BoxSelectHandle boxHandle = getSelectionBoxHandleMouseIsIn();

		// When the aspect ratio is locked, edge handles are disabled: treat them as CENTER for highlight purposes.
		boolean aspectRatioLocked = selectionBoxLockedAspectRatio > 0;
		BoxSelectHandle effectiveHandle = boxHandle;
		if (aspectRatioLocked && effectiveHandle != null && !effectiveHandle.isCorner()
				&& effectiveHandle != BoxSelectHandle.NONE && effectiveHandle != BoxSelectHandle.CENTER)
		{
			effectiveHandle = BoxSelectHandle.CENTER;
		}

		if (effectiveHandle != null && (effectiveHandle == BoxSelectHandle.NONE || effectiveHandle == BoxSelectHandle.CENTER || effectiveHandle.isCorner()))
		{
			for (BoxSelectHandle handle : new BoxSelectHandle[] { BoxSelectHandle.UPPER_LEFT, BoxSelectHandle.UPPER_RIGHT, BoxSelectHandle.LOWER_LEFT, BoxSelectHandle.LOWER_RIGHT })
			{
				Rectangle loc = getSelectionBoxHandleLocation(handle);
				g2.drawRect(loc.x, loc.y, loc.width, loc.height);
			}
		}
		else
		{
			if (effectiveHandle != null && effectiveHandle != BoxSelectHandle.NONE)
			{
				// For top, bottom, and sides, just highlight the area the handle is in.
				Rectangle rect = getSelectionBoxHandleLocation(effectiveHandle);
				if (rect != null)
				{
					g2.drawRect(rect.x, rect.y, rect.width, rect.height);
				}
			}
		}

		// Semi-transparent blue fill
		g2.setColor(new Color(0, 120, 255, 50));
		g2.fillRect(x, y, width, height);

		// Yellow border
		Stroke prevStroke = g2.getStroke();
		g2.setStroke(new BasicStroke(2.0f));
		g2.setColor(highlightEditColor);
		g2.drawRect(x, y, width, height);
		g2.setStroke(prevStroke);

		// Move icon centered in the box, only if it fits within the CENTER handle area
		Rectangle centerHandle = getSelectionBoxHandleLocation(BoxSelectHandle.CENTER);
		if (moveIconScaledSmall != null && centerHandle != null
				&& moveIconScaledSmall.getWidth() <= centerHandle.width
				&& moveIconScaledSmall.getHeight() <= centerHandle.height)
		{
			int iconX = centerHandle.x + (int) Math.round(centerHandle.width / 2.0) - (int) Math.round(moveIconScaledSmall.getWidth() / 2.0);
			int iconY = centerHandle.y + (int) Math.round(centerHandle.height / 2.0) - (int) Math.round(moveIconScaledSmall.getHeight() / 2.0);
			g2.drawImage(moveIconScaledSmall, iconX, iconY, null);
		}
	}

	private IntRectangle getSelectionBoxScaledByResolution()
	{
		int x = (int) (selectionBoxRI.x * resolution);
		int y = (int) (selectionBoxRI.y * resolution);
		int width = (int) (selectionBoxRI.width * resolution);
		int height = (int) (selectionBoxRI.height * resolution);
		return new IntRectangle(x, y, width, height);
	}

	public BoxSelectHandle getSelectionBoxHandleMouseIsIn()
	{
		if (selectionBoxRI == null)
		{
			return BoxSelectHandle.NONE;
		}

		java.awt.Point screenPosition = getMousePosition();
		if (screenPosition == null)
		{
			return BoxSelectHandle.NONE;
		}
		// Convert screen/component coords to map-pixel coords (RI * resolution),
		// which is what getSelectionBoxHandleLocation returns.
		nortantis.geom.Point riPoint = screenToRI(screenPosition);
		double res = resolution > 0 ? resolution : 1.0;
		java.awt.Point mousePosition = new java.awt.Point((int) (riPoint.x * res), (int) (riPoint.y * res));

		for (BoxSelectHandle handle : BoxSelectHandle.values())
		{
			Rectangle location = getSelectionBoxHandleLocation(handle);
			if (location != null && location.contains(mousePosition))
			{
				return handle;
			}
		}
		return BoxSelectHandle.NONE;
	}

	private Rectangle getSelectionBoxHandleLocation(BoxSelectHandle boxHandle)
	{
		if (boxHandle == null || boxHandle == BoxSelectHandle.NONE)
		{
			return null;
		}
		IntRectangle selectionBoxScaled = getSelectionBoxScaledByResolution();
		int x = selectionBoxScaled.x;
		int y = selectionBoxScaled.y;
		int width = selectionBoxScaled.width;
		int height = selectionBoxScaled.height;
		final int handleWidth = (int) (width * boxSelectHandleSizePercent);
		final int handleHeight = (int) (height * boxSelectHandleSizePercent);

		if (boxHandle == BoxSelectHandle.UPPER_LEFT)
		{
			return new Rectangle(x, y, handleWidth, handleHeight);
		}
		if (boxHandle == BoxSelectHandle.UPPER_RIGHT)
		{
			return new Rectangle(x + (width - handleWidth), y, handleWidth, handleHeight);
		}
		if (boxHandle == BoxSelectHandle.LOWER_LEFT)
		{
			return new Rectangle(x, y + (height - handleHeight), handleWidth, handleHeight);
		}
		if (boxHandle == BoxSelectHandle.LOWER_RIGHT)
		{
			return new Rectangle(x + (width - handleWidth), y + (height - handleHeight), handleWidth, handleHeight);
		}
		if (boxHandle == BoxSelectHandle.TOP)
		{
			return new Rectangle(x + handleWidth, y, width - handleWidth * 2, handleHeight);
		}
		if (boxHandle == BoxSelectHandle.LEFT)
		{
			return new Rectangle(x, y + handleHeight, handleWidth, height - handleHeight * 2);
		}
		if (boxHandle == BoxSelectHandle.RIGHT)
		{
			return new Rectangle(x + (width - handleWidth), y + handleHeight, handleWidth, height - handleHeight * 2);
		}
		if (boxHandle == BoxSelectHandle.BOTTOM)
		{
			return new Rectangle(x + handleWidth, y + (height - handleHeight), width - handleWidth * 2, handleHeight);
		}
		if (boxHandle == BoxSelectHandle.CENTER)
		{
			return new Rectangle(x + handleWidth, y + handleHeight, width - handleWidth * 2, height - handleHeight * 2);
		}
		return null;
	}


	public enum BoxSelectHandle
	{
		NONE, LEFT, RIGHT, TOP, BOTTOM, UPPER_LEFT, UPPER_RIGHT, LOWER_LEFT, LOWER_RIGHT, CENTER;

		public boolean isCorner()
		{
			return this == BoxSelectHandle.UPPER_LEFT || this == BoxSelectHandle.UPPER_RIGHT ||  this == BoxSelectHandle.LOWER_LEFT || this == BoxSelectHandle.LOWER_RIGHT;
		}
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
		hideIconEditTools();
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
