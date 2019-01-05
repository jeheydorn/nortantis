package nortantis.editor;

import java.util.List;

import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class IconTypeButtons
{
	public JPanel panel;
	public List<JRadioButton> buttons;
	
	public IconTypeButtons(JPanel panel, List<JRadioButton> buttons)
	{
		this.panel = panel;
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
