/*
 * The MIT License
 *
 * Copyright 2016-2018 CloudBees Inc., Google Inc.
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
package jenkins.model.logging;

import hudson.Launcher;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Run;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.export.Exported;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

/**
 * Defines logging method for Jenkins runs.
 * This method defines how the log is persisted to the disk.
 * @author Oleg Nenashev
 * @author Xing Yan
 * @see LogStorageFactory
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class LogStorage<T extends Loggable> {

    protected transient T loggable;

    public LogStorage(@Nonnull T loggable) {
        this.loggable = loggable;
    }

    @Exported
    public String getId() {
        return getClass().getName();
    }

    /**
     * Called when the owner is loaded from disk.
     * The owner may be persisted on the disk, so the build reference should be {@code transient} (quasi-{@code final}) and restored here.
     * @param loggable an owner to which this component is associated.
     */
    public void onLoad(@Nonnull T loggable) {
        this.loggable = loggable;
    }

    public static void onLoad(@Nonnull Loggable loggable, @CheckForNull LogStorage logStorage) {
        if (logStorage != null) {
            logStorage.onLoad(loggable);
        }
    }

    @Nonnull
    protected Loggable getOwner() {
        if (loggable == null) {
            throw new IllegalStateException("Owner has not been assigned to the object yet");
        }
        return loggable;

    }

    /**
     * Decorates logging on the Jenkins master side for non-{@link Run} loggable items.
     * @return Log filter on the master.
     *         {@code null} if the implementation does not support task logging
     * @throws IOException initialization error or wrong {@link Loggable} type
     * @throws InterruptedException one of the build listener decorators has been interrupted.
     */
    @CheckForNull
    public abstract TaskListener createTaskListener() throws IOException, InterruptedException;

    /**
     * Decorates logging on the Jenkins master side.
     * This method should be always implemented, because it will be consuming the input events.
     * Streams can be converted to per-line events by higher-level abstractions.
     *
     * @return Build Listener
     * @throws IOException initialization error or wrong {@link Loggable} type
     * @throws InterruptedException one of the build listener decorators has
     *            been interrupted.
     */
     @Nonnull
     public abstract BuildListener createBuildListener() throws IOException, InterruptedException;

    /**
     * Decorates external process launcher running on a node.
     * It may be overridden to redirect logs to external destination
     * instead of sending them by default to the master.
     * @param original Original launcher
     * @param run Run, for which the decoration should be performed
     * @param node Target node. May be {@code master} as well
     * @return Decorated launcher or {@code original} launcher
     */
    @Nonnull
    public Launcher decorateLauncher(@Nonnull Launcher original,
        @Nonnull Run<?,?> run, @Nonnull Node node) {
        return original;
    }

    /**
     * Gets log for an object.
     * @return Created log or {@link jenkins.model.logging.impl.BrokenAnnotatedLargeText} if it cannot be retrieved
     */
    @Nonnull
    public abstract AnnotatedLargeText<T> overallLog();

    //TODO: jglick requests justification of why it needs to be in the core
    /**
     * Gets log for a part of the object.
     * @param stepId Identifier of the step to be displayed.
     *               It may be Pipeline step or other similar abstraction
     * @param completed indicates that the step is completed
     * @return Created log or {@link jenkins.model.logging.impl.BrokenAnnotatedLargeText} if it cannot be retrieved
     */
    @Nonnull
    public abstract AnnotatedLargeText<T> stepLog(@CheckForNull String stepId, boolean completed);

    public abstract InputStream getLogInputStream() throws IOException;

    public @Nonnull Reader getLogReader() throws IOException {
        return new InputStreamReader(getLogInputStream(), getOwner().getCharset());
    }

    /**
     * Gets the entire log as text.
     * This method is a convenience implementation for legacy API users.
     * @return Entire log as a string
     * @throws IOException Failed to read logs
     * @deprecated Use methods like {@link #overallLog()}, {@link #getLog(int)} or {@link #getLogReader()} instead
     */
    @Deprecated
    public String getLog() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        overallLog().writeRawLogTo(0, baos);
        return baos.toString(loggable.getCharset().name());
    }

    public abstract List<String> getLog(int maxLines) throws IOException;

    /**
     * Gets log as a file.
     * This is a compatibility method, which is used in {@link Run#getLogFile()}.
     * Implementations may provide it, e.g. by creating temporary files if needed.
     * @return Log file. If it does not exist, {@link IOException} should be thrown
     * @throws IOException Log file cannot be retrieved
     * @deprecated The method is available for compatibility purposes only
     */
    @Deprecated
    @Nonnull
    public abstract File getLogFile() throws IOException;

    /**
     * Deletes the log in the storage.
     * @return {@code true} if the log was deleted.
     *         {@code false} if Log deletion is not supported.
     * @throws IOException Failed to delete the log.
     */
    public abstract boolean deleteLog() throws IOException;
}
