package nortantis.platform;

public abstract class FontMetrics
{
	public abstract int getAscent();
	
	public abstract int getDescent();
	
	public abstract int stringWidth(String string);
}
