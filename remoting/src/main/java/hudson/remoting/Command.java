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
package hudson.remoting;

import java.io.Serializable;

/**
 * One-way command to be sent over to the remote system and executed there.
 * This is layer 0, the lower most layer.
 *
 * <p>
 * At this level, remoting of class files are not provided, so both {@link Channel}s
 * need to have the definition of {@link Command}-implementation.
 * 
 * @author Kohsuke Kawaguchi
 */
abstract class Command implements Serializable {
    /**
     * This exception captures the stack trace of where the Command object is created.
     * This is useful for diagnosing the error when command fails to execute on the remote peer. 
     */
    public final Exception createdAt;


    protected Command() {
        this(true);
    }

    /**
     * @param recordCreatedAt
     *      If false, skip the recording of where the command is created. This makes the trouble-shooting
     *      and cause/effect correlation hard in case of a failure, but it will reduce the amount of the data
     *      transferred.
     */
    protected Command(boolean recordCreatedAt) {
        if(recordCreatedAt)
            this.createdAt = new Source();
        else
            this.createdAt = null;
    }

    /**
     * Called on a remote system to perform this command.
     *
     * @param channel
     *      The {@link Channel} of the remote system.
     */
    protected abstract void execute(Channel channel);

    private static final long serialVersionUID = 1L;

    private final class Source extends Exception {
        public Source() {
        }

        public String toString() {
            return "Command "+Command.this.toString()+" created at";
        }

        private static final long serialVersionUID = 1L;
    }
}
