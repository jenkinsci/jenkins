/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.util.FormValidation;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A view that delegates to another.
 *
 * TODO: this does not respond to renaming or deleting the proxied view.
 *
 * @author Tom Huybrechts
 *
 */
public class ProxyView extends View implements StaplerFallback {

    private String proxiedViewName;

    @DataBoundConstructor
    public ProxyView(String name) {
        super(name);

        if (Jenkins.get().getView(name) != null) {
            // if this is a valid global view name, let's assume the
            // user wants to show it
            proxiedViewName = name;
        }
    }

    public View getProxiedView() {
        if (proxiedViewName == null) {
            // just so we avoid errors just after creation
            return Jenkins.get().getPrimaryView();
        } else {
            return Jenkins.get().getView(proxiedViewName);
        }
    }

    public String getProxiedViewName() {
        return proxiedViewName;
    }

    public void setProxiedViewName(String proxiedViewName) {
        this.proxiedViewName = proxiedViewName;
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        return getProxiedView().getItems();
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return getProxiedView().contains(item);
    }

    @Override
    public TopLevelItem getItem(String name) {
        return getProxiedView().getItem(name);
    }

    @Override
    protected void submit(StaplerRequest2 req) throws IOException, ServletException, FormException {
        if (Util.isOverridden(ProxyView.class, getClass(), "submit", StaplerRequest.class)) {
            try {
                submit(StaplerRequest.fromStaplerRequest2(req));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            submitImpl(req);
        }
    }

    /**
     * @deprecated use {@link #submit(StaplerRequest2)}
     */
    @Deprecated
    @Override
    protected void submit(StaplerRequest req) throws IOException, javax.servlet.ServletException, FormException {
        try {
            submitImpl(StaplerRequest.toStaplerRequest2(req));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void submitImpl(StaplerRequest2 req) throws ServletException, FormException {
        String proxiedViewName = req.getSubmittedForm().getString("proxiedViewName");
        if (Jenkins.get().getView(proxiedViewName) == null) {
            throw new FormException("Not an existing global view", "proxiedViewName");
        }
        this.proxiedViewName = proxiedViewName;
    }

    @RequirePOST
    @Override
    public Item doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        return getProxiedView().doCreateItem(req, rsp);
    }

    /**
     * Fails if a global view with the given name does not exist.
     */
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String view = Util.fixEmpty(value);
        if (view == null) return FormValidation.ok();

        if (Jenkins.get().getView(view) != null)
            return FormValidation.ok();
        else
            return FormValidation.error(Messages.ProxyView_NoSuchViewExists(value));
    }

    @Extension @Symbol("proxy")
    public static class DescriptorImpl extends ViewDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ProxyView_DisplayName();
        }

        @Override
        public boolean isInstantiable() {
            // doesn't make sense to add a ProxyView to the global views
            return !(Stapler.getCurrentRequest2().findAncestorObject(ViewGroup.class) instanceof Jenkins);
        }

    }

    @Override
    public Object getStaplerFallback() {
        return getProxiedView();
    }

}
