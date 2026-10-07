package nortantis.swing;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;

@SuppressWarnings("serial")
public class DynamicLineBorder extends AbstractBorder
{
	private int thickness;
	private String uiKeyColor;

	public DynamicLineBorder(String uiKeyColor, int thickness)
	{
		this.uiKeyColor = uiKeyColor;
		this.thickness = thickness;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
	{
		Color color = UIManager.getColor(uiKeyColor);
		if (color == null)
		{
			color = Color.BLACK; // Fallback color
		}
		g.setColor(color);
		// Drawn with fills rather than lines because at fractional display scales, a line can land up to half a screen pixel away from a
		// fill of the same rectangle, which leaves a line of whatever the component filled its edge with showing beside the border.
		g.fillRect(x, y, width, thickness);
		g.fillRect(x, y + height - thickness, width, thickness);
		g.fillRect(x, y + thickness, thickness, height - thickness * 2);
		g.fillRect(x + width - thickness, y + thickness, thickness, height - thickness * 2);
	}

	@Override
	public Insets getBorderInsets(Component c, Insets insets)
	{
		insets.set(thickness, thickness, thickness, thickness);
		return insets;
	}

	@Override
	public Insets getBorderInsets(Component c)
	{
		return new Insets(thickness, thickness, thickness, thickness);
	}

	@Override
	public boolean isBorderOpaque()
	{
		return true;
	}
}
