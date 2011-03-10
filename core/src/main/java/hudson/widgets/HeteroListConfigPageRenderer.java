/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package hudson.widgets;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * Assists the lazy rendering of the configuration fragments from descriptor,
 * by remembering the "it" object that points to the overall configured target
 * (like {@link AbstractProject}).
 *
 * <p>
 * See <tt>hetero-list.jelly</tt>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.402
 */
public class HeteroListConfigPageRenderer {
    public final Object it;

    public HeteroListConfigPageRenderer(Object it) {
        this.it = it;
    }

    /**
     * Renders a configuration fragment.
     */
    @JavaScriptMethod
    public HttpResponse renderConfigPage(String id) {
        Descriptor d = Hudson.getInstance().getDescriptor(id);
        if (d==null)    return HttpResponses.notFound();
        return new ForwardToView(this,"render.jelly").with("target",it).with("descriptor",d);
    }
}
