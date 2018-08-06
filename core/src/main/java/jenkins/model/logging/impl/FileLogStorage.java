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

import com.jcraft.jzlib.GZIPInputStream;
import hudson.AbortException;
import hudson.Functions;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleLogFilter;
import hudson.console.ConsoleNote;
import hudson.model.TaskListener;
import jenkins.model.logging.Loggable;
import org.apache.commons.lang.ArrayUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Legacy File Log storage implementation.
 * When used, logging always goes to a file on the naster side.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public class FileLogStorage extends StreamLogStorage {

    private static final Logger LOGGER = Logger.getLogger(FileLogStorage.class.getName());


    public FileLogStorage(Loggable loggable) {
        super(loggable);
    }

    @Override
    public OutputStream createOutputStream() throws IOException {
        File logFile = getLogFileOrFail(getOwner());
        return Files.newOutputStream(logFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Nonnull
    @Override
    public File getLogFile() throws IOException {
        return getLogFileOrFail(loggable);
    }

    @Nonnull
    public static File getLogFileOrFail(Loggable loggable) throws IOException {
        final File file = loggable.getLogFileCompatLocation();
        if (file == null) {
            throw new AbortException("File log compatibility layer is invoked for a loggable " +
                    "object which returned null for getLogFileCompatLocation(): " + loggable);
        }
        return file;
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

    @Override
    public AnnotatedLargeText overallLog() {
        final File logFile;
        try {
            logFile = getLogFileOrFail(getOwner());
        } catch (IOException ex) {
            return new BrokenAnnotatedLargeText(ex, getOwner().getCharset());
        }

        return new AnnotatedLargeText<Loggable>
                (logFile, getOwner().getCharset(), getOwner().isLoggingFinished(), getOwner());
    }

    @Override
    public AnnotatedLargeText stepLog(@CheckForNull String stepId, boolean b) {
        // Not supported, there is no default implementation for "step"
        return new BrokenAnnotatedLargeText(
                new UnsupportedOperationException(FileLogStorage.class.getName() + " does not support partial logs"),
                getOwner().getCharset()
        );
    }

    @Override
    public boolean deleteLog() throws IOException {
        File logFile = getLogFileOrFail(loggable);
        if (logFile.exists()) {
            try {
                Files.delete(logFile.toPath());
            } catch (Exception ex) {
                throw new IOException("Failed to delete " + logFile, ex);
            }
        } else {
            LOGGER.log(Level.FINE, "Trying to delete Log File of {0} which does not exist: {1}",
                    new Object[] {loggable, logFile});
        }
        return true;
    }

    @Override
    public InputStream getLogInputStream() throws IOException {
        File logFile = getLogFileOrFail(loggable);

        if (logFile.exists() ) {
            // Checking if a ".gz" file was return
            try {
                InputStream fis = Files.newInputStream(logFile.toPath());
                if (logFile.getName().endsWith(".gz")) {
                    return new GZIPInputStream(fis);
                } else {
                    return fis;
                }
            } catch (InvalidPathException e) {
                throw new IOException(e);
            }
        }

        String message = "No such file: " + logFile;
        return new ByteArrayInputStream(message.getBytes(getOwner().getCharset()));
    }


    @Override
    public List<String> getLog(int maxLines) throws IOException {
        if (maxLines == 0) {
            return Collections.emptyList();
        }
        int lines = 0;
        long filePointer;
        final List<String> lastLines = new ArrayList<>(Math.min(maxLines, 128));
        final List<Byte> bytes = new ArrayList<>();

        try (RandomAccessFile fileHandler = new RandomAccessFile(
                getLogFileOrFail(loggable), "r")) {
            long fileLength = fileHandler.length() - 1;

            for (filePointer = fileLength; filePointer != -1 && maxLines != lines; filePointer--) {
                fileHandler.seek(filePointer);
                byte readByte = fileHandler.readByte();

                if (readByte == 0x0A) {
                    if (filePointer < fileLength) {
                        lines = lines + 1;
                        lastLines.add(convertBytesToString(bytes, getOwner().getCharset()));
                        bytes.clear();
                    }
                } else if (readByte != 0xD) {
                    bytes.add(readByte);
                }
            }
        }

        if (lines != maxLines) {
            lastLines.add(convertBytesToString(bytes, getOwner().getCharset()));
        }

        Collections.reverse(lastLines);

        // If the log has been truncated, include that information.
        // Use set (replaces the first element) rather than add so that
        // the list doesn't grow beyond the specified maximum number of lines.
        if (lines == maxLines) {
            lastLines.set(0, "[...truncated " + Functions.humanReadableByteSize(filePointer)+ "...]");
        }

        return ConsoleNote.removeNotes(lastLines);
    }

    private String convertBytesToString(List<Byte> bytes, Charset charset) {
        Collections.reverse(bytes);
        Byte[] byteArray = bytes.toArray(new Byte[bytes.size()]);
        return new String(ArrayUtils.toPrimitive(byteArray), charset);
    }
}
