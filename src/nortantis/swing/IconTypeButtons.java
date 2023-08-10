package nortantis.swing;

import java.util.List;

import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class IconTypeButtons
{
	public RowHider hider;
	public List<JRadioButton> buttons;
	
	public IconTypeButtons(RowHider hider, List<JRadioButton> buttons)
	{
		this.hider = hider;
		this.buttons = buttons;
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
