/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author dream
 */
class LineReader implements Iterable<String> {

    private static class LineIterator implements Iterator<String> {
        private final BufferedReader b;
        private boolean done = false;
        private String nextLine = null;

        public LineIterator(BufferedReader b) {
            this.b = b;
            queueLine();
        }

        final void queueLine() {
            try {
                nextLine = b.readLine();
            } catch (IOException ex) {
                done = true;
            }
            if(nextLine==null)
                done = true;
        }

        public boolean hasNext() {
            return !done;
        }

        public String next() {
            String line = nextLine;
            queueLine();
            return line;
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
    private final BufferedReader b;

    public LineReader(InputStream in) {
        this.b = new BufferedReader(
                new InputStreamReader(in, Configuration.charset));
    }

    public Iterator<String> iterator() {
        return new LineIterator(b);
    }

}
