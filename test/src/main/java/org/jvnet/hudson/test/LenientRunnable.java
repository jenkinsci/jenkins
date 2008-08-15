package org.jvnet.hudson.test;

/**
 * Like {@link Runnable} but can throw any exception.
 *
 * @author Kohsuke Kawaguchi
 */
public interface LenientRunnable {
    public void run() throws Exception;
}
