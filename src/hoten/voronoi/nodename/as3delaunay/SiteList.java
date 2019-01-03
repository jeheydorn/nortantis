package hoten.voronoi.nodename.as3delaunay;

import java.util.ArrayList;

import hoten.geom.Point;
import hoten.geom.Rectangle;

public final class SiteList implements IDisposable {

    private ArrayList<Site> _sites;
    private int _currentIndex;
    private boolean _sorted;

    public SiteList() {
        _sites = new ArrayList<Site>();
        _sorted = false;
    }

    @Override
    public void dispose() {
        if (_sites != null) {
            for (Site site : _sites) {
                site.dispose();
            }
            _sites.clear();
            _sites = null;
        }
    }

    public int push(Site site) {
        _sorted = false;
        _sites.add(site);
        return _sites.size();
    }

    public int get_length() {
        return _sites.size();
    }

    public Site next() {
        if (_sorted == false) {
            throw new Error("SiteList::next():  sites have not been sorted");
        }
        if (_currentIndex < _sites.size()) {
            return _sites.get(_currentIndex++);
        } else {
            return null;
        }
    }

    public Rectangle getSitesBounds() {
        if (_sorted == false) {
            Site.sortSites(_sites);
            _currentIndex = 0;
            _sorted = true;
        }
        double xmin, xmax, ymin, ymax;
        if (_sites.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }
        xmin = Double.MAX_VALUE;
        xmax = Double.MIN_VALUE;
        for (Site site : _sites) {
            if (site.get_x() < xmin) {
                xmin = site.get_x();
            }
            if (site.get_x() > xmax) {
                xmax = site.get_x();
            }
        }
        // here's where we assume that the sites have been sorted on y:
        ymin = _sites.get(0).get_y();
        ymax = _sites.get(_sites.size() - 1).get_y();

        return new Rectangle(xmin, ymin, xmax - xmin, ymax - ymin);
    }

    /*public ArrayList<Color> siteColors(referenceImage:BitmapData = null)
     {
     var colors:Vector.<uint> = new Vector.<uint>();
     for each (var site:Site in _sites)
     {
     colors.push(referenceImage ? referenceImage.getPixel(site.x, site.y) : site.color);
     }
     return colors;
     }*/
    public ArrayList<Point> siteCoords() {
        ArrayList<Point> coords = new ArrayList<Point>();
        for (Site site : _sites) {
            coords.add(site.get_coord());
        }
        return coords;
    }

    /**
     *
     * @return the largest circle centered at each site that fits in its region;
     * if the region is infinite, return a circle of radius 0.
     *
     */
    public ArrayList<Circle> circles() {
        ArrayList<Circle> circles = new ArrayList<Circle>();
        for (Site site : _sites) {
            double radius = 0;
            Edge nearestEdge = site.nearestEdge();

            //!nearestEdge.isPartOfConvexHull() && (radius = nearestEdge.sitesDistance() * 0.5);
            if (!nearestEdge.isPartOfConvexHull()) {
                radius = nearestEdge.sitesDistance() * 0.5;
            }
            circles.add(new Circle(site.get_x(), site.get_y(), radius));
        }
        return circles;
    }

    public ArrayList<ArrayList<Point>> regions(Rectangle plotBounds) {
        ArrayList<ArrayList<Point>> regions = new ArrayList<ArrayList<Point>>();
        for (Site site : _sites) {
            regions.add(site.region(plotBounds));
        }
        return regions;
    }
    /**
     *
     * @param proximityMap a BitmapData whose regions are filled with the site
     * index values; see PlanePointsCanvas::fillRegions()
     * @param x
     * @param y
     * @return coordinates of nearest Site to (x, y)
     *
     */
    /*public Point nearestSitePoint(proximityMap:BitmapData, double x, double y)
     {
     var index:uint = proximityMap.getPixel(x, y);
     if (index > _sites.length - 1)
     {
     return null;
     }
     return _sites[index].coord;
     }*/
}