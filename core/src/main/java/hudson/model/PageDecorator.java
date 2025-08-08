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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.DescriptorList;
import java.util.List;
import jenkins.model.Addressable;
import jenkins.model.Jenkins;

/**
 * Participates in the rendering of HTML pages for all pages of Hudson.
 *
 * <p>
 * This class provides a few hooks to augment the HTML generation process of Hudson, across
 * all the HTML pages that Hudson delivers.
 *
 * <p>
 * For example, if you'd like to add a Google Analytics stat to Hudson, then you need to inject
 * a small script fragment to all Hudson pages. This extension point provides a means to do that.
 *
 * <h2>Life-cycle</h2>
 * <p>
 * Plugins that contribute this extension point
 * should implement a new decorator and put {@link Extension} on the class.
 *
 * <h2>Associated Views</h2>
 * <h3>global.jelly</h3>
 * If this extension point needs to expose a global configuration, write this jelly page.
 * See {@link Descriptor} for more about this. Optional.
 *
 * <h3>footer.jelly</h3>
 * This page is added right before the {@code </body>} tag. Convenient place for adding tracking beacons, etc.
 *
 * <h3>header.jelly</h3>
 * This page is added right before the {@code </head>} tag. Convenient place for additional stylesheet,
 * {@code <meta>} tags, etc.
 *
 * <h3>httpHeaders.jelly</h3>
 * This is a generalization of the {@code X-Jenkins} header that aids auto-discovery.
 * This fragment can write additional {@code <st:header name="…" value="…"/>} tags that go along with it.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.235
 */
public abstract class PageDecorator extends Descriptor<PageDecorator> implements Addressable, ExtensionPoint, Describable<PageDecorator> {
    /**
     * @param yourClass
     *      pass-in "this.getClass()" (except that the constructor parameters cannot use 'this',
     *      so you'd have to hard-code the class name.
     * @deprecated as of 1.425
     *      Use the default constructor that's less error prone
     */
    @Deprecated
    protected PageDecorator(Class<? extends PageDecorator> yourClass) {
        super(yourClass);
    }

    protected PageDecorator() {
        super(self());
    }


    @Override
    public final Descriptor<PageDecorator> getDescriptor() {
        return this;
    }

    /**
     * Obtains the URL of this object, excluding the context path.
     *
     * <p>
     * Every {@link PageDecorator} is bound to URL via {@link Jenkins#getDescriptor()}.
     * This method returns such an URL.
     */
    @NonNull
    public final String getUrl() {
        return "descriptor/" + clazz.getName();
    }

    /**
     * All the registered instances.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    @Deprecated
    public static final List<PageDecorator> ALL = (List) new DescriptorList<>(PageDecorator.class);

    /**
     * Returns all the registered {@link PageDecorator} descriptors.
     */
    public static ExtensionList<PageDecorator> all() {
        return Jenkins.get().getDescriptorList(PageDecorator.class);
    }
}
