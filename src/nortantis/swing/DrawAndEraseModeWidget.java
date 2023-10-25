package nortantis.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;

import javax.swing.JToggleButton;

public class DrawAndEraseModeWidget
{
	private JToggleButton drawModeButton;
	private JToggleButton eraseModeButton;

	public DrawAndEraseModeWidget(String drawTooltipWithoutKeyboardShortcut, String eraseTooltipWithoutKeyboardShortcut, Runnable changeListener)
	{
		ActionListener modeListener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (e != null && e.getSource() == eraseModeButton)
				{
					drawModeButton.setSelected(false);
					if (!eraseModeButton.isSelected())
					{
						eraseModeButton.setSelected(true);
					}
					eraseModeButton.grabFocus();
				}
				else
				{
					// Draw button
					eraseModeButton.setSelected(false);
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
		eraseModeButton = new JToggleButton("<html><u>E</u>rase</html>");
		eraseModeButton.setToolTipText(eraseTooltipWithoutKeyboardShortcut + " (Alt+E)");
		eraseModeButton.addActionListener(modeListener);
		eraseModeButton.setMnemonic(KeyEvent.VK_E);

	}
	
	public RowHider addToOrganizer(GridBagOrganizer organizer, String labelTooltip)
	{
		return organizer.addLabelAndComponentsHorizontal("Mode:", labelTooltip,
				Arrays.asList(drawModeButton, eraseModeButton), 0, 5);

	}
	
	public boolean isDrawMode()
	{
		return drawModeButton.isSelected();
	}
	
	public boolean isEraseMode()
	{
		return eraseModeButton.isSelected();
	}
}
