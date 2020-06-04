/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Tom Huybrechts
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
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

/**
 * A UserProperty that remembers user-private views.
 *
 * @author Tom Huybrechts
 */
public class MyViewsProperty extends UserProperty implements ModifiableViewGroup, Action, StaplerFallback {

    /**
     * Name of the primary view defined by the user.
     * {@code null} means that the View is not defined.
     */
    @CheckForNull
    private String primaryViewName;

    /**
     * Always hold at least one view.
     */
    private CopyOnWriteArrayList<View> views = new CopyOnWriteArrayList<>();

    private transient ViewGroupMixIn viewGroupMixIn;

    @DataBoundConstructor
    public MyViewsProperty(@CheckForNull String primaryViewName) {
        this.primaryViewName = primaryViewName;
        readResolve(); // initialize fields
    }

    private MyViewsProperty() {
        this(null);
    }

    @Restricted(NoExternalUse.class)
    public Object readResolve() {
        if (views == null)
            // this shouldn't happen, but an error in 1.319 meant the last view could be deleted
            views = new CopyOnWriteArrayList<>();

        if (views.isEmpty()) {
            // preserve the non-empty invariant
            views.add(new AllView(AllView.DEFAULT_VIEW_NAME, this));
        }
        if (primaryViewName != null) {
            // It may happen when the default constructor is invoked
            primaryViewName = AllView.migrateLegacyPrimaryAllViewLocalizedName(views, primaryViewName);
        }

        viewGroupMixIn = new ViewGroupMixIn(this) {
            protected List<View> views() { return views; }
            protected String primaryView() { return primaryViewName; }
            protected void primaryView(String name) { primaryViewName=name; }
        };

        return this;
    }

    @CheckForNull
    public String getPrimaryViewName() {
        return primaryViewName;
    }

    /**
     * Sets the primary view.
     * @param primaryViewName Name of the primary view to be set.
     *                        {@code null} to make the primary view undefined.
     */
    public void setPrimaryViewName(@CheckForNull String primaryViewName) {
        this.primaryViewName = primaryViewName;
    }

    public User getUser() {
        return user;
    }

    ///// ViewGroup methods /////
    public String getUrl() {
        return user.getUrl() + "/my-views/";
    }

    public void save() throws IOException {
        user.save();
    }

    public Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    public View getView(String name) {
        return viewGroupMixIn.getView(name);
    }

    public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    public void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view,oldName,newName);
    }

    @Override
    public void addView(View view) throws IOException {
        viewGroupMixIn.addView(view);
    }

    public View getPrimaryView() {
        return viewGroupMixIn.getPrimaryView();
    }

    public HttpResponse doIndex() {
        return new HttpRedirect("view/" + Util.rawEncode(getPrimaryView().getViewName()) + "/");
    }

    @POST
    public synchronized void doCreateView(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, ParseException, FormException {
        checkPermission(View.CREATE);
        addView(View.create(req, rsp, this));
    }

    /**
     * Checks if a private view with the given name exists.
     * An error is returned if exists==true but the view does not exist.
     * An error is also returned if exists==false but the view does exist.
     **/
    public FormValidation doViewExistsCheck(@QueryParameter String value, @QueryParameter boolean exists) {
        checkPermission(View.CREATE);

        String view = Util.fixEmpty(value);
        if (view == null) return FormValidation.ok();
        if (exists) {
        	return (getView(view)!=null) ?
            		FormValidation.ok() :
            		FormValidation.error(Messages.MyViewsProperty_ViewExistsCheck_NotExist(view));
        } else {
        	return (getView(view)==null) ?
        			FormValidation.ok() :
        			FormValidation.error(Messages.MyViewsProperty_ViewExistsCheck_AlreadyExists(view));
        }
    }

    public ACL getACL() {
        return user.getACL();
    }

    ///// Action methods /////
    public String getDisplayName() {
        return Messages.MyViewsProperty_DisplayName();
    }

    public String getIconFileName() {
        return "user.png";
    }

    public String getUrlName() {
        return "my-views";
    }

    @Extension @Symbol("myView")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.MyViewsProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new MyViewsProperty();
        }
    }
    
    @Override
    public UserProperty reconfigure(StaplerRequest req, JSONObject form) throws FormException {
    	req.bindJSON(this, form);
    	return this;
    }

    public ViewsTabBar getViewsTabBar() {
        return Jenkins.get().getViewsTabBar();
    }

    public List<Action> getViewActions() {
        // Jenkins.get().getViewActions() are tempting but they are in a wrong scope
        return Collections.emptyList();
    }

    public Object getStaplerFallback() {
        return getPrimaryView();
    }

    public MyViewsTabBar getMyViewsTabBar() {
        return Jenkins.get().getMyViewsTabBar();
    }
    
    @Extension @Symbol("myView")
    public static class GlobalAction implements RootAction {

		public String getDisplayName() {
			return Messages.MyViewsProperty_GlobalAction_DisplayName();
		}

		public String getIconFileName() {
			// do not show when not logged in
			if (User.current() == null ) {
				return null;
			} 
			
			return "user.png";
		}

		public String getUrlName() {
			return "/me/my-views";
		}
		
    }
   
}
