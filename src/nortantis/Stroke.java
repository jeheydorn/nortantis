package nortantis;

import java.io.Serializable;

@SuppressWarnings("serial")
public class Stroke implements Serializable
{
	public final StrokeType type;
	public final float width;

	public Stroke(StrokeType type, float width)
	{
		this.type = type;
		this.width = width;
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
		Stroke other = (Stroke) obj;
		return type == other.type && Float.floatToIntBits(width) == Float.floatToIntBits(other.width);
	}
}