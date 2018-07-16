package jenkins.model.logging;

import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleNote;
import jenkins.util.io.OnMaster;

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

    public File getLogFile() throws IOException {
        //TODO: Push warnings to Telemetry API
        //Pipeline: LOGGER.log(Level.WARNING, "Avoid calling getLogFile on " + this,
        //        new UnsupportedOperationException());
        File f = File.createTempFile("deprecated", ".log", getOwner().getTmpDir());
        f.deleteOnExit();
        try (OutputStream os = new FileOutputStream(f)) {
            overallLog().writeRawLogTo(0, os);
        }
        return f;
    }

}
