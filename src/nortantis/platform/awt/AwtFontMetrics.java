package nortantis.platform.awt;

import java.util.Objects;

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

	@Override
	public int hashCode()
	{
		return Objects.hash(metrics);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		AwtFontMetrics other = (AwtFontMetrics) obj;
		return Objects.equals(metrics, other.metrics);
	}
}
