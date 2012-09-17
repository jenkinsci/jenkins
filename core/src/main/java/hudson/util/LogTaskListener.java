/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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

package hudson.util;

import hudson.console.ConsoleNote;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@link TaskListener} which sends messages to a {@link Logger}.
 */
public class LogTaskListener extends AbstractTaskListener implements Serializable {
    
    private final TaskListener delegate;

    public LogTaskListener(Logger logger, Level level) {
        delegate = new StreamTaskListener(new LogOutputStream(logger, level, new Throwable().getStackTrace()[1]));
    }

    public PrintStream getLogger() {
        return delegate.getLogger();
    }

    public PrintWriter error(String msg) {
        return delegate.error(msg);
    }

    public PrintWriter error(String format, Object... args) {
        return delegate.error(format, args);
    }

    public PrintWriter fatalError(String msg) {
        return delegate.fatalError(msg);
    }

    public PrintWriter fatalError(String format, Object... args) {
        return delegate.fatalError(format, args);
    }

    public void annotate(ConsoleNote ann) {
        // no annotation support
    }

    public void close() {
        delegate.getLogger().close();
    }

    private static class LogOutputStream extends OutputStream {

        private final Logger logger;
        private final Level level;
        private final StackTraceElement caller;
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public LogOutputStream(Logger logger, Level level, StackTraceElement caller) {
            this.logger = logger;
            this.level = level;
            this.caller = caller;
        }

        public void write(int b) throws IOException {
            if (b == '\r' || b == '\n') {
                flush();
            } else {
                baos.write(b);
            }
        }

        @Override
        public void flush() throws IOException {
            if (baos.size() > 0) {
                LogRecord lr = new LogRecord(level, baos.toString());
                lr.setLoggerName(logger.getName());
                lr.setSourceClassName(caller.getClassName());
                lr.setSourceMethodName(caller.getMethodName());
                logger.log(lr);
            }
            baos.reset();
        }

        @Override
        public void close() throws IOException {
            flush();
        }

    }

    private static final long serialVersionUID = 1L;
}
