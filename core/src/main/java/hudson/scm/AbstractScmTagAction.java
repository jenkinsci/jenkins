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
package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.model.TaskAction;
import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import hudson.security.Permission;
import hudson.security.ACL;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import jenkins.model.RunAction2;

/**
 * Common part of <tt>CVSSCM.TagAction</tt> and <tt>SubversionTagAction</tt>.
 *
 * <p>
 * This class implements the action that tags the modules. Derived classes
 * need to provide <tt>tagForm.jelly</tt> view that displays a form for
 * letting user start tagging.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractScmTagAction extends TaskAction implements BuildBadgeAction, RunAction2 {

    private transient /*final*/ Run<?,?> run;
    @Deprecated
    protected transient /*final*/ AbstractBuild build;

    /**
     * @since 1.568
     */
    protected AbstractScmTagAction(Run<?,?> run) {
        this.run = run;
        this.build = run instanceof AbstractBuild ? (AbstractBuild) run : null;
    }

    @Deprecated
    protected AbstractScmTagAction(AbstractBuild build) {
        this((Run) build);
    }

    public final String getUrlName() {
        // to make this consistent with CVSSCM, even though the name is bit off
        return "tagBuild";
    }

    /**
     * Defaults to {@link SCM#TAG}.
     */
    protected Permission getPermission() {
        return SCM.TAG;
    }

    /**
     * @since 1.568
     */
    public Run<?,?> getRun() {
        return run;
    }

    @Deprecated
    public AbstractBuild getBuild() {
        return build;
    }

    /**
     * This message is shown as the tool tip of the build badge icon.
     */
    public String getTooltip() {
        return null;
    }

    /**
     * Returns true if the build is tagged already.
     */
    public abstract boolean isTagged();

    protected ACL getACL() {
        return run.getACL();
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this,chooseAction()).forward(req,rsp);
    }

    protected synchronized String chooseAction() {
        if(workerThread!=null)
            return "inProgress.jelly";
        return "tagForm.jelly";
    }

    @Override public void onAttached(Run<?, ?> r) {
        // unnecessary, constructor already does this
    }

    @Override public void onLoad(Run<?, ?> r) {
        run = r;
        build = run instanceof AbstractBuild ? (AbstractBuild) run : null;
    }

}
