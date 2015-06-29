/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.queue.CauseOfBlockage;

/**
 * Indicates that a new build is blocked because the previous build is already in progress.
 * Useful for implementing {@link hudson.model.Queue.Task#getCauseOfBlockage} from a {@link Job} which supports {@link hudson.model.Queue.Task#isConcurrentBuild}.
 * @since TODO
 */
public class BlockedBecauseOfBuildInProgress extends CauseOfBlockage {
    
    private final Run<?, ?> build;

    public BlockedBecauseOfBuildInProgress(Run<?, ?> build) {
        this.build = build;
    }

    @Override public String getShortDescription() {
        Executor e = build.getExecutor();
        String eta = "";
        if (e != null) {
            eta = Messages.BlockedBecauseOfBuildInProgress_ETA(e.getEstimatedRemainingTime());
        }
        int lbn = build.getNumber();
        return Messages.BlockedBecauseOfBuildInProgress_shortDescription(lbn, eta);
    }

}
