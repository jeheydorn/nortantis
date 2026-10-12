package nortantis.swing;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;

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
		if (g instanceof Graphics2D g2 && isScaleOnlyTransform(g2.getTransform()))
		{
			paintInScreenPixels(g2, x, y, width, height);
			return;
		}
		g.fillRect(x, y, width, thickness);
		g.fillRect(x, y + height - thickness, width, thickness);
		g.fillRect(x, y + thickness, thickness, height - thickness * 2);
		g.fillRect(x + width - thickness, y + thickness, thickness, height - thickness * 2);
	}

	private static boolean isScaleOnlyTransform(AffineTransform transform)
	{
		return transform.getShearX() == 0 && transform.getShearY() == 0 && transform.getScaleX() > 0 && transform.getScaleY() > 0;
	}

	/**
	 * Draws the border with the same thickness in screen pixels on every side. At fractional display scales, a fill one component pixel
	 * thick covers a fractional number of screen pixels, which rounds to 1 or 2 screen pixels depending on where the component sits, so
	 * sides drawn that way can come out with different thicknesses.
	 */
	private void paintInScreenPixels(Graphics2D g, int x, int y, int width, int height)
	{
		AffineTransform transform = g.getTransform();
		double scaleX = transform.getScaleX();
		double scaleY = transform.getScaleY();
		// The screen pixels a non-antialiased fill of the component's rectangle covers: those whose centers lie inside it.
		int left = (int) Math.ceil(transform.getTranslateX() + x * scaleX - 0.5);
		int right = (int) Math.ceil(transform.getTranslateX() + (x + width) * scaleX - 0.5);
		int top = (int) Math.ceil(transform.getTranslateY() + y * scaleY - 0.5);
		int bottom = (int) Math.ceil(transform.getTranslateY() + (y + height) * scaleY - 0.5);
		int thicknessX = Math.max(1, (int) Math.round(thickness * scaleX));
		int thicknessY = Math.max(1, (int) Math.round(thickness * scaleY));

		Graphics2D screenGraphics = (Graphics2D) g.create();
		try
		{
			screenGraphics.setTransform(new AffineTransform());
			screenGraphics.fillRect(left, top, right - left, thicknessY);
			screenGraphics.fillRect(left, bottom - thicknessY, right - left, thicknessY);
			screenGraphics.fillRect(left, top + thicknessY, thicknessX, bottom - top - thicknessY * 2);
			screenGraphics.fillRect(right - thicknessX, top + thicknessY, thicknessX, bottom - top - thicknessY * 2);
		}
		finally
		{
			screenGraphics.dispose();
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
