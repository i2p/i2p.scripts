package updatemonotone;

import java.util.Iterator;

class RangeIterator implements Iterator<Integer> {
    final int bottom;
    final int top;
    final int span;
    final int randthing;
    int offset;

    public RangeIterator(int bottom, int top) {
        this.bottom = bottom;
        this.top = top;
        this.span = top - bottom;
        // XXX: This is a horrid hack. Ranges do not work this way what are u doin
        this.randthing = (int) (Math.random() * span);
        offset = 0;
    }

    public boolean hasNext() {
        return offset<span;
    }

    public Integer next() {
        return bottom+((offset++)+randthing)%span;
    }

    public void remove() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

public class Range implements Iterable<Integer> {
    private final int top;
    private final int bottom;
    public Range(int bottom, int top) {
        this.bottom = bottom;
        this.top = top;
    }

    public Iterator<Integer> iterator() {
        return new RangeIterator(bottom,top);
    }

}