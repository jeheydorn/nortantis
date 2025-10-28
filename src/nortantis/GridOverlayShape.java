package nortantis;

public enum GridOverlayShape
{
	Horizontal_hexes, Vertical_hexes, Squares;
	
	public String toString()
	{
		return name().replace("_", " ");
	}
}
