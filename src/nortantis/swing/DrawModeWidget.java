package nortantis.swing;

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

		drawModeButton = new JToggleButton("<html><u>D</u>raw</html>");
		drawModeButton.setToolTipText(drawTooltipWithoutKeyboardShortcut + " (Alt+D)");
		drawModeButton.setSelected(true);
		drawModeButton.addActionListener(modeListener);
		drawModeButton.setMnemonic(KeyEvent.VK_D);

		replaceModeButton = new JToggleButton("<html><u>R</u>eplace</html>");
		replaceModeButton.setToolTipText(replaceTooltipWithoutKeyboardShortcut + " (Alt+R)");
		replaceModeButton.addActionListener(modeListener);
		replaceModeButton.setMnemonic(KeyEvent.VK_R);

		editModeButton = new JToggleButton("<html>Edi<u>t</u></html>");
		editModeButton.setToolTipText(editTooltipWithoutKeyboardShortcut + " (Alt+T)");
		editModeButton.addActionListener(modeListener);
		editModeButton.setMnemonic(KeyEvent.VK_T);

		eraseModeButton = new JToggleButton("<html><u>E</u>rase</html>");
		eraseModeButton.setToolTipText(eraseTooltipWithoutKeyboardShortcut + " (Alt+E)");
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
		return segmentedWidget.addToOrganizer(organizer, "Mode:", labelTooltip);
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

	public void configureDrawButton(String html, String tooltipWithoutShortcut, int mnemonic, String shortcutText)
	{
		drawModeButton.setText(html);
		drawModeButton.setToolTipText(tooltipWithoutShortcut + " (" + shortcutText + ")");
		drawModeButton.setMnemonic(mnemonic);
		SwingHelper.reduceHorizontalMargin(drawModeButton);
	}

	public void selectEditMode()
	{
		editModeButton.doClick();
	}
}
