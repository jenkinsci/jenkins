/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package hudson.console;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;

/**
 * Descriptor for {@link ConsoleNote}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public abstract class ConsoleAnnotationDescriptor extends Descriptor<ConsoleNote<?>> implements ExtensionPoint {
    public ConsoleAnnotationDescriptor(Class<? extends ConsoleNote<?>> clazz) {
        super(clazz);
    }

    public ConsoleAnnotationDescriptor() {
    }

    /**
     * {@inheritDoc}
     *
     * Users use this name to enable/disable annotations.
     */
    @Override
    public String getDisplayName() {
        return super.getDisplayName();
    }

    /**
     * Returns true if this descriptor has a JavaScript to be inserted on applicable console page.
     */
    public boolean hasScript() {
        return hasResource("/script.js") !=null;
    }

    /**
     * Returns true if this descriptor has a stylesheet to be inserted on applicable console page.
     */
    public boolean hasStylesheet() {
        return hasResource("/style.css") !=null;
    }

    private URL hasResource(String name) {
        return clazz.getClassLoader().getResource(clazz.getName().replace('.','/').replace('$','/')+ name);
    }

    @WebMethod(name="script.js")
    public void doScriptJs(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveFile(req, hasResource("/script.js"), TimeUnit2.DAYS.toMillis(1));
    }

    @WebMethod(name="style.css")
    public void doStyleCss(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveFile(req, hasResource("/style.css"), TimeUnit2.DAYS.toMillis(1));
    }

    /**
     * Returns all the registered {@link ConsoleAnnotationDescriptor} descriptors.
     */
    public static DescriptorExtensionList<ConsoleNote<?>,ConsoleAnnotationDescriptor> all() {
        return (DescriptorExtensionList) Jenkins.getInstance().getDescriptorList(ConsoleNote.class);
    }
}
