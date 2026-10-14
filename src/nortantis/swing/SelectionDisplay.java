package nortantis.swing;

import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;

/**
 * Decides how the selection of an Edit mode looks: the box with the handles, an outline around each selected item, or both. Also tracks
 * the command key, which shows the outlines while it is held, since a click or a rectangle then changes the selection item by item.
 */
class SelectionDisplay
{
	enum Shown
	{
		Nothing, EachOutline, Box, BoxAndEachOutline;

		boolean showsBox()
		{
			return this == Box || this == BoxAndEachOutline;
		}

		boolean showsEachOutline()
		{
			return this == EachOutline || this == BoxAndEachOutline;
		}
	}

	private final BooleanSupplier isSelectingByDrag;
	private final BooleanSupplier isHandlePressed;
	private final Runnable onCommandKeyChange;
	private boolean isCommandKeyHeld;
	/**
	 * Whether the command key has stayed down since it was used for a keyboard shortcut that changed the selection, such as select-all.
	 * While it has, the selection keeps its box rather than switching to the outlines the command key normally shows, so that the shortcut
	 * doesn't flash them.
	 */
	private boolean isCommandKeyHeldSinceShortcut;

	/**
	 * @param isSelectingByDrag
	 *            Whether a selection rectangle is being dragged or a selecting brush stroke is in progress.
	 * @param isHandlePressed
	 *            Whether one of the box's handles, such as the move handle, is pressed.
	 * @param onCommandKeyChange
	 *            Runs on the event dispatch thread when the command key is pressed or released, or when it stops being held since a
	 *            shortcut.
	 */
	SelectionDisplay(BooleanSupplier isSelectingByDrag, BooleanSupplier isHandlePressed, Runnable onCommandKeyChange)
	{
		this.isSelectingByDrag = isSelectingByDrag;
		this.isHandlePressed = isHandlePressed;
		this.onCommandKeyChange = onCommandKeyChange;

		// A KeyEventDispatcher sees the command key even when the focus is outside the main window's components, such as in a text field.
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e ->
		{
			if (SwingHelper.isCommandModifierKeyCode(e.getKeyCode()) && (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_RELEASED))
			{
				setCommandKeyHeld(e.getID() == KeyEvent.KEY_PRESSED);
			}
			// Let other dispatchers and the focused component handle the event too.
			return false;
		});
	}

	/**
	 * Takes whether the command key is held from a mouse event, which catches a release made while another window had the focus.
	 */
	void update(InputEvent e)
	{
		setCommandKeyHeld(SwingHelper.isCommandKeyDown(e));
	}

	private void setCommandKeyHeld(boolean isHeld)
	{
		boolean wasShowingOutlines = isCommandKeyShowingOutlines();
		boolean wasHeld = isCommandKeyHeld;
		isCommandKeyHeld = isHeld;
		if (!isHeld)
		{
			isCommandKeyHeldSinceShortcut = false;
		}
		if (wasHeld != isHeld || wasShowingOutlines != isCommandKeyShowingOutlines())
		{
			onCommandKeyChange.run();
		}
	}

	/**
	 * Records that a keyboard shortcut using the command key, such as select-all, just changed the selection.
	 */
	void onSelectionChangedByShortcut()
	{
		isCommandKeyHeldSinceShortcut = isCommandKeyHeld;
	}

	boolean isCommandKeyHeld()
	{
		return isCommandKeyHeld;
	}

	/**
	 * Whether the command key is held for changing the selection item by item, so that each selected item is outlined in place of the box.
	 */
	boolean isCommandKeyShowingOutlines()
	{
		return isCommandKeyHeld && !isCommandKeyHeldSinceShortcut;
	}

	/**
	 * What to show for a selection of the given number of items. A single item's box is the same rectangle as its outline, so a single item
	 * never shows both.
	 */
	Shown getShown(int selectionSize)
	{
		if (selectionSize == 0)
		{
			return Shown.Nothing;
		}
		if (isSelectingByDrag.getAsBoolean())
		{
			return Shown.EachOutline;
		}
		if (isHandlePressed.getAsBoolean())
		{
			return selectionSize > 1 ? Shown.BoxAndEachOutline : Shown.Box;
		}
		if (isCommandKeyShowingOutlines())
		{
			return Shown.EachOutline;
		}
		return Shown.Box;
	}
}
