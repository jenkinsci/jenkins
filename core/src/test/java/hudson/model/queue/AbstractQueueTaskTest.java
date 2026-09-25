/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.Queue;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

@SuppressWarnings("deprecation")
class AbstractQueueTaskTest {

    @Issue("JENKINS-47517")
    @Test
    void causeOfBlockageOverrides() {
        Queue.Task t = new LegacyTask();
        assertFalse(t.isBuildBlocked());
        assertNull(t.getWhyBlocked());
        assertNull(t.getCauseOfBlockage());
    }

    static class LegacyTask extends AbstractQueueTask {
        @Override
        public boolean isBuildBlocked() {
            return getCauseOfBlockage() != null;
        }

        @Override
        public String getWhyBlocked() {
            CauseOfBlockage causeOfBlockage = getCauseOfBlockage();
            return causeOfBlockage != null ? causeOfBlockage.getShortDescription() : null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getFullDisplayName() {
            return null;
        }

        @Override
        public void checkAbortPermission() {
        }

        @Override
        public boolean hasAbortPermission() {
            return false;
        }

        @Override
        public String getUrl() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public Queue.Executable createExecutable() throws IOException {
            throw new IOException();
        }
    }

}
