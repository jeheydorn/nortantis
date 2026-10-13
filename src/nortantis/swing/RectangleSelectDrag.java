package nortantis.swing;

import nortantis.geom.Point;
import nortantis.geom.Rectangle;

/**
 * A press of the mouse in Edit mode with rectangle selection, which becomes a selection rectangle once the mouse moves far enough from
 * where it was pressed, and otherwise is a click.
 */
class RectangleSelectDrag
{
	/**
	 * How far, in panel pixels, the mouse must move from where it was pressed before the press becomes a selection rectangle instead of a
	 * click. Keeps a slightly shaky click from selecting with a tiny rectangle.
	 */
	private static final int dragThresholdInPanelPixels = 4;

	private final java.awt.Point pressPointInPanel;
	/** The press point in resolution-invariant coordinates, so the rectangle stays anchored to the map if the zoom or display quality changes. */
	private final Point pressPointRI;
	private boolean isDragging;

	RectangleSelectDrag(java.awt.Point pressPointInPanel, Point pressPointRI)
	{
		this.pressPointInPanel = pressPointInPanel;
		this.pressPointRI = pressPointRI;
	}

	/**
	 * Records that the mouse moved to the given panel point. Returns whether the press has become a selection rectangle, which it stays once
	 * it has.
	 */
	boolean update(java.awt.Point mouseLocationInPanel)
	{
		if (!isDragging && mouseLocationInPanel != null && mouseLocationInPanel.distance(pressPointInPanel) >= dragThresholdInPanelPixels)
		{
			isDragging = true;
		}
		return isDragging;
	}

	boolean isDragging()
	{
		return isDragging;
	}

	/**
	 * The selection rectangle, in resolution-invariant coordinates, from the press point to the given point.
	 */
	Rectangle getRectangleRI(Point mouseLocationRI)
	{
		return Rectangle.fromCorners(pressPointRI.x, pressPointRI.y, mouseLocationRI.x, mouseLocationRI.y);
	}
}
