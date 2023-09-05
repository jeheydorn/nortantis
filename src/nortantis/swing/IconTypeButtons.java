package nortantis.swing;

import java.util.List;

import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class IconTypeButtons
{
	public RowHider hider;
	public List<RadioButtonWithImage> buttons;
	
	/**
	 * Optional. Used for replacing the radio buttons.
	 */
	public JPanel buttonsPanel;
	
	public IconTypeButtons(RowHider hider, List<RadioButtonWithImage> buttons)
	{
		this.hider = hider;
		this.buttons = buttons;
	}
	
	public IconTypeButtons(RowHider hider, List<RadioButtonWithImage> buttons, JPanel buttonsPanel)
	{
		this.hider = hider;
		this.buttons = buttons;
		this.buttonsPanel = buttonsPanel;
	}
	
	public String getSelectedOption()
	{
		for (RadioButtonWithImage button : buttons)
		{
			if (button.getRadioButton().isSelected())
			{
				return button.getText();
			}
		}
		return null;
	}
}
