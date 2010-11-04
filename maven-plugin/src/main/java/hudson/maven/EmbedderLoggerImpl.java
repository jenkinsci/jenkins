/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.maven;

import hudson.model.TaskListener;

import java.io.PrintStream;
import java.util.StringTokenizer;

import org.apache.maven.cli.MavenLoggerManager;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

/**
 * {@link MavenEmbedderLogger} implementation that
 * sends output to {@link TaskListener}.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class EmbedderLoggerImpl extends MavenLoggerManager {
    private final PrintStream logger;

    public EmbedderLoggerImpl(TaskListener listener, int threshold) {
        super(new ConsoleLogger( threshold, "hudson-logger" ));
        logger = listener.getLogger();
    }

    private void print(String message, Throwable throwable, int threshold, String prefix) {
        if (getThreshold() <= threshold) {
            StringTokenizer tokens = new StringTokenizer(message,"\n");
            while(tokens.hasMoreTokens()) {
                logger.print(prefix);
                logger.println(tokens.nextToken());
            }

            if (throwable!=null)
                throwable.printStackTrace(logger);
        }
    }

    public void debug(String message, Throwable throwable) {
        print(message, throwable, Logger.LEVEL_DEBUG, "[DEBUG] ");
    }

    public void info(String message, Throwable throwable) {
        print(message, throwable, Logger.LEVEL_INFO, "[INFO ] ");
    }

    public void warn(String message, Throwable throwable) {
        print(message, throwable, Logger.LEVEL_WARN, "[WARN ] ");
    }

    public void error(String message, Throwable throwable) {
        print(message, throwable, Logger.LEVEL_ERROR, "[ERROR] ");
    }

    public void fatalError(String message, Throwable throwable) {
        print(message, throwable, Logger.LEVEL_FATAL, "[FATAL] ");
    }
}
