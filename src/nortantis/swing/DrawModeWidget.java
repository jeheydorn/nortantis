package nortantis.swing;

import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class DrawModeWidget
{
	private JToggleButton drawModeButton;
	private JToggleButton eraseModeButton;
	private JToggleButton replaceModeButton;
	private JToggleButton editModeButton;
	private SegmentedButtonWidget segmentedWidget;
	private final Runnable changeListener;

	public DrawModeWidget(String drawTooltipWithoutKeyboardShortcut, String eraseTooltipWithoutKeyboardShortcut, boolean includeReplaceButton, String replaceTooltipWithoutKeyboardShortcut,
			boolean includeEditModeButton, String editTooltipWithoutKeyboardShortcut, Runnable changeListener)
	{
		this.changeListener = changeListener;

		ActionListener modeListener = e ->
		{
			JToggleButton source = (JToggleButton) e.getSource();
			if (!source.isSelected())
			{
				source.setSelected(true);
			}
			source.grabFocus();
			changeListener.run();
		};

		drawModeButton = new JToggleButton(Translation.get("drawMode.draw"));
		drawModeButton.setToolTipText(drawTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.draw.shortcut", SwingHelper.getAltKeyName()) + ")");
		drawModeButton.setSelected(true);
		drawModeButton.addActionListener(modeListener);
		SwingHelper.bindAltMnemonic(drawModeButton, KeyEvent.VK_D);

		replaceModeButton = new JToggleButton(Translation.get("drawMode.replace"));
		replaceModeButton.setToolTipText(replaceTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.replace.shortcut", SwingHelper.getAltKeyName()) + ")");
		replaceModeButton.addActionListener(modeListener);
		SwingHelper.bindAltMnemonic(replaceModeButton, KeyEvent.VK_R);

		editModeButton = new JToggleButton(Translation.get("drawMode.edit"));
		editModeButton.setToolTipText(editTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.edit.shortcut", SwingHelper.getAltKeyName()) + ")");
		editModeButton.addActionListener(modeListener);
		SwingHelper.bindAltMnemonic(editModeButton, KeyEvent.VK_T);

		eraseModeButton = new JToggleButton(Translation.get("drawMode.erase"));
		eraseModeButton.setToolTipText(eraseTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.erase.shortcut", SwingHelper.getAltKeyName()) + ")");
		eraseModeButton.addActionListener(modeListener);
		SwingHelper.bindAltMnemonic(eraseModeButton, KeyEvent.VK_E);

		List<JToggleButton> buttons = new ArrayList<>();
		buttons.add(drawModeButton);
		if (includeReplaceButton)
		{
			buttons.add(replaceModeButton);
		}
		if (includeEditModeButton)
		{
			buttons.add(editModeButton);
		}
		buttons.add(eraseModeButton);

		segmentedWidget = new SegmentedButtonWidget(buttons);
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String labelTooltip)
	{
		return segmentedWidget.addToOrganizer(organizer, Translation.get("drawMode.mode.label"), labelTooltip);
	}

	public boolean isDrawMode()
	{
		return drawModeButton.isSelected();
	}

	public boolean isEraseMode()
	{
		return eraseModeButton.isSelected();
	}

	public boolean isReplaceMode()
	{
		return replaceModeButton.isSelected();
	}

	public boolean isEditMode()
	{
		return editModeButton.isSelected();
	}

	public void configureDrawButton(String translatedHtml, String tooltipWithoutShortcut, int mnemonic, String translatedShortcutText)
	{
		drawModeButton.setText(translatedHtml);
		drawModeButton.setToolTipText(tooltipWithoutShortcut + " (" + translatedShortcutText + ")");
		SwingHelper.bindAltMnemonic(drawModeButton, mnemonic);
		SwingHelper.reduceHorizontalMargin(drawModeButton);
	}

	public void selectEditMode()
	{
		selectEditMode(true);
	}

	public void selectEditMode(boolean grabFocus)
	{
		if (grabFocus)
		{
			editModeButton.doClick();
		}
		else
		{
			// Select edit mode and refresh the tool's edit UI (as a click would), but without moving keyboard focus onto the mode button.
			if (!editModeButton.isSelected())
			{
				editModeButton.setSelected(true);
			}
			changeListener.run();
		}
	}

	/**
	 * Moves keyboard focus to whichever mode button is currently selected. Use this when a tool selects an editable object (e.g. a text or
	 * river segment) but doesn't want to auto-focus the value-edit fields — placing focus here keeps the Tab order predictable and puts the
	 * user one Tab away from the edit fields.
	 */
	public void grabFocusOnSelectedButton()
	{
		if (drawModeButton.isSelected())
		{
			drawModeButton.grabFocus();
		}
		else if (editModeButton.isSelected())
		{
			editModeButton.grabFocus();
		}
		else if (replaceModeButton.isSelected())
		{
			replaceModeButton.grabFocus();
		}
		else if (eraseModeButton.isSelected())
		{
			eraseModeButton.grabFocus();
		}
	}
}
