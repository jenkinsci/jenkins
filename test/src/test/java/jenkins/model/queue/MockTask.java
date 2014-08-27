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

package jenkins.model.queue;

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.SubTask;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample queue task.
 */
public class MockTask extends AbstractQueueTask {

    public final AtomicInteger cnt;

    public MockTask(AtomicInteger cnt) {
        this.cnt = cnt;
    }
    
    @Override public boolean isBuildBlocked() {
        return false;
    }
    
    @Override public String getWhyBlocked() {
        return null;
    }
    
    @Override public String getName() {
        return "mock";
    }
    
    @Override public String getFullDisplayName() {
        return "Mock";
    }
    
    @Override public void checkAbortPermission() {}
    
    @Override public boolean hasAbortPermission() {
        return true;
    }
    
    @Override public String getUrl() {
        return "mock/";
    }
    
    @Override public String getDisplayName() {
        return "Mock";
    }
    
    @Override public Label getAssignedLabel() {
        return null;
    }
    
    @Override public Node getLastBuiltOn() {
        return null;
    }
    
    @Override public long getEstimatedDuration() {
        return -1;
    }
    
    @Override public ResourceList getResourceList() {
        return new ResourceList();
    }
    
    @Override public Queue.Executable createExecutable() throws IOException {
        return new Queue.Executable() {
            @Override public SubTask getParent() {
                return MockTask.this;
            }
            @Override public long getEstimatedDuration() {
                return -1;
            }
            @Override public void run() {
                cnt.incrementAndGet();
            }
        };
    }

}
