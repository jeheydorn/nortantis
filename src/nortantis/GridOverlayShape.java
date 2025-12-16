package nortantis;

public enum GridOverlayShape
{
	Horizontal_hexes, Vertical_hexes, Squares, Voronoi_polygons_on_land;

	public String toString()
	{
		return name().replace("_", " ");
	}
}
