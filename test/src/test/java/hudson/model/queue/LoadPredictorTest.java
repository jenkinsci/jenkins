/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

package hudson.model.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.JobOffer;
import hudson.model.Queue.Task;
import hudson.model.Queue.WaitingItem;
import hudson.slaves.DumbSlave;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class LoadPredictorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @TestExtension
    public static class LoadPredictorImpl extends LoadPredictor {
        @Override
        public Iterable<FutureLoad> predict(MappingWorksheet plan, Computer computer, long start, long end) {
            return List.of(new FutureLoad(start + 5000, end - (start + 5000), 1));
        }
    }

    /**
     * Makes sure that {@link LoadPredictor} is taken into account when building {@link MappingWorksheet}.
     * The scenario is:
     *
     * - a computer with 1 executor, idle.
     * - a future load of size 1 is predicted
     * - hence the consideration of the current task at hand shall fail, as it'll collide with the estimated future load.
     */
    @Test
    void test1() throws Exception {
        Task t = mock(Task.class);
        when(t.getEstimatedDuration()).thenReturn(10000L);
        when(t.getSubTasks()).thenReturn((Collection) List.of(t));

        Computer c = createMockComputer(1);

        JobOffer o = createMockOffer(c.getExecutors().getFirst());

        MappingWorksheet mw = new MappingWorksheet(wrap(t), List.of(o));

        // the test load predictor should have pushed down the executor count to 0
        assertTrue(mw.executors.isEmpty());
        assertEquals(1, mw.works.size());
    }

    private BuildableItem wrap(Queue.Task t) {
        return new BuildableItem(new WaitingItem(new GregorianCalendar(), t, new ArrayList<>()));
    }

    private JobOffer createMockOffer(Executor e) {
        JobOffer o = mock(JobOffer.class);
        when(o.getExecutor()).thenReturn(e);
        return o;
    }

    private Computer createMockComputer(int nExecutors) throws Exception {
        Node n = mock(DumbSlave.class);
        Computer c = mock(Computer.class);
        when(c.getNode()).thenReturn(n);

        List executors = new CopyOnWriteArrayList();

        for (int i = 0; i < nExecutors; i++) {
            Executor e = mock(Executor.class);
            when(e.isIdle()).thenReturn(true);
            when(e.getOwner()).thenReturn(c);
            executors.add(e);
        }

        Field f = Computer.class.getDeclaredField("executors");
        f.setAccessible(true);
        f.set(c, executors);

        when(c.getExecutors()).thenReturn(executors);

        return c;
    }
}
