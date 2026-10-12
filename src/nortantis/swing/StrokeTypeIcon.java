package nortantis.swing;

import nortantis.StrokeType;
import nortantis.platform.awt.AwtBridge;

import java.awt.*;
import java.awt.geom.Line2D;

/**
 * A small drawing of a straight line in a stroke type's pattern, using the same dash pattern the map draws with.
 */
public class StrokeTypeIcon extends LinePreviewIcon
{
	private final StrokeType type;

	public StrokeTypeIcon(StrokeType type)
	{
		this.type = type;
	}

	@Override
	protected void paintLine(Graphics2D g2, int x, int y)
	{
		BasicStroke stroke = AwtBridge.toAwtStroke(new nortantis.Stroke(type, strokeWidth), 1.0);
		g2.setStroke(stroke);
		// Dots are drawn wider than the stroke width, so the inset comes from the stroke to keep their round caps inside the icon.
		double inset = stroke.getLineWidth();
		double length = width - 2 * inset;
		float[] dashArray = stroke.getDashArray();
		if (dashArray != null)
		{
			// Shorten the line to end on a whole dash, so the last one isn't cut off.
			double dash = dashArray[0];
			double gap = dashArray[1];
			length = Math.floor((length + gap) / (dash + gap)) * (dash + gap) - gap;
		}
		double startX = x + (width - length) / 2.0;
		double centerY = y + height / 2.0;
		// The tiny overshoot keeps the last dot, whose zero-length dash lands exactly on the end of the line, from being dropped.
		final double overshoot = 0.01;
		g2.draw(new Line2D.Double(startX, centerY, startX + length + overshoot, centerY));
	}
}
