/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts, Geoff Cummings
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.util.EnumConverter;
import hudson.util.RunList;
import java.util.Objects;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;

public class RunParameterDefinition extends SimpleParameterDefinition {

    /**
     * Constants that control how Run Parameter is filtered.
     * @since 1.517
     */
    public enum RunParameterFilter {
        ALL,
        STABLE,
        SUCCESSFUL,
        COMPLETED;

        public String getName() {
            return name();
        }

        static {
            Stapler.CONVERT_UTILS.register(new EnumConverter(), RunParameterFilter.class);
        }
    }

    private final String projectName;
    private final String runId;
    private final RunParameterFilter filter;

    // TODO consider a simplified @DataBoundConstructor using @DataBoundSetter for description & filter
    /**
     * @since 1.517
     */
    @DataBoundConstructor
    public RunParameterDefinition(@NonNull String name, String projectName, @CheckForNull String description, @CheckForNull RunParameterFilter filter) {
        super(name, description);
        this.projectName = projectName;
        this.runId = null;
        this.filter = filter;
    }

    /**
     * @deprecated as of 1.517
     */
    @Deprecated
    public RunParameterDefinition(@NonNull String name, String projectName, @CheckForNull String description) {
        // delegate to updated constructor with additional RunParameterFilter parameter defaulted to ALL.
        this(name, projectName, description, RunParameterFilter.ALL);
    }

    private RunParameterDefinition(@NonNull String name, String projectName, String runId, @CheckForNull String description, @CheckForNull RunParameterFilter filter) {
        super(name, description);
        this.projectName = projectName;
        this.runId = runId;
        this.filter = filter;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof RunParameterValue) {
            RunParameterValue value = (RunParameterValue) defaultValue;
            return new RunParameterDefinition(getName(), getProjectName(), value.getRunId(), getDescription(), getFilter());
        } else {
            return this;
        }
    }

    @Exported
    public String getProjectName() {
        return projectName;
    }

    public Job getProject() {
        return Jenkins.get().getItemByFullName(projectName, Job.class);
    }

    /**
     * @return The current filter value, if filter is null, returns ALL
     * @since 1.517
     */
    @Exported
    public RunParameterFilter getFilter() {
        // if filter is null, default to RunParameterFilter.ALL
        return null == filter ? RunParameterFilter.ALL : filter;
    }

    /**
     * @since 1.517
     * @return Returns a list of builds, filtered based on the filter value.
     */
    public RunList getBuilds() {
        // use getFilter() method so we dont have to worry about null filter value.
        switch (getFilter()) {
            case COMPLETED:
                return getProject().getBuilds().overThresholdOnly(Result.ABORTED).completedOnly();
            case SUCCESSFUL:
                return getProject().getBuilds().overThresholdOnly(Result.UNSTABLE).completedOnly();
            case STABLE:
                return getProject().getBuilds().overThresholdOnly(Result.SUCCESS).completedOnly();
            default:
                return getProject().getBuilds();
        }
    }

    @Extension @Symbol({"run", "runParam"})
    public static class DescriptorImpl extends ParameterDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.RunParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/run.html";
        }

        @Override
        public ParameterDefinition newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            return req.bindJSON(RunParameterDefinition.class, formData);
        }

        public AutoCompletionCandidates doAutoCompleteProjectName(@QueryParameter String value) {
            return AutoCompletionCandidates.ofJobNames(Job.class, value, null, Jenkins.get());
        }

    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        if (runId != null) {
            return createValue(runId);
        }

        Run<?, ?> lastBuild;
        Job project = getProject();

        if (project == null) {
            return null;
        }

        // use getFilter() so we dont have to worry about null filter value.
        switch (getFilter()) {
        case COMPLETED:
            lastBuild = project.getLastCompletedBuild();
            break;
        case SUCCESSFUL:
            lastBuild = project.getLastSuccessfulBuild();
            break;
        case STABLE:
            lastBuild = project.getLastStableBuild();
            break;
        default:
            lastBuild = project.getLastBuild();
            break;
        }

        if (lastBuild != null) {
            return createValue(lastBuild.getExternalizableId());
        } else {
            return null;
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        RunParameterValue value = req.bindJSON(RunParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    @Override
    public RunParameterValue createValue(String value) {
        return new RunParameterValue(getName(), value, getDescription());
    }

    @Override
    public int hashCode() {
        if (RunParameterDefinition.class != getClass()) {
            return super.hashCode();
        }
        return Objects.hash(getName(), getDescription(), projectName, runId, filter);
    }

    @Override
    @SuppressFBWarnings(value = "EQ_GETCLASS_AND_CLASS_CONSTANT", justification = "ParameterDefinitionTest tests that subclasses are not equal to their parent classes, so the behavior appears to be intentional")
    public boolean equals(Object obj) {
        if (RunParameterDefinition.class != getClass())
            return super.equals(obj);
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RunParameterDefinition other = (RunParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        if (!Objects.equals(getDescription(), other.getDescription()))
            return false;
        if (!Objects.equals(projectName, other.projectName))
            return false;
        if (!Objects.equals(runId, other.runId))
            return false;
        return Objects.equals(filter, other.filter);
    }
}
