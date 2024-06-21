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
}