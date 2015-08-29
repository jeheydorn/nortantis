package hoten.geom;

/**
 * Rectangle.java
 *
 * @author Connor
 */
public class Rectangle {

    final public double x, y, width, height, right, bottom, left, top;

    public Rectangle(double x, double y, double width, double height) {
        left = this.x = x;
        top = this.y = y;
        this.width = width;
        this.height = height;
        right = x + width;
        bottom = y + height;
    }

    public boolean liesOnAxes(Point p) {
        return GenUtils.closeEnough(p.x, x, 1) || GenUtils.closeEnough(p.y, y, 1) || GenUtils.closeEnough(p.x, right, 1) || GenUtils.closeEnough(p.y, bottom, 1);
    }

    public boolean inBounds(Point p) {
        return inBounds(p.x, p.y);
    }

    public boolean inBounds(double x0, double y0) {
        if (x0 < x || x0 > right || y0 < y || y0 > bottom) {
            return false;
        }
        return true;
    }
}
