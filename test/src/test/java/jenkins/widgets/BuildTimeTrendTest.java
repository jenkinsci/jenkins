/*
 * The MIT License
 * 
 * Copyright (c) 2020, CloudBees, Inc.
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
package jenkins.widgets;

import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.XmlFile;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.slaves.DumbSlave;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.SortedMap;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BuildTimeTrendTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void withAbstractJob_OnMaster() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        JenkinsRule.WebClient wc = j.createWebClient();

        wc.withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(p, "buildTimeTrend");

        HtmlTable table = page.getDocumentElement().querySelector("table[data-is-master-slave-enabled=false]");
        assertNotNull(table);
    }

    @Test
    public void withAbstractJob_OnNode() throws Exception {
        DumbSlave agent = j.createSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);

        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        JenkinsRule.WebClient wc = j.createWebClient();

        wc.withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(p, "buildTimeTrend");
        DomNodeList<DomNode> anchors = page.getDocumentElement().querySelectorAll("table[data-is-master-slave-enabled=true] td a");
        Optional<DomNode> anchor = anchors.stream()
                .filter(a -> a.getTextContent().equals(agent.getNodeName()))
                .findFirst();
        assertTrue(anchor.isPresent());
    }

    @Test
    public void withAbstractJob_OnBoth() throws Exception {
        DumbSlave agent = j.createSlave();
        FreeStyleProject p = j.createFreeStyleProject();

        p.setAssignedNode(j.jenkins);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        
        p.setAssignedNode(agent);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        JenkinsRule.WebClient wc = j.createWebClient();

        wc.withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(p, "buildTimeTrend");

        DomNodeList<DomNode> anchors = page.getDocumentElement().querySelectorAll("table[data-is-master-slave-enabled=true] td a");
        Optional<DomNode> anchor = anchors.stream()
                .filter(a -> a.getTextContent().equals(agent.getNodeName()))
                .findFirst();
        // for the build on agent
        assertTrue(anchor.isPresent());

        String masterName = hudson.model.Messages.Hudson_Computer_DisplayName();
        DomNodeList<DomNode> tds = page.getDocumentElement().querySelectorAll("table[data-is-master-slave-enabled=true] td");
        Optional<DomNode> td = tds.stream()
                .filter(t -> t.getTextContent().equals(masterName))
                .findFirst();
        // for the build on master
        assertTrue(td.isPresent());
    }

    @Test
    @LocalData("localDataNonAbstractJob")
    public void withNonAbstractJob_withoutAgents() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        TopLevelItem p = j.jenkins.getItem("job0");
        assertThat(p, instanceOf(NonAbstractJob.class));

        wc.withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(p, "buildTimeTrend");

        DomNodeList<DomNode> tds = page.getDocumentElement().querySelectorAll("table[data-is-master-slave-enabled=false] td");
        Optional<DomNode> td = tds.stream()
                .filter(t -> t.getTextContent().equals("#1"))
                .findFirst();
        // for the stored build
        assertTrue(td.isPresent());
    }

    @Test
    @LocalData("localDataNonAbstractJob")
    @Issue("JENKINS-63232")
    public void withNonAbstractJob_withAgents() throws Exception {
        // just to trigger data-is-master-slave-enabled = true
        j.createSlave();
        
        // Before the correction, if there was an agent and the build was not inheriting from AbstractBuild, we got
        // Uncaught TypeError: Cannot read property 'escapeHTML' of undefined
        
        JenkinsRule.WebClient wc = j.createWebClient();
        TopLevelItem p = j.jenkins.getItem("job0");
        assertThat(p, instanceOf(NonAbstractJob.class));

        wc.withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(p, "buildTimeTrend");

        DomNodeList<DomNode> tds = page.getDocumentElement().querySelectorAll("table[data-is-master-slave-enabled=true] td");
        Optional<DomNode> td = tds.stream()
                .filter(t -> t.getTextContent().equals("#1"))
                .findFirst();
        // for the stored build
        assertTrue(td.isPresent());
        // with the correction, the last cell is just empty instead of throwing the TypeError
    }

    public static class NonAbstractBuild extends Run<NonAbstractJob, NonAbstractBuild> {
        protected NonAbstractBuild(@NonNull NonAbstractJob job) throws IOException {
            super(job);
            this.result = Result.SUCCESS;
            this.onEndBuilding();
        }
    }

    public static class NonAbstractJob extends Job<NonAbstractJob, NonAbstractBuild> implements TopLevelItem {
        public NonAbstractJob(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override 
        public boolean isBuildable() {
            return true;
        }

        private RunMap<NonAbstractBuild> runMap;
        
        @Override 
        protected SortedMap<Integer, NonAbstractBuild> _getRuns() {
            if (runMap == null){
                runMap = new RunMap<>(this.getBuildDir(), this::createBuildFromDir);
            }
            return runMap;
        }

        private NonAbstractBuild createBuildFromDir(File dir) throws IOException {
            NonAbstractBuild build = new NonAbstractBuild(NonAbstractJob.this);
            XmlFile xmlFile = new XmlFile(Run.XSTREAM2, new File(dir, "build.xml"));
            xmlFile.unmarshal(build);
            return build;
        }

        @Override 
        protected void removeRun(NonAbstractBuild run) {
            
        }

        @Override 
        public DescriptorImpl getDescriptor() {
            return (NonAbstractJob.DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
        }

        @TestExtension
        public static class DescriptorImpl extends TopLevelItemDescriptor {
            @Override 
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new NonAbstractJob(parent, name);
            }
        }
    }
}
