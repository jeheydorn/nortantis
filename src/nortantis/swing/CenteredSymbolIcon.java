package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;

/**
 * Draws a text symbol, such as an arrow, centered on the shape it actually draws rather than on the font's line height. Symbols often come
 * from a fallback font whose line metrics would leave them sitting off center if they were drawn as a button's text.
 */
public class CenteredSymbolIcon implements Icon
{
	private final String symbol;
	/**
	 * The symbol's font size, relative to the look and feel's button font.
	 */
	private final float sizeScale;

	public CenteredSymbolIcon(String symbol, float sizeScale)
	{
		this.symbol = symbol;
		this.sizeScale = sizeScale;
	}

	private Font getFont()
	{
		Font buttonFont = UIManager.getFont("Button.font");
		if (buttonFont == null)
		{
			buttonFont = new JButton().getFont();
		}
		return buttonFont.deriveFont(buttonFont.getSize2D() * sizeScale);
	}

	@Override
	public int getIconWidth()
	{
		return Math.round(getFont().getSize2D());
	}

	@Override
	public int getIconHeight()
	{
		return getIconWidth();
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			Color disabledColor = UIManager.getColor("Button.disabledText");
			g2.setColor(c.isEnabled() || disabledColor == null ? c.getForeground() : disabledColor);
			// TextLayout, unlike a glyph vector, falls back to another font for symbols the button font lacks.
			TextLayout layout = new TextLayout(symbol, getFont(), g2.getFontRenderContext());
			Rectangle2D bounds = layout.getBounds();
			float drawX = (float) (x + (getIconWidth() - bounds.getWidth()) / 2.0 - bounds.getX());
			float drawY = (float) (y + (getIconHeight() - bounds.getHeight()) / 2.0 - bounds.getY());
			layout.draw(g2, drawX, drawY);
		}
		finally
		{
			g2.dispose();
		}
	}
}
