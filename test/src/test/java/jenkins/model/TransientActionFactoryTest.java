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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.ProminentProjectAction;
import hudson.model.queue.FoldableAction;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TransientActionFactoryTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void addedToAbstractItem() throws Exception {
        assertNotNull(r.createFolder("d").getAction(MyAction.class));
        assertNotNull(r.createFreeStyleProject().getAction(MyAction.class));
    }

    @TestExtension("addedToAbstractItem") public static class TestItemFactory extends TransientActionFactory<AbstractItem> {
        @Override public Class<AbstractItem> type() {
            return AbstractItem.class;
        }

        @Override public Class<MyAction> actionType() {
            return MyAction.class;
        }

        @Override public Collection<? extends MyAction> createFor(AbstractItem i) {
            return Set.of(new MyAction());
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

    @Test
    void laziness() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        // testing getAction(Class)
        assertNull(p.getAction(FoldableAction.class));
        assertEquals(0, LazyFactory.count);
        assertNotNull(p.getAction(ProminentProjectAction.class));
        assertEquals(1, LazyFactory.count);
        assertNotNull(p.getAction(MyProminentProjectAction.class));
        assertEquals(2, LazyFactory.count);
        LazyFactory.count = 0;
        // getAllActions
        List<? extends Action> allActions = p.getAllActions();
        assertEquals(1, LazyFactory.count);
        assertThat(Util.filter(allActions, FoldableAction.class), Matchers.iterableWithSize(0));
        assertThat(Util.filter(allActions, ProminentProjectAction.class), Matchers.iterableWithSize(1));
        assertThat(Util.filter(allActions, MyProminentProjectAction.class), Matchers.iterableWithSize(1));
        LazyFactory.count = 0;
        // getActions(Class)
        assertThat(p.getActions(FoldableAction.class), Matchers.iterableWithSize(0));
        assertEquals(0, LazyFactory.count);
        assertThat(p.getActions(ProminentProjectAction.class), Matchers.iterableWithSize(1));
        assertEquals(1, LazyFactory.count);
        assertThat(p.getActions(MyProminentProjectAction.class), Matchers.iterableWithSize(1));
        assertEquals(2, LazyFactory.count);
        LazyFactory.count = 0;
        // different context type
        MockFolder d = r.createFolder("d");
        assertNull(d.getAction(FoldableAction.class));
        assertNull(d.getAction(ProminentProjectAction.class));
        allActions = d.getAllActions();
        assertThat(Util.filter(allActions, FoldableAction.class), Matchers.iterableWithSize(0));
        assertThat(Util.filter(allActions, ProminentProjectAction.class), Matchers.iterableWithSize(0));
        assertThat(d.getActions(FoldableAction.class), Matchers.iterableWithSize(0));
        assertThat(d.getActions(ProminentProjectAction.class), Matchers.iterableWithSize(0));
        assertEquals(0, LazyFactory.count);
    }

    @SuppressWarnings("rawtypes")
    @TestExtension("laziness") public static class LazyFactory extends TransientActionFactory<AbstractProject> {
        static int count;

        @Override public Class<AbstractProject> type() {
            return AbstractProject.class;
        }

        @Override public Class<? extends Action> actionType() {
            return ProminentProjectAction.class;
        }

        @Override public Collection<? extends Action> createFor(AbstractProject p) {
            count++;
            return Set.of(new MyProminentProjectAction());
        }
    }

    @Test
    void compatibility() throws Exception {
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
        assertThat(Util.filter(allActions, FoldableAction.class), Matchers.iterableWithSize(0));
        assertThat(Util.filter(allActions, ProminentProjectAction.class), Matchers.iterableWithSize(1));
        OldFactory.count = 0;
        // getActions(Class)
        assertThat(p.getActions(FoldableAction.class), Matchers.iterableWithSize(0));
        assertEquals(1, OldFactory.count);
        assertThat(p.getActions(ProminentProjectAction.class), Matchers.iterableWithSize(1));
        assertEquals(2, OldFactory.count);
    }

    @TestExtension("compatibility") public static class OldFactory extends TransientActionFactory<FreeStyleProject> {
        static int count;

        @Override public Class<FreeStyleProject> type() {
            return FreeStyleProject.class;
    }

        @Override public Collection<? extends Action> createFor(FreeStyleProject p) {
            count++;
            return Set.of(new MyProminentProjectAction());
        }
    }

    @Issue("JENKINS-51584")
    @Test
    void transientActionsAreNotPersistedOnQueueItems() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild build = r.buildAndAssertSuccess(p);
        // MyProminentProjectAction is only added via the TransientActionFactory and should never be persisted.
        assertThat(Util.filter(build.getActions(), MyProminentProjectAction.class), is(empty()));
        assertThat(Util.filter(build.getAllActions(), MyProminentProjectAction.class), hasSize(1));
    }

    /**
     * Transient actions appear first, actions added to an Actionable appear last
     */
    @Test
    void ordering() throws IOException, ExecutionException, InterruptedException {
        var project = r.createFreeStyleProject();
        var build = project.scheduleBuild2(0).get();
        build.addAction(new InvisibleAction() {});

        var actions = build.getActions(InvisibleAction.class);

        assertThat(actions, hasSize(2));
        assertThat(actions.get(0), instanceOf(MyProminentProjectAction.class));
        assertThat(actions.get(1), instanceOf(InvisibleAction.class));
    }

    @TestExtension({"transientActionsAreNotPersistedOnQueueItems", "ordering"})
    public static class AllFactory extends TransientActionFactory<Actionable> {

        @Override
        public Class<Actionable> type() {
            return Actionable.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Actionable target) {
            return Set.of(new MyProminentProjectAction());
        }
    }

    private static class MyProminentProjectAction extends InvisibleAction implements ProminentProjectAction {

        private String allocation;

        MyProminentProjectAction() {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            new Exception("MyProminentProjectAction allocated at: ").printStackTrace(pw);
            allocation = sw.toString();
        }

        @Override
        public String toString() {
            return allocation;
        }
    }
}
