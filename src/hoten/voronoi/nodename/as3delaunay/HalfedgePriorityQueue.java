package hoten.voronoi.nodename.as3delaunay;

import java.util.ArrayList;

import hoten.geom.Point;

public final class HalfedgePriorityQueue // also known as heap
{

    private ArrayList<Halfedge> _hash;
    private int _count;
    private int _minBucket;
    private int _hashsize;
    private double _ymin;
    private double _deltay;

    public HalfedgePriorityQueue(double ymin, double deltay, int sqrt_nsites) {
        _ymin = ymin;
        _deltay = deltay;
        _hashsize = 4 * sqrt_nsites;
        initialize();
    }

    public void dispose() {
        // get rid of dummies
        for (int i = 0; i < _hashsize; ++i) {
            _hash.get(i).dispose();
        }
        _hash.clear();
        _hash = null;
    }

    private void initialize() {
        int i;

        _count = 0;
        _minBucket = 0;
        _hash = new ArrayList<Halfedge>(_hashsize);
        // dummy Halfedge at the top of each hash
        for (i = 0; i < _hashsize; ++i) {
            _hash.add(Halfedge.createDummy());
            _hash.get(i).nextInPriorityQueue = null;
        }
    }

    public void insert(Halfedge halfEdge) {
        Halfedge previous, next;
        int insertionBucket = bucket(halfEdge);
        if (insertionBucket < _minBucket) {
            _minBucket = insertionBucket;
        }
        previous = _hash.get(insertionBucket);
        while ((next = previous.nextInPriorityQueue) != null
                && (halfEdge.ystar > next.ystar || (halfEdge.ystar == next.ystar && halfEdge.vertex.get_x() > next.vertex.get_x()))) {
            previous = next;
        }
        halfEdge.nextInPriorityQueue = previous.nextInPriorityQueue;
        previous.nextInPriorityQueue = halfEdge;
        ++_count;
    }

    public void remove(Halfedge halfEdge) {
        Halfedge previous;
        int removalBucket = bucket(halfEdge);

        if (halfEdge.vertex != null) {
            previous = _hash.get(removalBucket);
            while (previous.nextInPriorityQueue != halfEdge) {
                previous = previous.nextInPriorityQueue;
            }
            previous.nextInPriorityQueue = halfEdge.nextInPriorityQueue;
            _count--;
            halfEdge.vertex = null;
            halfEdge.nextInPriorityQueue = null;
            halfEdge.dispose();
        }
    }

    private int bucket(Halfedge halfEdge) {
        int theBucket = (int) ((halfEdge.ystar - _ymin) / _deltay * _hashsize);
        if (theBucket < 0) {
            theBucket = 0;
        }
        if (theBucket >= _hashsize) {
            theBucket = _hashsize - 1;
        }
        return theBucket;
    }

    private boolean isEmpty(int bucket) {
        return (_hash.get(bucket).nextInPriorityQueue == null);
    }

    /**
     * move _minBucket until it contains an actual Halfedge (not just the dummy
     * at the top);
     *
     */
    private void adjustMinBucket() {
        while (_minBucket < _hashsize - 1 && isEmpty(_minBucket)) {
            ++_minBucket;
        }
    }

    public boolean empty() {
        return _count == 0;
    }

    /**
     * @return coordinates of the Halfedge's vertex in V*, the transformed
     * Voronoi diagram
     *
     */
    public Point min() {
        adjustMinBucket();
        Halfedge answer = _hash.get(_minBucket).nextInPriorityQueue;
        return new Point(answer.vertex.get_x(), answer.ystar);
    }

    /**
     * remove and return the min Halfedge
     *
     * @return
     *
     */
    public Halfedge extractMin() {
        Halfedge answer;

        // get the first real Halfedge in _minBucket
        answer = _hash.get(_minBucket).nextInPriorityQueue;

        _hash.get(_minBucket).nextInPriorityQueue = answer.nextInPriorityQueue;
        _count--;
        answer.nextInPriorityQueue = null;

        return answer;
    }
}