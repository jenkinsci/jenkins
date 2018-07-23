package jenkins.model.logging.impl;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.LoggingMethod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Legacy File Log storage implementation.
 * When used, logging always goes to a file on the naster side.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public class FileLogStorage extends StreamLoggingMethod implements FileLogCompatLayer {

    public FileLogStorage(Loggable loggable) {
        super(loggable);
    }

    @Override
    public OutputStream createOutputStream() throws IOException {
        File logFile = getLogFileOrFail(getOwner());
        return Files.newOutputStream(logFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @CheckForNull
    @Override
    public TaskListener createTaskListener() {
        return null;
    }

    @CheckForNull
    @Override
    public ConsoleLogFilter getExtraConsoleLogFilter() {
        return null;
    }
}
