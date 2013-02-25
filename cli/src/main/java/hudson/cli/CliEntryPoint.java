/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.cli;

import hudson.remoting.Pipe;

import java.io.OutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Remotable interface for CLI entry point on the server side.
 *
 * @author Kohsuke Kawaguchi
 */
public interface CliEntryPoint {
    /**
     * Just like the static main method.
     *
     * @param locale
     *      Locale of this client.
     */
    int main(List<String> args, Locale locale, InputStream stdin, OutputStream stdout, OutputStream stderr);

    /**
     * Does the named command exist?
     */
    boolean hasCommand(String name);

    /**
     * Returns {@link #VERSION}, so that the client and the server can detect version incompatibility
     * gracefully.
     */
    int protocolVersion();

    /**
     * Initiates authentication out of band.
     * <p>
     * This method starts two-way byte channel that allows the client and the server to perform authentication.
     * The current supported implementation is based on SSH public key authentication that mutually authenticates
     * clients and servers.
     *
     * @param protocol
     *      Currently only "ssh" is supported.
     * @throws UnsupportedOperationException
     *      If the specified protocol is not supported by the server.
     */
    void authenticate(String protocol, Pipe c2s, Pipe s2c);

    int VERSION = 1;
}
