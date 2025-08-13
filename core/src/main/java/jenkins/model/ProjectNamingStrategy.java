/*
 * The MIT License
 *
 * Copyright (c) 2012, Dominik Bartholdi, Seiji Sogabe
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * This ExtensionPoint allows to enforce the name of projects/jobs.
 *
 * @author Dominik Bartholdi (imod)
 */
public abstract class ProjectNamingStrategy implements Describable<ProjectNamingStrategy>, ExtensionPoint {

    @Override
    public ProjectNamingStrategyDescriptor getDescriptor() {
        return (ProjectNamingStrategyDescriptor) Jenkins.get().getDescriptor(getClass());
    }

    public static DescriptorExtensionList<ProjectNamingStrategy, ProjectNamingStrategyDescriptor> all() {
        return Jenkins.get().getDescriptorList(ProjectNamingStrategy.class);
    }

    /**
     * Called when creating a new job.
     *
     * @param name
     *            the name given from the UI
     * @throws Failure
     *             if the user has to be informed about an illegal name, forces the user to change the name before submitting. The message of the failure will be presented to the user.
     * @deprecated Use {@link #checkName(String, String)}
     */
    @Deprecated
    public void checkName(String name) throws Failure {
        // no op
    }

    /**
     * Called when creating a new job.
     *
     * @param parentName
     *            the full name of the parent ItemGroup
     * @param name
     *            the name given from the UI
     * @throws Failure
     *             if the user has to be informed about an illegal name, forces the user to change the name before submitting. The message of the failure will be presented to the user.
     *
     * @since 2.367
     */
    public void checkName(String parentName, String name) throws Failure {
        checkName(name);
    }

    /**
     * This flag can be used to force existing jobs to be migrated to a new naming strategy - if this method returns true, the naming will be enforced at every config change. If {@code false} is
     * returned, only new jobs have to follow the strategy.
     *
     * @return {@code true} if existing jobs should be enforced to confirm to the naming standard.
     */
    public boolean isForceExistingJobs() {
        return false;
    }

    /**
     * The default naming strategy which does not restrict the name of a job.
     */
    public static final ProjectNamingStrategy DEFAULT_NAMING_STRATEGY = new DefaultProjectNamingStrategy();

    /**
     * Default implementation which does not restrict the name to any form.
     */
    public static final class DefaultProjectNamingStrategy extends ProjectNamingStrategy implements Serializable {

        private static final long serialVersionUID = 1L;

        @DataBoundConstructor
        public DefaultProjectNamingStrategy() {
        }

        @Override
        public void checkName(String origName) throws Failure {
            // default - should just do nothing (this is how Jenkins worked before introducing this ExtensionPoint)
        }

        /**
         * DefaultProjectNamingStrategy is stateless, therefore save to keep the same instance
         */
        private Object readResolve() {
            return DEFAULT_NAMING_STRATEGY;
        }

        @Extension @Symbol("standard")
        public static final class DescriptorImpl extends ProjectNamingStrategyDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.DefaultProjectNamingStrategy_DisplayName();
            }

            @Override
            public String getHelpFile() {
                return "/help/system-config/defaultJobNamingStrategy.html";
            }
        }
    }

    /**
     * Naming strategy which allows the admin to define a pattern a job's name has to follow.
     */
    public static final class PatternProjectNamingStrategy extends ProjectNamingStrategy implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * regex pattern a job's name has to follow
         */
        private final String namePattern;

        private final String description;

        private boolean forceExistingJobs;

        @Deprecated
        public PatternProjectNamingStrategy(String namePattern, boolean forceExistingJobs) {
            this(namePattern, null, forceExistingJobs);
        }

        /** @since 1.533 */
        @DataBoundConstructor
        public PatternProjectNamingStrategy(String namePattern, String description, boolean forceExistingJobs) {
            this.namePattern = namePattern;
            this.description = description;
            this.forceExistingJobs = forceExistingJobs;
        }

        @Override
        public void checkName(String name) {
            if ((namePattern != null && !namePattern.isBlank()) && (name != null && !name.isBlank())) {
                if (!Pattern.matches(namePattern, name)) {
                    throw new Failure(description == null || description.isEmpty() ?
                        Messages.Hudson_JobNameConventionNotApplyed(name, namePattern) :
                        description);
                }
            }
        }

        public String getNamePattern() {
            return namePattern;
        }

        /** @since 1.533 */
        public String getDescription() {
            return description;
        }

        @Override
        public boolean isForceExistingJobs() {
            return forceExistingJobs;
        }

        @Extension @Symbol("pattern")
        public static final class DescriptorImpl extends ProjectNamingStrategyDescriptor {

            public static final String DEFAULT_PATTERN = ".*";

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.PatternProjectNamingStrategy_DisplayName();
            }

            @Override
            public String getHelpFile() {
                return "/help/system-config/patternJobNamingStrategy.html";
            }

            public FormValidation doCheckNamePattern(@QueryParameter String value)
                    throws IOException, ServletException {
                String pattern = Util.fixEmptyAndTrim(value);
                if (pattern == null) {
                    return FormValidation.error(Messages.PatternProjectNamingStrategy_NamePatternRequired());
                }
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    return FormValidation.error(Messages.PatternProjectNamingStrategy_NamePatternInvalidSyntax());
                }
                return FormValidation.ok();
            }
        }
    }

    public abstract static class ProjectNamingStrategyDescriptor extends Descriptor<ProjectNamingStrategy> {
    }

}
