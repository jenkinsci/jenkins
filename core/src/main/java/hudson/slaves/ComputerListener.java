/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.slaves;

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.ExtensionPoint;
import hudson.Extension;

/**
 * Receives notifications about status changes of {@link Computer}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.246
 */
public abstract class ComputerListener implements ExtensionPoint {
    /**
     * Called right after a {@link Computer} comes online.
     */
    public void onOnline(Computer c) {}

    /**
     * Called right after a {@link Computer} went offline.
     */
    public void onOffline(Computer c) {}

    /**
     * Registers this {@link ComputerListener} so that it will start receiving events.
     *
     * @deprecated as of 1.286
     *      put {@link Extension} on your class to have it auto-registered.
     */
    public final void register() {
        Hudson.getInstance().getExtensionList(ComputerListener.class).add(this);
    }

    /**
     * Unregisters this {@link ComputerListener} so that it will never receive further events.
     *
     * <p>
     * Unless {@link ComputerListener} is unregistered, it will never be a subject of GC.
     */
    public final boolean unregister() {
        return Hudson.getInstance().getExtensionList(ComputerListener.class).remove(this);
    }
}
