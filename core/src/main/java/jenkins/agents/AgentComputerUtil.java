/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package jenkins.agents;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

public final class AgentComputerUtil {
    private AgentComputerUtil() {
    }

    /**
     * Obtains a {@link VirtualChannel} that allows some computation to be performed on the master.
     * This method can be called from any thread on the master, or from agent (more precisely,
     * it only works from the remoting request-handling thread in agents, which means if you've started
     * separate thread on agents, that'll fail.)
     *
     * @return null if the calling thread doesn't have any trace of where its master is.
     * @since 2.235
     */
    @CheckForNull
    public static VirtualChannel getChannelToMaster() {
        if (Jenkins.getInstanceOrNull() != null) {// check if calling thread is on master or on agent
            return FilePath.localChannel;
        }

        // if this method is called from within the agent computation thread, this should work
        Channel c = Channel.current();
        if (c != null && Boolean.TRUE.equals(c.getProperty("slave"))) {
            return c;
        }

        return null;
    }
}
