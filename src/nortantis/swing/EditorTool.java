package nortantis.swing;

import nortantis.FontFinder;
import nortantis.MapSettings;
import nortantis.editor.EdgeType;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.util.OSHelper;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

public abstract class EditorTool
{
	protected final MapEditingPanel mapEditingPanel;
	private JPanel toolOptionsPanel;
	private JScrollPane toolsOptionsPanelContainer;
	protected MainWindow mainWindow;
	private JToggleButton toggleButton;
	protected Undoer undoer;
	protected ToolsPanel toolsPanel;
	protected List<Integer> brushSizes = Arrays.asList(1, 25, 70, 140);
	protected MapUpdater updater;

	public EditorTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		this.mainWindow = parent;
		this.toolsPanel = toolsPanel;
		mapEditingPanel = parent.mapEditingPanel;
		toolOptionsPanel = createToolOptionsPanel();
		toolsOptionsPanelContainer = new JScrollPane(toolOptionsPanel);
		toolsOptionsPanelContainer.setBorder(BorderFactory.createEmptyBorder());
		undoer = parent.undoer;
		this.updater = mapUpdater;
	}

	public abstract String getToolbarName();

	public abstract int getMnemonic();

	public abstract Image getToolIcon();

	public void onSwitchingTo()
	{
		// This is needed so that highlights in the overlay tool clear all the way when switching to other tools. I don't know why, maybe a
		// bug in Swing.
		mainWindow.revalidate();
		mainWindow.repaint();
	}

	public abstract void onSwitchingAway();

	protected abstract JPanel createToolOptionsPanel();

	public JPanel getToolOptionsPanel()
	{
		return toolOptionsPanel;
	}

	public JScrollPane getToolOptionsPane()
	{
		return toolsOptionsPanelContainer;
	}

	protected abstract void handleMousePressedOnMap(MouseEvent e);

	protected void handleMouseRightPressedOnMap(MouseEvent e)
	{
	}

	protected abstract void handleMouseReleasedOnMap(MouseEvent e);

	protected abstract void handleMouseMovedOnMap(MouseEvent e);

	protected abstract void handleMouseDraggedOnMap(MouseEvent e);

	protected abstract void handleMouseExitedMap(MouseEvent e);

	protected abstract void onAfterShowMap();

	public void setToggled(boolean toggled)
	{
		toggleButton.setSelected(toggled);
		updateBorder();
	}

	public void updateBorder()
	{
		if ((OSHelper.isWindows() || OSHelper.isLinux()) && UserPreferences.getInstance().lookAndFeel == LookAndFeel.System)
		{
			toggleButton.setBorder(ToolsPanel.createToggleButtonBorder(false));
			return;
		}

		toggleButton.setBorder(ToolsPanel.createToggleButtonBorder(toggleButton.isSelected()));
	}

	public void setToggleButton(JToggleButton toggleButton)
	{
		this.toggleButton = toggleButton;
	}

	protected abstract void onAfterUndoRedo();

	protected abstract void onBeforeUndoRedo();

	public nortantis.geom.Point getPointOnGraph(java.awt.Point pointOnMapEditingPanel)
	{
		if (pointOnMapEditingPanel == null)
		{
			return null;
		}

		int borderWidth = updater.mapParts.background.getBorderPaddingScaledByResolution();
		double zoom = mainWindow.zoom;
		double osScale = mapEditingPanel.osScale;
		return new nortantis.geom.Point((((pointOnMapEditingPanel.x - (borderWidth * zoom * (1.0 / osScale))) * (1.0 / zoom) * osScale)),
				(((pointOnMapEditingPanel.y - (borderWidth * zoom) * (1.0 / osScale)) * (1.0 / zoom) * osScale)));
	}

	protected Set<Center> getSelectedCenters(java.awt.Point pointFromMouse, int brushDiameter)
	{
		Set<Center> selected = new HashSet<Center>();

		if (updater.mapParts == null || updater.mapParts.graph == null)
		{
			assert false;
			return selected;
		}

		int brushRadius = (int) ((double) ((brushDiameter / mainWindow.zoom)) * mapEditingPanel.osScale) / 2;

		if (!new RotatedRectangle(updater.mapParts.graph.bounds).overlapsCircle(getPointOnGraph(pointFromMouse), brushRadius))
		{
			// The brush is off the map.
			return selected;
		}

		Center center = updater.mapParts.graph.findClosestCenter(getPointOnGraph(pointFromMouse));
		if (center == null)
		{
			return selected;
		}
		else
		{
			selected.add(center);
		}

		if (brushDiameter <= 1)
		{
			return selected;
		}

		return updater.mapParts.graph.breadthFirstSearch((c) -> isCenterOverlappingCircle(c, getPointOnGraph(pointFromMouse), brushRadius), center);
	}

	/**
	 * Determines if a center is overlapping the given circle. Note that this isn't super precise because it doesn't account for the edge of
	 * the circle protruding into the center without overlapping any of the center's corners or centroid.
	 */
	private boolean isCenterOverlappingCircle(Center center, nortantis.geom.Point circleCenter, double radius)
	{
		for (Corner corner : center.corners)
		{
			if (isPointWithinCircle(corner.loc.x, corner.loc.y, circleCenter, radius))
			{
				return true;
			}
		}

		return isPointWithinCircle(center.loc.x, center.loc.y, circleCenter, radius);
	}

	private boolean isPointWithinCircle(double x, double y, nortantis.geom.Point circleCenter, double radius)
	{
		double deltaX = x - circleCenter.x;
		double deltaY = y - circleCenter.y;
		return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY)) <= radius;
	}

	public abstract void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean refreshImagePreviews);

	public abstract void getSettingsFromGUI(MapSettings settings);

	/**
	 * If this tool enables or disables any components, it should be done in this method so that the framework can call it to re-disable
	 * components after enabling everything in the tools options panel when the editor is ready to use.
	 */
	public abstract void handleEnablingAndDisabling(MapSettings settings);

	public abstract void onBeforeLoadingNewMap();

	public void handleImagesRefresh(MapSettings settings)
	{
	}

	public void handleCustomImagesPathChanged(String customImagesPath)
	{

	}

	/** Space kept clear at the left and right edges of a tool icon, so text never sits against the edge of the image. */
	private static final int toolIconTextMargin = 2;
	/** Tool icon text is not shrunk below this, since text too small to read is no more useful than text that overflows. */
	private static final int minToolIconFontSize = 8;
	/**
	 * The size a tool icon's label prefers. The Text tool's icon is the exception: its picture is the word itself, so it asks for its own,
	 * much larger size.
	 */
	protected static final int toolIconLabelFontSize = 16;

	/**
	 * One line of text on a tool icon: what to draw, the left edge to start it at, and the baseline to sit it on.
	 */
	protected record ToolIconText(String text, int x, int baselineY)
	{
	}

	/**
	 * Draws a tool icon's only line of text, centered across the icon.
	 */
	protected static void drawCenteredToolIconText(Painter p, Image icon, int preferredFontSize, String text, int baselineY)
	{
		setToolIconFont(p, icon, preferredFontSize, new ToolIconText(text, toolIconTextMargin, baselineY));
		p.drawString(text, (icon.getWidth() - p.stringWidth(text)) / 2.0, baselineY);
	}

	/**
	 * Draws the lines of a tool icon's text, each starting exactly where it asks to start, at the largest shared size at or below the
	 * preferred size at which every line fits between its own left edge and the right edge of the icon.
	 *
	 * <p>
	 * Sizing the text to the room the tool leaves for it is what keeps a translation from overflowing its icon or drifting across the
	 * picture behind it, without a table of per-language offsets. The family is bundled, so it has the same metrics on every operating
	 * system.
	 */
	protected static void drawToolIconText(Painter p, Image icon, int preferredFontSize, ToolIconText... lines)
	{
		setToolIconFont(p, icon, preferredFontSize, lines);
		for (ToolIconText line : lines)
		{
			p.drawString(line.text(), line.x(), line.baselineY());
		}
	}

	private static void setToolIconFont(Painter p, Image icon, int preferredFontSize, ToolIconText... lines)
	{
		for (int fontSize = preferredFontSize; fontSize > minToolIconFontSize; fontSize--)
		{
			p.setFont(createToolIconFont(fontSize, lines));
			if (everyLineFits(p, icon, lines))
			{
				return;
			}
		}
		p.setFont(createToolIconFont(minToolIconFontSize, lines));
	}

	private static boolean everyLineFits(Painter p, Image icon, ToolIconText... lines)
	{
		for (ToolIconText line : lines)
		{
			if (p.stringWidth(line.text()) > icon.getWidth() - line.x() - toolIconTextMargin)
			{
				return false;
			}
		}
		return true;
	}

	private static Font createToolIconFont(int fontSize, ToolIconText... lines)
	{
		Font font = Font.create(FontFinder.getChromeFontFamily(), FontStyle.Plain, fontSize);
		for (ToolIconText line : lines)
		{
			if (font.canDisplayUpTo(line.text()) != -1)
			{
				// A bundled family covers every language Nortantis is translated into, so reaching this means the chrome font for some
				// language is wrong. Chrome may substitute silently, unlike map text, but it should never need to.
				return Font.create("SansSerif", FontStyle.Plain, fontSize);
			}
		}
		return font;
	}

	protected boolean isSelected()
	{
		// toggleButton is assigned after construction (via setToggleButton), so it can be null if this is called while a subclass's
		// tool options panel is still being built. In that case the tool is not yet selected.
		return toggleButton != null && toggleButton.isSelected();
	}
}
