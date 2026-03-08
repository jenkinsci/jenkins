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
import hudson.EnvVars;
import java.util.Locale;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

public class RunParameterValue extends ParameterValue {

    private static /* non-final for Script console */ boolean SKIP_EXISTENCE_CHECK = SystemProperties.getBoolean(RunParameterValue.class.getName() + ".SKIP_EXISTENCE_CHECK", false);
    private final String runId;

    @DataBoundConstructor
    public RunParameterValue(String name, String runId, String description) {
        super(name, description);
        this.runId = check(runId);
    }

    public RunParameterValue(String name, String runId) {
        super(name, null);
        this.runId = check(runId);
    }

    private static String check(String runId) {
        if (runId == null || runId.indexOf('#') == -1) {
            throw new IllegalArgumentException(runId);
        }

        if (!SKIP_EXISTENCE_CHECK) {
            try {
                if (Run.fromExternalizableId(runId) == null) {
                    throw new IllegalArgumentException(runId);
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(runId);
            }
        }

        return runId;
    }

    /**
     * Can be null if the {@link Run} that this was pointing to no longer exists.
     */
    public @CheckForNull Run getRun() {
        return Run.fromExternalizableId(runId);
    }

    public String getRunId() {
        return runId;
    }

    private String[] split() {
        if (runId == null) {
            return null;
        }
        String[] r = runId.split("#");
        if (r.length != 2) {
            return null;
        }
        return r;
    }

    @Exported
    public String getJobName() {
        String[] r = split();
        return r == null ? null : r[0];
    }

    @Exported
    public String getNumber() {
        String[] r = split();
        return r == null ? null : r[1];
    }

    @Override
    public Run getValue() {
        return getRun();
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        Run run = getRun();

        String value = null == run ? "UNKNOWN" : Jenkins.get().getRootUrl() + run.getUrl();
        env.put(name, value);

        env.put(name + ".jobName", getJobName());   // undocumented, left for backward compatibility
        env.put(name + "_JOBNAME", getJobName());   // prefer this version

        env.put(name + ".number", getNumber());   // same as above
        env.put(name + "_NUMBER", getNumber());

        // if run is null, default to the standard '#1' display name format
        env.put(name + "_NAME",  null == run ? "#" + getNumber() : run.getDisplayName());  // since 1.504

        String buildResult = null == run || null == run.getResult() ? "UNKNOWN" : run.getResult().toString();
        env.put(name + "_RESULT",  buildResult);  // since 1.517

        env.put(name.toUpperCase(Locale.ENGLISH), value); // backward compatibility pre 1.345

    }

    @Override
    public String toString() {
        return "(RunParameterValue) " + getName() + "='" + getRunId() + "'";
    }

    @Override public String getShortDescription() {
        Run run = getRun();
        return name + "=" + (null == run ? getJobName() + " #" + getNumber() : run.getFullDisplayName());
    }

}
