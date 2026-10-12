package nortantis.swing;

import javax.swing.*;
import java.awt.*;

/**
 * A small drawing of a line, for showing what a choice in a combo box looks like. It is drawn in the text color of the component it is painted on, so
 * that it follows the theme and the selection highlight.
 */
public abstract class LinePreviewIcon implements Icon
{
	protected static final int width = 44;
	protected static final int height = 12;
	protected static final float strokeWidth = 1.5f;

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			Color disabledColor = UIManager.getColor("Label.disabledForeground");
			g2.setColor(c.isEnabled() || disabledColor == null ? c.getForeground() : disabledColor);
			paintLine(g2, x, y);
		}
		finally
		{
			g2.dispose();
		}
	}

	/**
	 * Draws the line within the icon's bounds, which start at (x, y). The color and rendering hints are already set.
	 */
	protected abstract void paintLine(Graphics2D g2, int x, int y);

	@Override
	public int getIconWidth()
	{
		return width;
	}

	@Override
	public int getIconHeight()
	{
		return height;
	}
}
