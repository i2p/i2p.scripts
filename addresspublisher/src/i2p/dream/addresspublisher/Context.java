/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.util.Timer;

/**
 *
 * @author dream
 */
public class Context {
    final public Timer timer;
    final public KnownHosts knownHosts;
    Context(String name) {
        timer = new Timer(name+" Timer",true);
        knownHosts = new KnownHosts(timer);
    }
}
