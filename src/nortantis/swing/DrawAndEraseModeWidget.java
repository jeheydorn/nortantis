package nortantis.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

public class DrawAndEraseModeWidget
{
	private JToggleButton drawModeButton;
	private JToggleButton eraseModeButton;
	private JToggleButton replaceModeButton;
	private JToggleButton editModeButton;
	private boolean includeReplaceButton;
	private boolean includeEditModeButton;

	public DrawAndEraseModeWidget(String drawTooltipWithoutKeyboardShortcut, String eraseTooltipWithoutKeyboardShortcut,
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

		drawModeButton = new JToggleButton("<html><u>D</u>raw</html>");
		drawModeButton.setToolTipText(drawTooltipWithoutKeyboardShortcut + " (Alt+D)");
		drawModeButton.setSelected(true);
		drawModeButton.addActionListener(modeListener);
		drawModeButton.setMnemonic(KeyEvent.VK_D);
		drawModeButton.setPreferredSize(new Dimension(50, drawModeButton.getPreferredSize().height));

		eraseModeButton = new JToggleButton("<html><u>E</u>rase</html>");
		eraseModeButton.setToolTipText(eraseTooltipWithoutKeyboardShortcut + " (Alt+E)");
		eraseModeButton.addActionListener(modeListener);
		eraseModeButton.setMnemonic(KeyEvent.VK_E);
		eraseModeButton.setPreferredSize(new Dimension(50, eraseModeButton.getPreferredSize().height));

		replaceModeButton = new JToggleButton("<html><u>R</u>eplace</html>");
		replaceModeButton.setToolTipText(replaceTooltipWithoutKeyboardShortcut + " (Alt+R)");
		replaceModeButton.addActionListener(modeListener);
		replaceModeButton.setMnemonic(KeyEvent.VK_R);
		replaceModeButton.setPreferredSize(new Dimension(65, replaceModeButton.getPreferredSize().height));

		editModeButton = new JToggleButton("<html>Ed<u>i</u>t</html>");
		editModeButton.setToolTipText(editTooltipWithoutKeyboardShortcut + " (Alt+I)");
		editModeButton.addActionListener(modeListener);
		editModeButton.setMnemonic(KeyEvent.VK_I);
		editModeButton.setPreferredSize(new Dimension(65, editModeButton.getPreferredSize().height));
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String labelTooltip)
	{
		JPanel holder = new JPanel();
		holder.setLayout(new WrapLayout(WrapLayout.LEFT));
		// Remove the horizontal and vertical gaps from the border around the elements.
		holder.setBorder(BorderFactory.createEmptyBorder(-5, -5, -5, -5));
		holder.add(drawModeButton);
		holder.add(eraseModeButton);
		if (includeReplaceButton)
		{
			holder.add(replaceModeButton);
		}
		if (includeEditModeButton)
		{
			holder.add(editModeButton);
		}

		return organizer.addLabelAndComponent("Mode:", labelTooltip, holder);
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
