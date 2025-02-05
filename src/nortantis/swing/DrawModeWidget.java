package nortantis.swing;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import nortantis.util.OSHelper;

public class DrawModeWidget
{
	private JToggleButton drawModeButton;
	private JToggleButton eraseModeButton;
	private JToggleButton replaceModeButton;
	private JToggleButton editModeButton;
	private boolean includeReplaceButton;
	private boolean includeEditModeButton;
	private JPanel container;

	public DrawModeWidget(String drawTooltipWithoutKeyboardShortcut, String eraseTooltipWithoutKeyboardShortcut,
			boolean includeReplaceButton, String replaceTooltipWithoutKeyboardShortcut, boolean includeEditModeButton,
			String editTooltipWithoutKeyboardShortcut, Runnable changeListener)
	{
		this.includeReplaceButton = includeReplaceButton;
		this.includeEditModeButton = includeEditModeButton;

		ActionListener modeListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (e != null && e.getSource() == eraseModeButton)
				{
					drawModeButton.setSelected(false);
					replaceModeButton.setSelected(false);
					editModeButton.setSelected(false);
					if (!eraseModeButton.isSelected())
					{
						eraseModeButton.setSelected(true);
					}
					eraseModeButton.grabFocus();
				}
				else if (e != null && e.getSource() == replaceModeButton)
				{
					drawModeButton.setSelected(false);
					eraseModeButton.setSelected(false);
					editModeButton.setSelected(false);
					if (!replaceModeButton.isSelected())
					{
						replaceModeButton.setSelected(true);
					}
					replaceModeButton.grabFocus();
				}
				else if (e != null && e.getSource() == editModeButton)
				{
					drawModeButton.setSelected(false);
					eraseModeButton.setSelected(false);
					replaceModeButton.setSelected(false);
					if (!editModeButton.isSelected())
					{
						editModeButton.setSelected(true);
					}
					editModeButton.grabFocus();
				}
				else
				{
					// Draw button
					eraseModeButton.setSelected(false);
					replaceModeButton.setSelected(false);
					editModeButton.setSelected(false);
					if (!drawModeButton.isSelected())
					{
						drawModeButton.setSelected(true);
					}
					drawModeButton.grabFocus();
				}
				changeListener.run();
			}
		};

		boolean isWindows = OSHelper.isWindows();
		drawModeButton = new JToggleButton("<html><u>D</u>raw</html>");
		drawModeButton.setToolTipText(drawTooltipWithoutKeyboardShortcut + " (Alt+D)");
		drawModeButton.setSelected(true);
		drawModeButton.addActionListener(modeListener);
		drawModeButton.setMnemonic(KeyEvent.VK_D);
		drawModeButton.setPreferredSize(new Dimension(isWindows ? 51 : 57, drawModeButton.getPreferredSize().height));

		replaceModeButton = new JToggleButton("<html><u>R</u>eplace</html>");
		replaceModeButton.setToolTipText(replaceTooltipWithoutKeyboardShortcut + " (Alt+R)");
		replaceModeButton.addActionListener(modeListener);
		replaceModeButton.setMnemonic(KeyEvent.VK_R);
		replaceModeButton.setPreferredSize(new Dimension(isWindows ? 65 : 75, replaceModeButton.getPreferredSize().height));

		editModeButton = new JToggleButton("<html>Edi<u>t</u></html>");
		editModeButton.setToolTipText(editTooltipWithoutKeyboardShortcut + " (Alt+T)");
		editModeButton.addActionListener(modeListener);
		editModeButton.setMnemonic(KeyEvent.VK_T);
		editModeButton.setPreferredSize(new Dimension(isWindows ? 51 : 53, editModeButton.getPreferredSize().height));

		eraseModeButton = new JToggleButton("<html><u>E</u>rase</html>");
		eraseModeButton.setToolTipText(eraseTooltipWithoutKeyboardShortcut + " (Alt+E)");
		eraseModeButton.addActionListener(modeListener);
		eraseModeButton.setMnemonic(KeyEvent.VK_E);
		eraseModeButton.setPreferredSize(new Dimension(isWindows ? 51 : 61, eraseModeButton.getPreferredSize().height));
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String labelTooltip)
	{
		container = new JPanel();
		container.setLayout(new WrapLayout(WrapLayout.LEFT));
		// Remove the horizontal and vertical gaps from the border around the elements.
		container.setBorder(BorderFactory.createEmptyBorder(-5, -5, -5, -5));
		addOptionsToContainer(true, true, includeReplaceButton, includeEditModeButton);

		return organizer.addLabelAndComponent("Mode:", labelTooltip, container);
	}

	private void addOptionsToContainer(boolean showDrawMode, boolean showEraseMode, boolean showReplaceMode, boolean showEditMode)
	{
		List<JToggleButton> visibleButtons = new ArrayList<>();

		if (showDrawMode)
		{
			visibleButtons.add(drawModeButton);
		}
		if (showReplaceMode)
		{
			visibleButtons.add(replaceModeButton);
		}
		if (showEditMode)
		{
			visibleButtons.add(editModeButton);
		}
		if (showEraseMode)
		{
			visibleButtons.add(eraseModeButton);
		}

		for (JToggleButton button : visibleButtons)
		{
			container.add(button);
		}

		if (visibleButtons.size() > 0)
		{
			Optional<JToggleButton> optional = visibleButtons.stream().filter((button) -> button.isSelected()).findFirst();
			if (!optional.isPresent())
			{
				visibleButtons.get(0).doClick();
			}
		}
	}

	public void showOrHideOptions(boolean showDrawMode, boolean showEraseMode, boolean showReplaceMode, boolean showEditMode)
	{
		container.removeAll();
		addOptionsToContainer(showDrawMode, showEraseMode, showReplaceMode, showEditMode);
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
}
