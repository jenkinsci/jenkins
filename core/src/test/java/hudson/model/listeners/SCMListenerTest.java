/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package hudson.model.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;

@SuppressWarnings("deprecation")
class SCMListenerTest {

    @Issue("JENKINS-23522")
    @SuppressWarnings("rawtypes")
    @Test
    void onChangeLogParsed() throws Exception {
        SCM scm = Mockito.mock(SCM.class);
        BuildListener bl = Mockito.mock(BuildListener.class);
        ChangeLogSet cls = Mockito.mock(ChangeLogSet.class);
        AbstractBuild ab = Mockito.mock(AbstractBuild.class);
        AbstractProject ap = Mockito.mock(AbstractProject.class);
        Mockito.when(ab.getProject()).thenReturn(ap);
        Mockito.when(ap.getScm()).thenReturn(scm);
        for (L l : new L[] {new L1(), new L2(), new L3()}) {
            assertEquals(0, l.cnt);
            l.onChangeLogParsed(ab, bl, cls);
            assertEquals(1, l.cnt);
            l.onChangeLogParsed(ab, scm, bl, cls);
            assertEquals(2, l.cnt);
        }
        Run r = Mockito.mock(Run.class);
        TaskListener tl = Mockito.mock(TaskListener.class);
        L l = new L1();
        l.onChangeLogParsed(r, scm, tl, cls);
        assertEquals(0, l.cnt, "cannot handle this");
        l = new L2();
        l.onChangeLogParsed(r, scm, tl, cls);
        assertEquals(1, l.cnt, "does handle this");
        l = new L3();
        l.onChangeLogParsed(r, scm, tl, cls);
        assertEquals(0, l.cnt, "cannot handle this");
    }

    private static class L extends SCMListener {
        int cnt;
    }

    private static class L1 extends L {
        @Override public void onChangeLogParsed(AbstractBuild<?, ?> build, BuildListener listener, ChangeLogSet<?> changelog) {
            cnt++;
        }
    }

    private static class L2 extends L {
        @Override public void onChangeLogParsed(Run<?, ?> build, SCM scm, TaskListener listener, ChangeLogSet<?> changelog) {
            cnt++;
        }
    }

    private static class L3 extends L {
        @Override public void onChangeLogParsed(AbstractBuild<?, ?> build, BuildListener listener, ChangeLogSet<?> changelog) throws Exception {
            cnt++;
            super.onChangeLogParsed(build, listener, changelog);
        }
    }

}
