package net.i2p.util;

import java.util.Map;
import java.util.Properties;

/**
 * Usage: PropDump
 *
 */
public class PropDump {
    public static void main(String args[]) {
	Properties p = new OrderedProperties();
	p.putAll(System.getProperties());
	for (Map.Entry e : p.entrySet()) {
		System.out.println(e.getKey() + "=" + e.getValue());
	}
    }
}
