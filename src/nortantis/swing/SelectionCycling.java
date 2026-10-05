package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Alt+click (Option+click on macOS) in the Edit modes of the Text and Icons tools selects the next of the items under the cursor, which
 * reaches items hidden behind others. The position in the cycle is the current selection, so nothing about the cycle is stored.
 *
 * <p>
 * This also tracks whether Alt is held, so that tools can outline what Alt+click can reach, and keeps an Alt release that follows an
 * Alt+click from activating the menu bar, which pressing and releasing Alt by itself does on Windows.
 */
final class SelectionCycling
{
	private static boolean isKeyTrackingInstalled;
	private static boolean isAltDown;
	private static boolean wasMapClickedWhileAltDown;
	private static final List<Runnable> altChangeListeners = new CopyOnWriteArrayList<>();

	private SelectionCycling()
	{
	}

	/**
	 * Chooses what Alt+click selects among the items under the cursor: the item after the last one that is selected, wrapping around, or
	 * the first item if none is selected. Items are compared by identity.
	 *
	 * @param candidates
	 *            The items under the cursor, in the order cycling visits them, starting with the one a plain click selects.
	 */
	static <T> T chooseNext(List<T> candidates, Collection<T> selection)
	{
		if (candidates.isEmpty())
		{
			return null;
		}

		int lastSelectedIndex = -1;
		for (int i = 0; i < candidates.size(); i++)
		{
			T candidate = candidates.get(i);
			if (selection.stream().anyMatch(selected -> selected == candidate))
			{
				lastSelectedIndex = i;
			}
		}
		return candidates.get((lastSelectedIndex + 1) % candidates.size());
	}

	/**
	 * Whether Alt is held, as last seen by the keyboard.
	 */
	static boolean isAltDown()
	{
		return isAltDown;
	}

	/**
	 * Records that the map was clicked with Alt held, so that releasing Alt doesn't activate the menu bar.
	 */
	static void recordAltClick()
	{
		wasMapClickedWhileAltDown = true;
	}

	/**
	 * Runs the given action on the event dispatch thread whenever Alt is pressed or released.
	 */
	static void addAltChangeListener(Runnable listener)
	{
		installKeyTracking();
		altChangeListeners.add(listener);
	}

	private static void installKeyTracking()
	{
		if (isKeyTrackingInstalled)
		{
			return;
		}
		isKeyTrackingInstalled = true;

		// A KeyEventDispatcher sees the key wherever focus is, and runs before the look and feel's handling of Alt, so that it can keep an
		// Alt release from reaching the menu bar.
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e ->
		{
			if (e.getKeyCode() != KeyEvent.VK_ALT)
			{
				return false;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED)
			{
				if (!isAltDown)
				{
					isAltDown = true;
					wasMapClickedWhileAltDown = false;
					altChangeListeners.forEach(Runnable::run);
				}
				return false;
			}

			if (e.getID() == KeyEvent.KEY_RELEASED)
			{
				isAltDown = false;
				boolean wasClicked = wasMapClickedWhileAltDown;
				wasMapClickedWhileAltDown = false;
				altChangeListeners.forEach(Runnable::run);
				if (wasClicked)
				{
					// In case the look and feel acted on the press, make sure no menu is left selected.
					SwingUtilities.invokeLater(() -> MenuSelectionManager.defaultManager().clearSelectedPath());
					return true;
				}
			}
			return false;
		});

		// When Alt+Tab switches to another application, that application receives the Alt release.
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("activeWindow", e ->
		{
			if (e.getNewValue() == null && isAltDown)
			{
				isAltDown = false;
				wasMapClickedWhileAltDown = false;
				altChangeListeners.forEach(Runnable::run);
			}
		});
	}
}
