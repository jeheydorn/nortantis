package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

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
		for (int i = 0; i < thickness; i++)
		{
			g.drawRect(x + i, y + i, width - 1 - i - i, height - 1 - i - i);
		}
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
