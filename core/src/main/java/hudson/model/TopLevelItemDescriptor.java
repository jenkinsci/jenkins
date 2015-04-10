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

import hudson.ExtensionList;
import jenkins.model.Jenkins;
import jenkins.model.TopLevelItemDescriptorCategory;
import org.acegisecurity.AccessDeniedException;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.jelly.DefaultScriptInvoker;
import org.kohsuke.stapler.jelly.JellyClassTearOff;

import java.io.IOException;
import java.io.StringWriter;

/**
 * {@link Descriptor} for {@link TopLevelItem}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TopLevelItemDescriptor extends Descriptor<TopLevelItem> {
    protected TopLevelItemDescriptor(Class<? extends TopLevelItem> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link TopLevelItem} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected TopLevelItemDescriptor() {
    }

    @Exported
    public TopLevelItemDescriptorCategory getCategory() {
        return TopLevelItemDescriptorCategory.JOBS_AND_WORKFLOWS;
    }
    
    /**
     * {@link TopLevelItemDescriptor}s often uses other descriptors to decorate itself.
     * This method allows the subtype of {@link TopLevelItemDescriptor}s to filter them out.
     *
     * <p>
     * This is useful for a workflow/company specific job type that wants to eliminate
     * options that the user would see.
     *
     * @since 1.294
     */
    public boolean isApplicable(Descriptor descriptor) {
        return true;
    }

    /**
     * {@link TopLevelItemDescriptor}s often may want to limit the scope within which they can be created.
     * This method allows the subtype of {@link TopLevelItemDescriptor}s to filter them out.
     *
     * @since TODO
     */
    public boolean isApplicableIn(ItemGroup parent) {
        return true;
    }

    /**
     * Checks if this top level item is applicable within the specified item group.
     * <p>
     * This is just a convenience function.
     * @since TODO
     */
    public final void checkApplicableIn(ItemGroup parent) {
        if (!isApplicableIn(parent)) {
            throw new AccessDeniedException(
                    Messages.TopLevelItemDescriptor_NotApplicableIn(getDisplayName(), parent.getFullDisplayName()));
        }
    }

    /**
     * Tests if the given instance belongs to this descriptor, in the sense
     * that this descriptor can produce items like the given one.
     *
     * <p>
     * {@link TopLevelItemDescriptor}s that act like a wizard and produces different
     * object types than {@link #clazz} can override this method to augment
     * instance-descriptor relationship.
     * @since 1.410
     */
    public boolean testInstance(TopLevelItem i) {
        return clazz.isInstance(i);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Used as the caption when the user chooses what job type to create.
     * The descriptor implementation also needs to have <tt>newJobDetail.jelly</tt>
     * script, which will be used to render the text below the caption
     * that explains the job type.
     */
    public abstract String getDisplayName();

    /**
     * For REST API, expose the detailed description HTML.
     */
    @Exported
    public String getNewInstanceDetailHtml() throws JellyException, IOException {
        // TODO: there should be a method like "include" to do this better
        // Stapler.getCurrentRequest().getView(this,"newInstanceDetails").include(req,rsp);

        Script s = WebApp.getCurrent().getMetaClass(this).getTearOff(JellyClassTearOff.class).findScript("newInstanceDetail.jelly");
        if (s==null)        return null;
        
        StringWriter sw = new StringWriter();
        new DefaultScriptInvoker().invokeScript(
                Stapler.getCurrentRequest(),
                Stapler.getCurrentResponse(),
                s,
                this,
                XMLOutput.createXMLOutput(sw)
        );
        return sw.toString();
    }
    
    /**
     * @deprecated since 2007-01-19.
     *      This is not a valid operation for {@link Job}s.
     */
    @Deprecated
    public TopLevelItem newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new {@link TopLevelItem}.
     *
     * @deprecated as of 1.390
     *      Use {@link #newInstance(ItemGroup, String)}
     */
    public TopLevelItem newInstance(String name) {
        return newInstance(Jenkins.getInstance(), name);
    }

    /**
     * Creates a new {@link TopLevelItem} for the specified parent.
     *
     * @since 1.390
     */
    public abstract TopLevelItem newInstance(ItemGroup parent, String name);

    /**
     * Returns all the registered {@link TopLevelItem} descriptors.
     */
    public static ExtensionList<TopLevelItemDescriptor> all() {
        return Items.all();
    }

}
