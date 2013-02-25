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

import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.jvnet.tiger_types.Types;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

import net.sf.json.JSONObject;

/**
 * {@link Descriptor} for {@link JobProperty}.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.72
 */
public abstract class JobPropertyDescriptor extends Descriptor<JobProperty<?>> {
    protected JobPropertyDescriptor(Class<? extends JobProperty<?>> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link JobProperty} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected JobPropertyDescriptor() {
    }

    /**
     * {@inheritDoc}
     *
     * @return
     *      null to avoid setting an instance of {@link JobProperty} to the target project.
     */
    @Override
    public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        // JobPropertyDescriptors are bit different in that we allow them even without any user-visible configuration parameter,
        // so replace the lack of form data by an empty one. 
        if(formData.isNullObject()) formData=new JSONObject();

        return super.newInstance(req, formData);
    }

    /**
     * Returns true if this {@link JobProperty} type is applicable to the
     * given job type.
     * 
     * <p>
     * The default implementation of this method checks if the given job type is assignable to 'J' of
     * {@link JobProperty}<tt>&lt;J></tt>, but subtypes can extend this to change this behavior.
     *
     * @return
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of this job.
     */
    public boolean isApplicable(Class<? extends Job> jobType) {
        Type parameterization = Types.getBaseClass(clazz, JobProperty.class);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            Class applicable = Types.erasure(Types.getTypeArgument(pt, 0));
            return applicable.isAssignableFrom(jobType);
        } else {
            throw new AssertionError(clazz+" doesn't properly parameterize JobProperty. The isApplicable() method must be overriden.");
        }
    }

    /**
     * Gets the {@link JobPropertyDescriptor}s applicable for a given job type.
     */
    public static List<JobPropertyDescriptor> getPropertyDescriptors(Class<? extends Job> clazz) {
        List<JobPropertyDescriptor> r = new ArrayList<JobPropertyDescriptor>();
        for (JobPropertyDescriptor p : all())
            if(p.isApplicable(clazz))
                r.add(p);
        return r;
    }

    public static Collection<JobPropertyDescriptor> all() {
        return (Collection) Jenkins.getInstance().getDescriptorList(JobProperty.class);
    }
}
