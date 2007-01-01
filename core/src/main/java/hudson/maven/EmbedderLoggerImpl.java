package hudson.maven;

import hudson.model.TaskListener;
import org.apache.maven.embedder.AbstractMavenEmbedderLogger;
import org.apache.maven.embedder.MavenEmbedderLogger;

import java.io.PrintStream;
import java.util.StringTokenizer;

/**
 * {@link MavenEmbedderLogger} implementation that
 * sends output to {@link TaskListener}.
 * 
 * @author Kohsuke Kawaguchi
 */
final class EmbedderLoggerImpl extends AbstractMavenEmbedderLogger {
    private final PrintStream logger;

    public EmbedderLoggerImpl(TaskListener listener) {
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
        print(message, throwable, LEVEL_DEBUG, "[DEBUG] ");
    }

    public void info(String message, Throwable throwable) {
        print(message, throwable, LEVEL_INFO, "[INFO ] ");
    }

    public void warn(String message, Throwable throwable) {
        print(message, throwable, LEVEL_WARN, "[WARN ] ");
    }

    public void error(String message, Throwable throwable) {
        print(message, throwable, LEVEL_ERROR, "[ERROR] ");
    }

    public void fatalError(String message, Throwable throwable) {
        print(message, throwable, LEVEL_FATAL, "[FATAL] ");
    }
}
