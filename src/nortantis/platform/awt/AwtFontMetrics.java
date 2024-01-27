package nortantis.platform.awt;

import nortantis.platform.FontMetrics;

public class AwtFontMetrics extends FontMetrics
{
	java.awt.FontMetrics metrics;
	
	public AwtFontMetrics(java.awt.FontMetrics metrics)
	{
		this.metrics = metrics;
	}

	@Override
	public int getAscent()
	{
		return metrics.getAscent();
	}

	@Override
	public int getDescent()
	{
		return metrics.getDescent();
	}

	@Override
	public int stringWidth(String string)
	{
		return metrics.stringWidth(string);
	}
}
