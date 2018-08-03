/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleNote;
import hudson.model.Run;
import jenkins.util.io.OnMaster;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

/**
 * Defines how the logs should be browsed.
 * @author Oleg Nenashev
 * @author Jesse Glick
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class LogBrowser<T extends Loggable> extends LogHandler implements OnMaster {

    public LogBrowser(Loggable loggable) {
        super(loggable);
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

    public InputStream getLogInputStream() throws IOException {
        // Inefficient but probably rarely used anyway.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        overallLog().writeRawLogTo(0, baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    public Reader getLogReader() throws IOException {
        // As above.
        return overallLog().readAll();
    }

    @SuppressWarnings("deprecation")
    public String getLog() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        overallLog().writeRawLogTo(0, baos);
        return baos.toString("UTF-8");
    }

    public List<String> getLog(int maxLines) throws IOException {
        int lineCount = 0;
        List<String> logLines = new LinkedList<>();
        if (maxLines == 0) {
            return logLines;
        }
        try (BufferedReader reader = new BufferedReader(getLogReader())) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                logLines.add(line);
                ++lineCount;
                if (lineCount > maxLines) {
                    logLines.remove(0);
                }
            }
        }
        if (lineCount > maxLines) {
            logLines.set(0, "[...truncated " + (lineCount - (maxLines - 1)) + " lines...]");
        }
        return ConsoleNote.removeNotes(logLines);
    }

    /**
     * Gets log as a file.
     * This is a compatibility method, which is used in {@link Run#getLogFile()}.
     * {@link LogBrowser} implementations may provide it, e.g. by creating temporary files if needed.
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
