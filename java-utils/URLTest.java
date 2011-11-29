package net.i2p.util;

import java.net.MalformedURLException;
import java.net.URL;


/**
 * Usage: URLTest URL
 *
 */
public class URLTest {
    public static void main(String args[]) {
        if (args.length != 1) {
            System.err.println("Usage: URLTest URL");
            System.exit(1);
        }
        URL url = null;
	try {
		url = new URL(args[0]);
	} catch (MalformedURLException mue) {
		System.err.println("Error: " + mue);
            System.exit(1);
	}
	System.err.println("Host: " + url.getHost());
	System.err.println("Port: " + url.getPort());
	System.err.println("File: " + url.getFile());
	System.err.println("Path: " + url.getPath());
	System.err.println("Proto: " + url.getProtocol());
	System.err.println("Query: " + url.getQuery());
	System.err.println("Ref: " + url.getRef());
	System.err.println("User: " + url.getUserInfo());
	System.err.println("Auth: " + url.getAuthority());
	System.err.println("ToString: " + url.toString());
    }
}
