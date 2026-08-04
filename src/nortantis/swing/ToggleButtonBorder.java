package nortantis.swing;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * The border around the image in a toggle button that shows an image, drawing a highlight when the button is selected. The innermost pixel
 * of the highlight can be given its own color, separating the highlight from the image.
 * <p>
 * The highlight is painted in device pixels rather than in the border's own coordinates so that it is one pixel thick on every side. A
 * border painted in the border's coordinates lands between device pixels whenever the display scale makes its width fractional, which
 * leaves each side to round on its own.
 */
@SuppressWarnings("serial")
public class ToggleButtonBorder extends AbstractBorder
{
	/**
	 * The width of the border, before display scaling. This covers the highlight and the separator together.
	 */
	public static final int borderWidth = 4;

	/**
	 * The thickness of the separator between the highlight and the image, in device pixels. It is in device pixels rather than scaled with
	 * the display so that it stays a thin line rather than growing into a band on a scaled display.
	 */
	public static final int separatorThickness = 1;

	private final Color highlightColor;
	private final Color separatorColor;

	/**
	 * @param highlightColor
	 *            The color of the highlight, or null to leave the border blank.
	 * @param separatorColor
	 *            The color of the single pixel between the highlight and the image, or null to fill the whole border with the highlight.
	 */
	public ToggleButtonBorder(Color highlightColor, Color separatorColor)
	{
		this.highlightColor = highlightColor;
		this.separatorColor = separatorColor;
	}

	@Override
	public Insets getBorderInsets(Component c)
	{
		return new Insets(borderWidth, borderWidth, borderWidth, borderWidth);
	}

	@Override
	public Insets getBorderInsets(Component c, Insets insets)
	{
		insets.set(borderWidth, borderWidth, borderWidth, borderWidth);
		return insets;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
	{
		if (highlightColor == null)
		{
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			AffineTransform transform = g2.getTransform();
			double scale = transform.getScaleX();

			// Round each edge to a device pixel separately rather than rounding the width and height. A button whose size in device pixels
			// is not a whole number would otherwise end up a pixel wider than it is drawn, putting its right and bottom sides out of step
			// with its left and top.
			int left = (int) Math.round(transform.getTranslateX() + (x * scale));
			int top = (int) Math.round(transform.getTranslateY() + (y * scale));
			int right = (int) Math.round(transform.getTranslateX() + ((x + width) * scale));
			int bottom = (int) Math.round(transform.getTranslateY() + ((y + height) * scale));

			// Undo the scaling Java adds because of Windows Display settings scaling apps, so that the edges land on whole pixels.
			g2.setTransform(new AffineTransform());

			int scaledBorderWidth = (int) Math.round(borderWidth * scale);
			int highlightThickness = separatorColor == null ? scaledBorderWidth : scaledBorderWidth - separatorThickness;

			g2.setColor(highlightColor);
			fillRing(g2, left, top, right - left, bottom - top, highlightThickness);

			if (separatorColor != null)
			{
				g2.setColor(separatorColor);
				fillRing(g2, left + highlightThickness, top + highlightThickness, (right - left) - (highlightThickness * 2),
						(bottom - top) - (highlightThickness * 2), separatorThickness);
			}
		}
		finally
		{
			g2.dispose();
		}
	}

	private static void fillRing(Graphics2D g2, int x, int y, int width, int height, int thickness)
	{
		if (thickness <= 0 || width <= thickness * 2 || height <= thickness * 2)
		{
			return;
		}

		g2.fillRect(x, y, width, thickness);
		g2.fillRect(x, y + height - thickness, width, thickness);
		g2.fillRect(x, y + thickness, thickness, height - (thickness * 2));
		g2.fillRect(x + width - thickness, y + thickness, thickness, height - (thickness * 2));
	}
}
