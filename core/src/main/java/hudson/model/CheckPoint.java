/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import hudson.tasks.BuildStep;
import hudson.tasks.Recorder;
import hudson.tasks.Builder;
import hudson.scm.SCM;
import javax.annotation.Nonnull;

/**
 * Provides a mechanism for synchronizing build executions in the face of concurrent builds.
 *
 * <p>
 * At certain points of a build, {@link BuildStep}s and other extension points often need
 * to refer to what happened in its earlier build. For example, a {@link SCM} check out
 * can run concurrently, but the changelog computation requires that the check out of the
 * earlier build has completed. Or if Hudson is sending out an e-mail, he needs to know
 * the result of the previous build, so that he can decide an e-mail is necessary or not.
 *
 * <p>
 * Check pointing is a primitive mechanism to provide this sort of synchronization.
 * These methods can be only invoked from {@link Executor} threads.
 *
 * <p>
 * Each {@link CheckPoint} instance represents unique check points. {@link CheckPoint}
 * instances are normally created as a static instance, because two builds of the same project
 * needs to refer to the same check point instance for synchronization to happen properly.
 *
 * <p>
 * This class defines a few well-known check point instances. plugins can define
 * their additional check points for their own use.
 *
 * <p>Note that not all job types support checkpoints.
 *
 * <h2>Example</h2>
 * <p>
 * {@code JUnitResultArchiver} provides a good example of how a {@link Recorder} can
 * depend on its earlier result.
 *
 * @author Kohsuke Kawaguchi
 * @see BuildStep#getRequiredMonitorService() 
 * @since 1.319
 */
public final class CheckPoint {
    private final Object identity;
    private final String internalName;

    /**
     * For advanced uses. Creates a check point that uses the given object as its identity.
     */
    public CheckPoint(String internalName, Object identity) {
        this.internalName = internalName;
        this.identity = identity;
    }

    /**
     * @param internalName
     *      Name of this check point that's used in the logging, stack traces, debug messages, and so on.
     *      This is not displayed to users. No need for i18n.
     */
    public CheckPoint(String internalName) {
        this(internalName, new Object());
    }

    @Override
    public boolean equals(Object that) {
        if (that == null || getClass() != that.getClass()) return false;
        return identity== ((CheckPoint) that).identity;
    }

    @Override
    public int hashCode() {
        return identity.hashCode();
    }

    @Override
    public String toString() {
        return "Check point "+internalName;
    }

    /**
     * Records that the execution of the build has reached to a check point, idenified
     * by the given identifier.
     *
     * <p>
     * If the successive builds are {@linkplain #block() waiting for this check point},
     * they'll be released.
     *
     * <p>
     * This method can be only called from an {@link Executor} thread.
     */
    public void report() {
        Run.reportCheckpoint(this);
    }

    /**
     * Waits until the previous build in progress reaches a check point, identified
     * by the given identifier, or until the current executor becomes the youngest build in progress.
     *
     * <p>
     * Note that "previous build in progress" should be interpreted as "previous (build in progress)" instead of
     * "(previous build) if it's in progress". This makes a difference around builds that are aborted or
     * failed very early without reporting the check points. Imagine the following time sequence:
     *
     * <ol>
     * <li>Build #1, #2, and #3 happens around the same time
     * <li>Build #3 waits for check point {@code JUnitResultArchiver}
     * <li>Build #2 aborts before getting to that check point
     * <li>Build #1 finally checks in {@code JUnitResultArchiver}
     * </ol>
     *
     * <p>
     * Using this method, build #3 correctly waits until the step 4. Because of this behavior,
     * the {@link #report()}/{@link #block()} pair can normally
     * be used without a try/finally block.
     *
     * <p>
     * This method can be only called from an {@link Executor} thread.
     *
     * @throws InterruptedException
     *      If the build (represented by the calling executor thread) is aborted while it's waiting.  
     */
    public void block() throws InterruptedException {
        Run.waitForCheckpoint(this, null, null);
    }

    /**
     * Like {@link #block()} but allows for richer logging.
     * @param listener an optional listener to which
     * @param waiter a description of what component is requesting the wait, such as {@link Descriptor#getDisplayName}
     * @throws InterruptedException if the build is aborted while waiting
     * @since 1.528
     */
    public void block(@Nonnull BuildListener listener, @Nonnull String waiter) throws InterruptedException {
        Run.waitForCheckpoint(this, listener, waiter);
    }

    /**
     * {@link CheckPoint} that indicates that {@link AbstractBuild#getCulprits()} is computed.
     */
    public static final CheckPoint CULPRITS_DETERMINED = new CheckPoint("CULPRITS_DETERMINED");
    /**
     * {@link CheckPoint} that indicates that the build is completed.
     * ({@link AbstractBuild#isBuilding()}==false)
     */
    public static final CheckPoint COMPLETED = new CheckPoint("COMPLETED");
    /**
     * {@link CheckPoint} that indicates that the build has finished executing the "main" portion
     * ({@link Builder}s in case of {@link FreeStyleProject}) and now moving on to the post-build
     * steps.
     */
    public static final CheckPoint MAIN_COMPLETED = new CheckPoint("MAIN_COMPLETED");
}
