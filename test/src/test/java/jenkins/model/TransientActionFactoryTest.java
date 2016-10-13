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

import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.ProminentProjectAction;
import hudson.model.queue.FoldableAction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matchers;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class TransientActionFactoryTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void addedToAbstractItem() throws Exception {
        assertNotNull(r.createFolder("d").getAction(MyAction.class));
        assertNotNull(r.createFreeStyleProject().getAction(MyAction.class));
    }
    @TestExtension("addedToAbstractItem") public static class TestItemFactory extends TransientActionFactory<AbstractItem, MyAction> {
        @Override public Class<AbstractItem> type() {return AbstractItem.class;}
        @Override public Class<MyAction> actionType() {return MyAction.class;}
        @Override public Collection<? extends MyAction> createFor(AbstractItem i) {
            return Collections.singleton(new MyAction());
        }
    }

    private static class MyAction implements Action {
        @Override public String getIconFileName() {
            return null;
        }
        @Override public String getDisplayName() {
            return null;
        }
        @Override public String getUrlName() {
            return null;
        }
    }

    @Test public void laziness() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        // testing getAction(Class)
        assertNull(p.getAction(FoldableAction.class));
        assertEquals(0, LazyFactory.count);
        assertNotNull(p.getAction(ProminentProjectAction.class));
        assertEquals(1, LazyFactory.count);
        LazyFactory.count = 0;
        // getAllActions
        List<? extends Action> allActions = p.getAllActions();
        assertEquals(1, LazyFactory.count);
        assertThat(Util.filter(allActions, FoldableAction.class), Matchers.<FoldableAction>iterableWithSize(0));
        assertThat(Util.filter(allActions, ProminentProjectAction.class), Matchers.<ProminentProjectAction>iterableWithSize(1));
        LazyFactory.count = 0;
        // getActions(Class)
        assertThat(p.getActions(FoldableAction.class), Matchers.<FoldableAction>iterableWithSize(0));
        assertEquals(0, LazyFactory.count);
        assertThat(p.getActions(ProminentProjectAction.class), Matchers.<ProminentProjectAction>iterableWithSize(1));
        assertEquals(1, LazyFactory.count);
    }
    @TestExtension("laziness") public static class LazyFactory extends TransientActionFactory<FreeStyleProject, ProminentProjectAction> {
        static int count;
        @Override public Class<FreeStyleProject> type() {return FreeStyleProject.class;}
        @Override public Class<ProminentProjectAction> actionType() {return ProminentProjectAction.class;}
        @Override public Collection<? extends ProminentProjectAction> createFor(FreeStyleProject p) {
            count++;
            class A extends InvisibleAction implements ProminentProjectAction {}
            return Collections.singleton(new A());
        }
    }

    @Test public void compatibility() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        // testing getAction(Class)
        assertNull(p.getAction(FoldableAction.class));
        assertEquals(1, OldFactory.count);
        assertNotNull(p.getAction(ProminentProjectAction.class));
        assertEquals(2, OldFactory.count);
        OldFactory.count = 0;
        // getAllActions
        List<? extends Action> allActions = p.getAllActions();
        assertEquals(1, OldFactory.count);
        assertThat(Util.filter(allActions, FoldableAction.class), Matchers.<FoldableAction>iterableWithSize(0));
        assertThat(Util.filter(allActions, ProminentProjectAction.class), Matchers.<ProminentProjectAction>iterableWithSize(1));
        OldFactory.count = 0;
        // getActions(Class)
        assertThat(p.getActions(FoldableAction.class), Matchers.<FoldableAction>iterableWithSize(0));
        assertEquals(1, OldFactory.count);
        assertThat(p.getActions(ProminentProjectAction.class), Matchers.<ProminentProjectAction>iterableWithSize(1));
        assertEquals(2, OldFactory.count);
    }
    @SuppressWarnings("rawtypes") // cannot actually compile one using a single type parameter, so have to simulate binary compatibility with rawtypes
    @TestExtension("compatibility") public static class OldFactory extends TransientActionFactory {
        static int count;
        @Override public Class<FreeStyleProject> type() {return FreeStyleProject.class;}
        @Override public Collection createFor(Object o) {
            count++;
            class A extends InvisibleAction implements ProminentProjectAction {}
            return Collections.singleton(new A());
        }
    }

}
