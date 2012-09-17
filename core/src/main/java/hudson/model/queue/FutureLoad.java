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

/**
 * Estimated future load to Hudson.
 *
 * @author Kohsuke Kawaguchi
 * @see LoadPredictor
 */
public final class FutureLoad {
    /**
     * When is this load expected to start?
     */
    public final long startTime;
    /**
     * How many executors is this going to consume?
     */
    public final int numExecutors;
    /**
     * How long is task expected to continue, in milliseconds?
     */
    public final long duration;

    public FutureLoad(long startTime, long duration, int numExecutors) {
        this.startTime = startTime;
        this.numExecutors = numExecutors;
        this.duration = duration;
    }

    public String toString() {
        return "startTime="+startTime+",#executors="+numExecutors+",duration="+duration;
    }
}
