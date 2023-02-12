package net.i2p.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.i2p.I2PAppContext;

/**
 * Count how often we were called from particular places.
 *
 * @author zzz
 */
public class TraceCounter {
    private final ObjectCounter<WrappedException> counter;
    private final String name;
    private final long CLEAN_TIME;
    private static final Log _log = I2PAppContext.getGlobalContext().logManager().getLog(TraceCounter.class);

    /**
     *  @param name just used in the log
     *  @param cleanTime how often to dump out the count and reset the counters
     */
    public TraceCounter(String name, long cleanTime) {
        this.counter = new ObjectCounter<WrappedException>();
        this.name = name;
        this.CLEAN_TIME = cleanTime;
        SimpleTimer2.getInstance().addPeriodicEvent(new Cleaner(), CLEAN_TIME);
    }

    public void trace(Exception e) {
        this.counter.increment(new WrappedException(e));
    }

    private class Cleaner implements SimpleTimer.TimedEvent {
        public void timeReached() {
           if (_log.shouldInfo()) {
               List<WrappedException> traces = new ArrayList<WrappedException>();
               traces.addAll(counter.objects());
               Collections.sort(traces, new WrappedExceptionComparator());
               // dont intermix the logging from multiple TraceCounters
               synchronized(TraceCounter.class) {
                   _log.info("=======================================================");
                   _log.info("Total location count for " + name + ": " + traces.size());
                   ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
                   int total = 0;
                   for (WrappedException stea : traces) {
                       baos.reset();
                       PrintStream ps = new PrintStream(baos);
                       stea.ex.printStackTrace(ps);
                       ps.close();
                       int count = counter.count(stea);
                       _log.info(name + " Count: " + count + ' ' + baos.toString());
                       total += count;
                   }
                   _log.info("Total event count for " + name + ": " + total);
                   _log.info("=======================================================");
               }
           }
           TraceCounter.this.counter.clear();
        }
    }

    private class WrappedExceptionComparator implements Comparator<WrappedException> {
        public int compare(WrappedException l, WrappedException r) {
            // reverse
            return counter.count(r) - counter.count(l);
        }
    }

    private static class WrappedException {
        public final Exception ex;

        WrappedException(Exception e) {
            ex = e;
        }

        @Override
        public boolean equals(Object o) {
            return Arrays.equals(ex.getStackTrace(), ((WrappedException)o).ex.getStackTrace());
        }
    
        @Override
        public int hashCode() {
            StackTraceElement[] stea = ex.getStackTrace();
            int rv = 0;
            for (int i = 0; i < stea.length; i++) {
                rv += 777777 * (i + 1) * stea[i].getLineNumber(); 
            }
            return rv; 
        }
    }
}
