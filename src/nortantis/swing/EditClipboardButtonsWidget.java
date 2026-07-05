package nortantis.swing;

import nortantis.swing.translation.Translation;

import javax.swing.JButton;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Standard "Copy / Paste / Delete" button row used by editor tools that have a multi-select state (IconsTool, LandWaterTool). The widget
 * builds three {@link JButton}s with their shortcut keys (Ctrl+C / Ctrl+V / Delete) already bound, wired to caller-supplied actions, and
 * exposes a single {@link #addToOrganizer} entry point that places them in a tool's options panel and returns the row hider.
 *
 * <p>
 * Callers that need an extra tool-specific button alongside (e.g. IconsTool's "Reset Scale") pass it via the overload that accepts
 * additional components — they get rendered in the same row as the standard three.
 */
public class EditClipboardButtonsWidget
{
	private final JButton copyButton;
	private final JButton pasteButton;
	private final JButton deleteButton;

	/**
	 * @param deleteTooltip
	 *            Tooltip for the Delete button. Copy/Paste tooltips are fixed (they reference the modifier key name); the Delete tooltip
	 *            varies by tool (icons vs. road/river CPs) so the caller supplies it.
	 */
	public EditClipboardButtonsWidget(Runnable copyAction, Runnable pasteAction, Runnable deleteAction, String deleteTooltip)
	{
		copyButton = new JButton(Translation.get("iconsTool.copy"));
		copyButton.setToolTipText(Translation.get("iconsTool.copy.tooltip", SwingHelper.getCommandKeyName()));
		SwingHelper.bindButtonShortcut(copyButton, KeyStroke.getKeyStroke(KeyEvent.VK_C, SwingHelper.getMenuShortcutKeyMask()), "copyAction");
		copyButton.addActionListener(e -> copyAction.run());

		pasteButton = new JButton(Translation.get("iconsTool.paste"));
		pasteButton.setToolTipText(Translation.get("iconsTool.paste.tooltip", SwingHelper.getCommandKeyName()));
		SwingHelper.bindButtonShortcut(pasteButton, KeyStroke.getKeyStroke(KeyEvent.VK_V, SwingHelper.getMenuShortcutKeyMask()), "pasteAction");
		pasteButton.addActionListener(e -> pasteAction.run());

		deleteButton = new JButton(Translation.get("iconsTool.delete"));
		deleteButton.setToolTipText(deleteTooltip);
		// Bind Backspace as well as Delete: the key labeled "delete" sends Backspace on most Mac keyboards.
		SwingHelper.bindButtonShortcut(deleteButton, "deleteAction", KeyStroke.getKeyStroke("DELETE"), KeyStroke.getKeyStroke("BACK_SPACE"));
		deleteButton.addActionListener(e -> deleteAction.run());
	}

	/** Adds the three buttons to {@code organizer} in a left-aligned row and returns the row's hider. */
	public RowHider addToOrganizer(GridBagOrganizer organizer)
	{
		return addToOrganizer(organizer, Collections.emptyList());
	}

	/**
	 * Adds the three buttons plus any {@code extraButtons} (in order) to {@code organizer} in a single left-aligned row and returns the
	 * row's hider. Tool-specific buttons (e.g. "Reset Scale") are appended after the standard three.
	 */
	public RowHider addToOrganizer(GridBagOrganizer organizer, List<? extends Component> extraButtons)
	{
		List<Component> all = new ArrayList<>();
		all.addAll(Arrays.asList(copyButton, pasteButton, deleteButton));
		all.addAll(extraButtons);
		return organizer.addLeftAlignedComponents(all);
	}
}
