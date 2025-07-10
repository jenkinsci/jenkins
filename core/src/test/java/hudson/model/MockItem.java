/*
 * The MIT License
 *
 * Copyright (c) 2013-2015, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.queue.CauseOfBlockage;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MockItem extends Queue.Item {

    public MockItem(long id) {
        super(new MockQueueTask(), Collections.emptyList(), id, null);
    }

    public MockItem(Queue.Task task) {
        super(task, Collections.emptyList(), -1, null);
    }

    public MockItem(Queue.Task task, List<Action> actions, long id) {
        super(task, actions, id, null);
    }

    /**
     * Override as needed.
     */
    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        return null;
    }

    /**
     * Override as needed.
     */
    @Override
    void enter(Queue q) {
    }

    /**
     * Override as needed.
     */
    @Override
    boolean leave(Queue q) {
        return true;
    }

    private static class MockQueueTask implements Queue.Task {
        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getFullDisplayName() {
            return null;
        }

        @NonNull
        @Override
        public String getUrl() {
            return "";
        }

        @Override
        public Queue.Executable createExecutable() {
            return null;
        }
    }
}
