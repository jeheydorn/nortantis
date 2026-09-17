package nortantis.swing;

import nortantis.MapSettings.WaveLineShape;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * A small drawing of a wave line shape, in the text color of the component it is painted on, so that it follows the theme and the selection
 * highlight.
 */
public class WaveLineShapeIcon implements Icon
{
	private static final int width = 44;
	private static final int height = 12;
	private static final double periodsShown = 2.5;
	private static final float strokeWidth = 1.5f;

	private final WaveLineShape shape;

	public WaveLineShapeIcon(WaveLineShape shape)
	{
		this.shape = shape;
	}

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
			g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			double inset = strokeWidth;
			double drawWidth = width - 2 * inset;
			double drawHeight = height - 2 * inset;
			Path2D.Double path = new Path2D.Double();
			// Every period starts on a sample, so the scallops' cusps stay sharp.
			int sampleCount = 200;
			for (int i = 0; i <= sampleCount; i++)
			{
				double fraction = (double) i / sampleCount;
				double phase = fraction * periodsShown;
				phase -= Math.floor(phase);
				double pointX = x + inset + fraction * drawWidth;
				double pointY = y + inset + drawHeight * (1.0 - shape.evaluate(phase));
				if (i == 0)
				{
					path.moveTo(pointX, pointY);
				}
				else
				{
					path.lineTo(pointX, pointY);
				}
			}
			g2.draw(path);
		}
		finally
		{
			g2.dispose();
		}
	}

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
