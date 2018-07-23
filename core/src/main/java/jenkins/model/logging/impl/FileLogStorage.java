/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc. and other Jenkins contributors
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
