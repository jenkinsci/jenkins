/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans
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

import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.Collections;
import static org.junit.Assert.*;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class RunTest  {

    @Rule public JenkinsRule j = new JenkinsRule();

    private List<? extends Run<?,?>.Artifact> createArtifactList(String... paths) throws Exception {
        FreeStyleProject prj = j.createFreeStyleProject();
        FreeStyleBuild r = prj.scheduleBuild2(0).get();
        Run<FreeStyleProject,FreeStyleBuild>.ArtifactList list = r.new ArtifactList();
        for (String p : paths) {
            list.add(r.new Artifact(p,p,p,String.valueOf(p.length()),"n"+list.size()));  // Assuming all test inputs don't need urlencoding
        }
        list.computeDisplayName();
        return list;
    }
    
    @Test public void artifactListDisambiguation1() throws Exception {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/j.xml");
        assertEquals(a.get(0).getDisplayPath(),"c.xml");
        assertEquals(a.get(1).getDisplayPath(),"g.xml");
        assertEquals(a.get(2).getDisplayPath(),"j.xml");
    }

    @Test public void artifactListDisambiguation2() throws Exception {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/g.xml");
        assertEquals(a.get(0).getDisplayPath(),"c.xml");
        assertEquals(a.get(1).getDisplayPath(),"f/g.xml");
        assertEquals(a.get(2).getDisplayPath(),"i/g.xml");
    }

    @Test public void artifactListDisambiguation3() throws Exception {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a.xml","a/a.xml");
        assertEquals(a.get(0).getDisplayPath(),"a.xml");
        assertEquals(a.get(1).getDisplayPath(),"a/a.xml");
    }

    @Bug(17935)
    @Test public void getDynamicInvisibleTransientAction() throws Exception {
        TransientBuildActionFactory.all().add(0, new TransientBuildActionFactory() {
            @Override public Collection<? extends Action> createFor(Run target) {
                return Collections.singleton(new Action() {
                    @Override public String getDisplayName() {
                        return "Test";
                    }
                    @Override public String getIconFileName() {
                        return null;
                    }
                    @Override public String getUrlName() {
                        return null;
                    }
                });
            }
        });
        j.assertBuildStatusSuccess(j.createFreeStyleProject("stuff").scheduleBuild2(0));
        j.createWebClient().assertFails("job/stuff/1/nonexistent", HttpURLConnection.HTTP_NOT_FOUND);
    }

}
