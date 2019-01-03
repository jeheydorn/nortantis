package nortantis;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * For handling when the user cancels their selection of a background color.
 */
public class BGColorCancelHandler implements ActionListener
{
	private Color orginal;
	private BGColorPreviewPanel target;

	public BGColorCancelHandler(Color original, BGColorPreviewPanel target)
	{
		this.orginal = original;
		this.target = target;
	}
	
	@Override
	public void actionPerformed(ActionEvent arg0)
	{
		target.setColor(orginal);
	}
	
}
