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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor.FormException;
import hudson.util.DescriptorList;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.util.List;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * List of all installed SCMs.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMS {
    /**
     * List of all installed SCMs.
     * @deprecated as of 1.286
     *      Use {@link SCM#all()} for read access and {@link Extension} for registration.
     */
    @Deprecated
    public static final List<SCMDescriptor<?>> SCMS = (List) new DescriptorList<>(SCM.class);

    /**
     * Parses {@link SCM} configuration from the submitted form.
     *
     * @param target
     *      The project for which this SCM is configured to.
     */
    @SuppressWarnings("deprecation")
    public static SCM parseSCM(StaplerRequest2 req, AbstractProject target) throws FormException, ServletException {
        SCM scm = SCM.all().newInstanceFromRadioList(req.getSubmittedForm().getJSONObject("scm"));
        if (scm == null) {
            scm = new NullSCM(); // JENKINS-36043 workaround for AbstractMultiBranchProject.submit
        }
        scm.getDescriptor().incrementGeneration();
        return scm;
    }

    /**
     * @deprecated use {@link #parseSCM(StaplerRequest2, AbstractProject)}
     */
    @Deprecated
    public static SCM parseSCM(StaplerRequest req, AbstractProject target) throws FormException, javax.servlet.ServletException {
        try {
            return parseSCM(StaplerRequest.toStaplerRequest2(req), target);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * @deprecated as of 1.294
     *      Use {@link #parseSCM(StaplerRequest2, AbstractProject)} and pass in the caller's project type.
     */
    @Deprecated
    public static SCM parseSCM(StaplerRequest req) throws FormException, javax.servlet.ServletException {
        return parseSCM(req, null);
    }

}
