/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.main;

import hudson.model.listeners.ItemListener;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class JenkinsRuleTimeoutTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    {
        r.timeout = 10; // let us not wait three minutes!
    }

    @Test
    public void hangInterruptiblyInTest() throws Exception {
        try {
            hangInterruptibly();
        } catch (InterruptedException x) {
            System.err.println("Interrupted, good.");
        }
    }

    @Ignore("TODO ought to use the idiom from org.junit.internal.runners.statements.FailOnTimeout: " +
            "run test in its own thread so we can fail even if it does not respond to interruptions")
    @Test
    public void hangUninterruptiblyInTest() throws Exception {
        hangUninterruptibly();
    }

    @Test
    public void hangInterruptiblyInShutdown() throws Exception {
        System.err.println("Test itself passed…");
    }
    @TestExtension("hangInterruptiblyInShutdown")
    public static class HangsInterruptibly extends ItemListener {
        @Override
        public void onBeforeShutdown() {
            try {
                hangInterruptibly();
            } catch (InterruptedException x) {
                System.err.println("Interrupted, good.");
            }
        }

    }

    @Ignore("TODO ditto")
    @Test
    public void hangUninterruptiblyInShutdown() throws Exception {
        System.err.println("Test itself passed…");
    }
    @TestExtension("hangUninterruptiblyInShutdown")
    public static class HangsUninterruptibly extends ItemListener {
        @Override
        public void onBeforeShutdown() {
            hangUninterruptibly();
        }

    }

    private static void hangInterruptibly() throws InterruptedException {
        Thread.sleep(Long.MAX_VALUE);
    }

    @SuppressWarnings("SleepWhileHoldingLock")
    private static void hangUninterruptibly() {
        // Adapted from http://stackoverflow.com/a/22489064/12916
        final Object a = new Object();
        final Object b = new Object();
        new Thread("other") {
            @Override
            public void run() {
                synchronized (a) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException exc) {
                        System.err.println("interrupted t1");
                    }
                    synchronized (b) {
                        assert false : "should not get here";
                    }
                }
            }
        }.start();
        synchronized (b) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException exc) {
                System.err.println("interrupted t2");
            }
            synchronized (a) {
                assert false : "should not get here";
            }
        }
    }

}
