/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.model;

import hudson.util.DescriptorList;
import hudson.Extension;

import java.util.List;

/**
 * List of all installed {@link Job} types.
 * 
 * @author Kohsuke Kawaguchi
 * @deprecated since 1.281
 */
@Deprecated
public class Jobs {
    /**
     * List of all installed {@link JobPropertyDescriptor} types.
     *
     * <p>
     * Plugins can add their {@link JobPropertyDescriptor}s to this list.
     *
     * @see JobPropertyDescriptor#getPropertyDescriptors(Class)
     *
     * @deprecated as of 1.281
     *      Use {@link JobPropertyDescriptor#all()} for read access,
     *      and {@link Extension} for registration.
     */
    @Deprecated
    public static final List<JobPropertyDescriptor> PROPERTIES = (List)
            new DescriptorList<JobProperty<?>>((Class)JobProperty.class);
}
