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
package hudson;

import jenkins.model.Jenkins;

import java.net.SocketAddress;

/**
 * Extension point that contributes an XML fragment to the UDP broadcast.
 *
 * <p>
 * Put {@link Extension} on your implementation class to have it auto-discovered.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.304
 * @see UDPBroadcastThread
 */
public abstract class UDPBroadcastFragment implements ExtensionPoint {
    /**
     * Called to build up a response XML.
     *
     * @param buf
     *      This is the buffer to write XML to. The implementation of this method
     *      should write a complete fragment. Because of the packet length restriction
     *      in UDP (somewhere around 1500 bytes), you cannot send a large amount of information.
     * @param sender
     *      The socket address that sent the discovery packet out.
     */
    public abstract void buildFragment(StringBuilder buf, SocketAddress sender);

    /**
     * Returns all the registered {@link UDPBroadcastFragment}s.
     */
    public static ExtensionList<UDPBroadcastFragment> all() {
        return ExtensionList.lookup(UDPBroadcastFragment.class);
    }
}
