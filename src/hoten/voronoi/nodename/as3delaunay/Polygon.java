package hoten.voronoi.nodename.as3delaunay;

import java.util.ArrayList;

import hoten.geom.Point;

public final class Polygon {

    private ArrayList<Point> _vertices;

    public Polygon(ArrayList<Point> vertices) {
        _vertices = vertices;
    }

    public double area() {
        return Math.abs(signedDoubleArea() * 0.5);
    }

    public Winding winding() {
        double signedDoubleArea = signedDoubleArea();
        if (signedDoubleArea < 0) {
            return Winding.CLOCKWISE;
        }
        if (signedDoubleArea > 0) {
            return Winding.COUNTERCLOCKWISE;
        }
        return Winding.NONE;
    }

    private double signedDoubleArea() {
        int index, nextIndex;
        int n = _vertices.size();
        Point point, next;
        double signedDoubleArea = 0;
        for (index = 0; index < n; ++index) {
            nextIndex = (index + 1) % n;
            point = _vertices.get(index);
            next = _vertices.get(nextIndex);
            signedDoubleArea += point.x * next.y - next.x * point.y;
        }
        return signedDoubleArea;
    }
}