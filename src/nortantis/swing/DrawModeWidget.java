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

	public DrawModeWidget(String drawTooltipWithoutKeyboardShortcut, String eraseTooltipWithoutKeyboardShortcut, boolean includeReplaceButton, String replaceTooltipWithoutKeyboardShortcut,
			boolean includeEditModeButton, String editTooltipWithoutKeyboardShortcut, Runnable changeListener)
	{
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
		drawModeButton.setToolTipText(drawTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.draw.shortcut") + ")");
		drawModeButton.setSelected(true);
		drawModeButton.addActionListener(modeListener);
		drawModeButton.setMnemonic(KeyEvent.VK_D);

		replaceModeButton = new JToggleButton(Translation.get("drawMode.replace"));
		replaceModeButton.setToolTipText(replaceTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.replace.shortcut") + ")");
		replaceModeButton.addActionListener(modeListener);
		replaceModeButton.setMnemonic(KeyEvent.VK_R);

		editModeButton = new JToggleButton(Translation.get("drawMode.edit"));
		editModeButton.setToolTipText(editTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.edit.shortcut") + ")");
		editModeButton.addActionListener(modeListener);
		editModeButton.setMnemonic(KeyEvent.VK_T);

		eraseModeButton = new JToggleButton(Translation.get("drawMode.erase"));
		eraseModeButton.setToolTipText(eraseTooltipWithoutKeyboardShortcut + " (" + Translation.get("drawMode.erase.shortcut") + ")");
		eraseModeButton.addActionListener(modeListener);
		eraseModeButton.setMnemonic(KeyEvent.VK_E);

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
		drawModeButton.setMnemonic(mnemonic);
		SwingHelper.reduceHorizontalMargin(drawModeButton);
	}

	public void selectEditMode()
	{
		editModeButton.doClick();
	}
}
