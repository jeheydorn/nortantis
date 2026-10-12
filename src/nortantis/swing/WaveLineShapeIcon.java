package nortantis.swing;

import nortantis.MapSettings.WaveLineShape;

import java.awt.*;
import java.awt.geom.Path2D;

/**
 * A small drawing of a wave line shape.
 */
public class WaveLineShapeIcon extends LinePreviewIcon
{
	private static final double periodsShown = 2.5;

	private final WaveLineShape shape;

	public WaveLineShapeIcon(WaveLineShape shape)
	{
		this.shape = shape;
	}

	@Override
	protected void paintLine(Graphics2D g2, int x, int y)
	{
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
}
