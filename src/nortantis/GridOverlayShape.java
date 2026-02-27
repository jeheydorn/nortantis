package nortantis;

import nortantis.swing.translation.Translation;

public enum GridOverlayShape
{
	Horizontal_hexes, Vertical_hexes, Squares, Voronoi_polygons;

	public String toString()
	{
		return Translation.get("GridOverlayShape." + name());
	}
}
