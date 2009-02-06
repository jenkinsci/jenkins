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
package hudson.tasks.junit;

import hudson.model.AbstractBuild;
import hudson.model.ModelObject;
import hudson.model.Api;
import hudson.Util;

import java.io.Serializable;

import org.kohsuke.stapler.export.ExportedBean;

/**
 * Base class for all test result objects.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class TestObject implements ModelObject, Serializable {
    public abstract AbstractBuild<?,?> getOwner();

    /**
     * Gets the counter part of this {@link TestObject} in the previous run.
     *
     * @return null
     *      if no such counter part exists.
     */
    public abstract TestObject getPreviousResult();

    /**
     * Time took to run this test. In seconds.
     */
    public abstract float getDuration();

    /**
     * Returns the string representation of the {@link #getDuration()},
     * in a human readable format.
     */
    public String getDurationString() {
        return Util.getTimeSpanString((long)(getDuration()*1000));
    }

    /**
     * Exposes this object through the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Replaces URL-unsafe characters.
     */
    protected static String safe(String s) {
        // 3 replace calls is still 2-3x faster than a regex replaceAll
        return s.replace('/','_').replace('\\', '_').replace(':','_');
    }

    private static final long serialVersionUID = 1L;
}
