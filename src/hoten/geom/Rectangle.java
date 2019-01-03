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

    public boolean liesOnAxes(Point p, double closeEnoughDistance) {
        return GenUtils.closeEnough(p.x, x, closeEnoughDistance) || GenUtils.closeEnough(p.y, y, closeEnoughDistance) || GenUtils.closeEnough(p.x, right, closeEnoughDistance) || GenUtils.closeEnough(p.y, bottom, closeEnoughDistance);
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
