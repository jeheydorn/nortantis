package nortantis.swing;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;

import javax.swing.JToggleButton;

public class DrawAndEraseModeWidget
{
	private JToggleButton drawModeButton;
	private JToggleButton eraseModeButton;
	private JToggleButton replaceModeButton;
	private boolean includeReplaceButton;

	public DrawAndEraseModeWidget(String drawTooltipWithoutKeyboardShortcut, String eraseTooltipWithoutKeyboardShortcut,
			String replaceTooltipWithoutKeyboardShortcut, boolean includeReplaceButton, Runnable changeListener)
	{
		this.includeReplaceButton = includeReplaceButton;
		
		ActionListener modeListener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (e != null && e.getSource() == eraseModeButton)
				{
					drawModeButton.setSelected(false);
					replaceModeButton.setSelected(false);
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
					if (!replaceModeButton.isSelected())
					{
						replaceModeButton.setSelected(true);
					}
					replaceModeButton.grabFocus();
				}
				else
				{
					// Draw button
					eraseModeButton.setSelected(false);
					replaceModeButton.setSelected(false);
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
		replaceModeButton.setPreferredSize(new Dimension(50, replaceModeButton.getPreferredSize().height));
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String labelTooltip)
	{
		if (includeReplaceButton)
		{
			return organizer.addLabelAndComponentsHorizontal("Mode:", labelTooltip, Arrays.asList(drawModeButton, eraseModeButton, replaceModeButton), 0, 5);
		}
		else
		{
			return organizer.addLabelAndComponentsHorizontal("Mode:", labelTooltip, Arrays.asList(drawModeButton, eraseModeButton), 0, 5);
		}

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
}
