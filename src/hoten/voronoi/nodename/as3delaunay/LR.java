package hoten.voronoi.nodename.as3delaunay;

public final class LR {

    final public static LR LEFT = new LR( "left");
    final public static LR RIGHT = new LR( "right");
    private String _name;

    public LR(String name) {
        _name = name;
    }

    public static LR other(LR leftRight) {
        return leftRight == LEFT ? RIGHT : LEFT;
    }

    @Override
    public String toString() {
        return _name;
    }
}
