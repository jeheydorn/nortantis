package nortantis;

public enum GridOverlayShape
{
	Horizontal_hexes, Vertical_hexes, Squares, Voronoi_polygons;

	public String toString()
	{
		return name().replace("_", " ");
	}
}
