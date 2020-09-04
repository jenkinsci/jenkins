/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

public class AbstractScmTagActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void regularTextDisplayedCorrectly() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        String tagToKeep = "Nice tag with space";
        p.setScm(new FakeSCM(tagToKeep));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        String tooltip = buildAndExtractTooltipAttribute(p);
        assertEquals(tagToKeep, tooltip);
    }
    
    @Test
    @Issue("SECURITY-1537")
    public void preventXssInTagAction() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new FakeSCM("<img src='x' onerror=alert(123)>XSS"));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        String tooltip = buildAndExtractTooltipAttribute(p);
        assertThat(tooltip, not(containsString("<")));
        assertThat(tooltip, startsWith("&lt;"));
    }
    
    private String buildAndExtractTooltipAttribute(FreeStyleProject p) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.getPage(p);

        DomElement buildHistory = page.getElementById("buildHistory");
        DomNodeList<HtmlElement> imgs = buildHistory.getElementsByTagName("img");
        HtmlImage tagImage = (HtmlImage) imgs.stream()
                .filter(i -> i.getAttribute("class").contains("icon-save"))
                .findFirst().orElseThrow(IllegalStateException::new);

        String tooltip = tagImage.getAttribute("tooltip");
        return tooltip;
    }

    public static class FakeSCM extends SCM {

        private String desiredTooltip;

        FakeSCM(String desiredTooltip) {
            this.desiredTooltip = desiredTooltip;
        }

        @Override
        public ChangeLogParser createChangeLogParser() {
            return null;
        }

        @Override
        public void checkout(@NonNull Run<?, ?> build, @NonNull Launcher launcher, @NonNull FilePath workspace, @NonNull TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState baseline) throws IOException, InterruptedException {
            build.addAction(new TooltipTagAction(build, desiredTooltip));
        }
    }

    public static class TooltipTagAction extends AbstractScmTagAction {

        private String desiredTooltip;

        TooltipTagAction(Run build, String desiredTooltip) {
            super(build);
            this.desiredTooltip = desiredTooltip;
        }

        @Override
        public boolean isTagged() {
            return true;
        }

        @Override
        public String getIconFileName() {
            return "save.gif";
        }

        @Override
        public String getDisplayName() {
            return "ClickOnMe";
        }

        @Override
        public String getTooltip() {
            return desiredTooltip;
        }
    }
}
