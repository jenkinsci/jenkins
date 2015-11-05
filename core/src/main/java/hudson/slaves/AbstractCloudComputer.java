/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

import java.io.IOException;
import javax.annotation.CheckForNull;

/**
 * Partial implementation of {@link Computer} to be used in conjunction with
 * {@link AbstractCloudSlave}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.382
 */
public class AbstractCloudComputer<T extends AbstractCloudSlave> extends SlaveComputer {
    public AbstractCloudComputer(T slave) {
        super(slave);
    }

    @CheckForNull
    @Override
    public T getNode() {
        return (T) super.getNode();
    }

    /**
     * When the slave is deleted, free the node right away.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(DELETE);
        try {
            T node = getNode();
            if (node != null) { // No need to terminate nodes again
                node.terminate();
            }
            return new HttpRedirect("..");
        } catch (InterruptedException e) {
            return HttpResponses.error(500,e);
        }
    }
}
