package hoten.voronoi.nodename.as3delaunay;

import java.util.ArrayList;

public final class Triangle {

    private ArrayList<Site> _sites;

    public ArrayList<Site> get_sites() {
        return _sites;
    }

    public Triangle(Site a, Site b, Site c) {
        _sites = new ArrayList();
        _sites.add(a);
        _sites.add(b);
        _sites.add(c);
    }

    public void dispose() {
        _sites.clear();
        _sites = null;
    }
}