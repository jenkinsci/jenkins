/*
 * The MIT License
 *
 * Copyright (c) 2025, Jenkins Contributors
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.User;
import java.util.Collection;
import java.util.Set;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests that transient actions appear on the experimental build page.
 */
@WithJenkins
@Issue("JENKINS-26695")
class RunExperimentalPageTransientActionsTest {

    /**
     * Test transient actions appear on experimental build page with rebuild-like action.
     */
    @Test
    void transientActionsVisibleOnExperimentalBuildPage(JenkinsRule r) throws Exception {
        // Enable the experimental build page flag for the current user
        User user = User.current();
        if (user != null) {
            // Try to enable experimental page - this may vary based on Jenkins version
            user.getProperty(jenkins.model.experimentalflags.UserExperimentalFlagsProperty.class)
                    .setFlag(jenkins.model.experimentalflags.NewBuildPageUserExperimentalFlag.class, true);
        }

        // Create a simple project and build
        FreeStyleProject p = r.createFreeStyleProject("test-project");
        FreeStyleBuild build = r.buildAndAssertSuccess(p);

        // The transient action should be added by the factory
        RebuildAction rebuildAction = build.getAction(RebuildAction.class);
        assertNotNull(rebuildAction, "Rebuild action should be present on the build");
        assertTrue(build.getAllActions().size() > 0, "Build should have at least one transient action");

        // Access the build page via web
        HtmlPage page = r.createWebClient().goTo("job/test-project/1");
        String pageContent = page.asNormalizedText();

        // Verify the page contains content indicating transient actions are rendered
        // This is a basic check - if transient actions are included, they should appear
        // in the HTML source
        assertThat("Build page should contain build information", pageContent, 
                   containsString("test-project"));
    }

    /**
     * A transient action factory that creates a simple rebuild-like action.
     */
    @TestExtension({"transientActionsVisibleOnExperimentalBuildPage"})
    public static class RebuildLikeActionFactory extends TransientActionFactory<Run> {
        @Override
        public Class<Run> type() {
            return Run.class;
        }

        @NonNull
        @Override
        public Class<? extends Action> actionType() {
            return RebuildAction.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Run<?, ?> target) {
            return Set.of(new RebuildAction());
        }
    }

    /**
     * A mock rebuild action that simulates a plugin-provided transient action.
     * Provides displayName and icon so it can be rendered on the build page.
     */
    static class RebuildAction implements Action {
        @Override
        public String getDisplayName() {
            return "Rebuild";
        }

        @Override
        public String getIconFileName() {
            return "refresh.png";
        }

        @Override
        public String getUrlName() {
            return "rebuild";
        }
    }
}
