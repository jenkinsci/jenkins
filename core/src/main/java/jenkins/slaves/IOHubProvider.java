/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package jenkins.slaves;

import hudson.Extension;
import hudson.init.Terminator;
import hudson.model.Computer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.remoting.protocol.IOHub;

/**
 * Singleton holder of {@link IOHub}
 *
 * @since FIXME
 */
@Extension
public class IOHubProvider {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(IOHubProvider.class.getName());
    /**
     * Our hub.
     */
    private IOHub hub;

    public IOHubProvider() {
        try {
            hub = IOHub.create(Computer.threadPoolForRemoting);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to launch IOHub", e);
            this.hub = null;
        }
    }

    public IOHub getHub() {
        return hub;
    }

    @Terminator
    public void cleanUp() throws IOException {
        if (hub != null) {
            hub.close();
            hub = null;
        }
    }

}
