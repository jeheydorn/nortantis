package nortantis.graph.voronoi.nodename.as3delaunay;

public final class Winding
{

	public static final Winding CLOCKWISE = new Winding("clockwise");
	public static final Winding COUNTERCLOCKWISE = new Winding("counterclockwise");
	public static final Winding NONE = new Winding("none");
	private String _name;

	private Winding(String name)
	{
		super();
		_name = name;
	}

	@Override
	public String toString()
	{
		return _name;
	}
}