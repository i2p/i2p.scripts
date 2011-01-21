/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package xpisigner;

import java.io.File;
import java.util.Iterator;
import java.util.Stack;

/**
 *
 * @author dream
 */
class FileWalkerIter implements Iterator<File> {

    boolean done = false;
    
    Stack<File> stack = new Stack<File>();

    FileWalkerIter(File src) {
        stack.push(src);
    }

    public boolean hasNext() {        
        return !stack.isEmpty();
    }

    public File next() {
        File f = stack.pop();
        if(f.isDirectory()) {
            for(File c : f.listFiles()) {                
                stack.push(c);
            }
        }
        return f;
    }

    public void remove() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}

class FileWalker implements Iterable<File> {
    private final File src;

    FileWalker(File src) {
        this.src = src;
    }

    public Iterator<File> iterator() {
        return new FileWalkerIter(src);
    }
}