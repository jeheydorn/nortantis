package nortantis.swing;

import java.util.List;

import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class IconTypeButtons
{
	public RowHider hider;
	public List<JRadioButton> buttons;
	
	/**
	 * Optional. Used for replacing the radio buttons.
	 */
	public JPanel buttonsPanel;
	
	public IconTypeButtons(RowHider hider, List<JRadioButton> buttons)
	{
		this.hider = hider;
		this.buttons = buttons;
	}
	
	public IconTypeButtons(RowHider hider, List<JRadioButton> buttons, JPanel buttonsPanel)
	{
		this.hider = hider;
		this.buttons = buttons;
		this.buttonsPanel = buttonsPanel;
	}
	
	public String getSelectedOption()
	{
		for (JRadioButton button : buttons)
		{
			if (button.isSelected())
			{
				return button.getText();
			}
		}
		return null;
	}
}
