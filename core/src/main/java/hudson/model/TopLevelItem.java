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
package hudson.model;

import hudson.Extension;
import hudson.ExtensionPoint;

/**
 * {@link Item} that can be directly displayed under {@link jenkins.model.Jenkins} or other containers.
 * (A "container" would be any {@link ItemGroup}{@code <TopLevelItem>}, such as a folder of projects.)
 * Ones that don't need to be under specific parent (say, unlike {@code MatrixConfiguration}),
 * and thus can be freely moved, copied, etc.
 *
 * <p>
 * To register a custom {@link TopLevelItem} class from a plugin, put {@link Extension} on your
 * {@link TopLevelItemDescriptor}. Also see {@link Items#XSTREAM}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface TopLevelItem extends Item, ExtensionPoint, Describable<TopLevelItem> {
    /**
     *
     * @see Describable#getDescriptor()
     */
    TopLevelItemDescriptor getDescriptor();
}
