/*
 * The MIT License
 *
 * Copyright (c) 2025, Jan Faracik
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

package jenkins.run;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.RunList;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Handles build comparison page requests and provides comparison data.
 */
public class BuildComparison {

    private final Run<?, ?> currentBuild;
    private final Job<?, ?> job;

    public BuildComparison(Run<?, ?> currentBuild) {
        this.currentBuild = currentBuild;
        this.job = currentBuild.getParent();
    }

    /**
     * Gets the current build being viewed.
     */
    @NonNull
    public Run<?, ?> getCurrentBuild() {
        return currentBuild;
    }

    /**
     * Gets the job that owns the builds.
     */
    @NonNull
    public Job<?, ?> getJob() {
        return job;
    }

    /**
     * Gets the second build for comparison based on request parameters.
     * Always uses Stapler.getCurrentRequest2() to ensure we get the actual current request,
     * as the req parameter from Jelly templates may not always be the current request.
     */
    @CheckForNull
    public Run<?, ?> getBuild2(StaplerRequest2 req) {
        // Always use Stapler.getCurrentRequest2() to get the actual current request
        // The req parameter is kept for backward compatibility but we use the current request instead
        StaplerRequest2 requestToUse = Stapler.getCurrentRequest2();

        String build2Param = requestToUse != null ? requestToUse.getParameter("build2") : null;

        if (build2Param != null) {
            try {
                int buildNumber = Integer.parseInt(build2Param);
                return job.getBuildByNumber(buildNumber);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Gets a list of recent builds for the build selector dropdown.
     */
    @NonNull
    public RunList<? extends Run<?, ?>> getRecentBuilds() {
        return job.getNewBuilds();
    }

    /**
     * Gets all builds for the job (limited to reasonable number).
     */
    @NonNull
    public RunList<? extends Run<?, ?>> getAllBuilds() {
        return job.getBuilds().limit(500);
    }
}
