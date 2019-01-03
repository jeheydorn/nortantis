package hoten.geom;

/**
 * GenUtil.java
 *
 * @author Connor
 */
public class GenUtils {

    public static boolean closeEnough(double d1, double d2, double diff) {
        return Math.abs(d1 - d2) <= diff;
    }
}
