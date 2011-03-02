package i2p.dream.addresspublisher;

import java.util.Iterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author dream
 */
class StupidW3C implements Iterable<Node> {

    int position;
    final NodeList list;

    public StupidW3C(NodeList list) {
        this.list = list;
        this.position = 0;
    }

    public Iterator<Node> iterator() {
        return new Iterator<Node>() {
            int position = 0;

            public boolean hasNext() {
                return (position + 1) < list.getLength();
            }

            public Node next() {
                return list.item(position++);
            }

            public void remove() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
    }
}
