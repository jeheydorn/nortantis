package nortantis.swing;

import java.util.List;

import javax.swing.JPanel;

public class IconTypeButtons
{
	public RowHider hider;
	public List<RadioButtonWithImage> buttons;

	/**
	 * Optional. Used for replacing the radio buttons.
	 */
	public JPanel buttonsPanel;

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

	public boolean selectButtonIfPresent(String buttonText)
	{
		for (RadioButtonWithImage buttonWithImage : buttons)
		{
			if (buttonWithImage.getText().equals(buttonText))
			{
				buttonWithImage.getRadioButton().doClick();
				return true;
			}
		}

		return false;
	}
}
