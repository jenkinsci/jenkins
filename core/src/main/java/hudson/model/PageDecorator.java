/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
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

import hudson.ExtensionPoint;
import hudson.Plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Participates in the rendering of HTML pages for all pages of Hudson.
 *
 * <p>
 * This class provides a few hooks to augument the HTML generation process of Hudson, across
 * all the HTML pages that Hudson delivers.
 *
 * <p>
 * For example, if you'd like to add a Google Analytics stat to Hudson, then you need to inject
 * a small script fragment to all Hudson pages. This extension point provides a means to do that.
 *
 * <h2>Life-cycle</h2>
 * <p>
 * Instances of this class is singleton. {@link Plugin}s that contribute this extension point
 * should instantiate a new decorator and add it to the {@link #ALL} list in {@link Plugin#start()}. 
 *
 * <h2>Associated Views</h2>
 * <h4>global.jelly</h4>
 * <p>
 * If this extension point needs to expose a global configuration, write this jelly page.
 * See {@link Descriptor} for more about this. Optional.
 *
 * <h4>footer.jelly</h4>
 * <p>
 * This page is added right before the &lt;/body> tag. Convenient place for adding tracking beacons, etc.
 *
 * <h4>header.jelly</h4>
 * <p>
 * This page is added right before the &lt;/head> tag. Convenient place for additional stylesheet,
 * &lt;meta> tags, etc.
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.235
 */
public abstract class PageDecorator extends Descriptor<PageDecorator> implements ExtensionPoint, Describable<PageDecorator> {
    /**
     * @param yourClass
     *      pass-in "this.getClass()" (except that the constructor parameters cannot use 'this',
     *      so you'd have to hard-code the class name.
     */
    protected PageDecorator(Class<? extends PageDecorator> yourClass) {
        super(yourClass);
    }

    /**
     * Infers the type of the corresponding {@link Describable} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected PageDecorator() {
    }

    public final Descriptor<PageDecorator> getDescriptor() {
        return this;
    }

    /**
     * Unless this object has additional web presence, display name is not used at all.
     * So default to "".
     */
    public String getDisplayName() {
        return "";
    }

    /**
     * All the registered instances.
     */
    public static final List<PageDecorator> ALL = new CopyOnWriteArrayList<PageDecorator>();
}
