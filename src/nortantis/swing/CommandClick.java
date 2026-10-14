package nortantis.swing;

/**
 * A press with the command key held on a single item in Edit mode, which changes the selection only once the mouse is released, and only
 * if the mouse didn't move far enough first to make the press a drag. That way a press that turns into a selection rectangle or a brush
 * stroke doesn't first toggle the item it began on.
 *
 * @param <T>
 *            What the click changes the selection with when it is released.
 */
class CommandClick<T>
{
	private final java.awt.Point pressPointInPanel;
	private final T item;
	private final boolean isAltDown;

	CommandClick(java.awt.Point pressPointInPanel, T item, boolean isAltDown)
	{
		this.pressPointInPanel = pressPointInPanel;
		this.item = item;
		this.isAltDown = isAltDown;
	}

	/**
	 * Whether the mouse has moved far enough from the press, to the given panel point, for the press to be a drag instead of a click.
	 */
	boolean isDragAt(java.awt.Point mouseLocationInPanel)
	{
		return RectangleSelectDrag.isPastDragThreshold(pressPointInPanel, mouseLocationInPanel);
	}

	T getItem()
	{
		return item;
	}

	/**
	 * Whether Alt was held at the press, which makes the click remove the item rather than toggle it.
	 */
	boolean isAltDown()
	{
		return isAltDown;
	}
}
