/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;
import javax.annotation.CheckForNull;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Pluggable ability to manage transfer and/or storage of build artifacts.
 * The descriptor should specify at least a display name, and optionally a {@code config} view.
 * Since the user can configure this class, you must have a {@link DataBoundConstructor}.
 * @see ArtifactManagerConfiguration
 * @see ArtifactManagerFactoryDescriptor
 * @since 1.532
 */
public abstract class ArtifactManagerFactory extends AbstractDescribableImpl<ArtifactManagerFactory> implements ExtensionPoint {

    /**
     * Optionally creates a manager for a particular build.
     * All configured factories are consulted in sequence; the first manager thus yielded (if any) will be stored in the build.
     * {@link StandardArtifactManager} is used as a fallback.
     * @param build a running (or recently completed) build ready for {@link ArtifactManager#archive}
     * @return a manager, or null if this manager should not handle this kind of project, builds on this kind of slave, etc.
     */
    public abstract @CheckForNull ArtifactManager managerFor(Run<?,?> build);

    @Override public ArtifactManagerFactoryDescriptor getDescriptor() {
        return (ArtifactManagerFactoryDescriptor) super.getDescriptor();
    }

}
