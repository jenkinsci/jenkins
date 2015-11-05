package org.jvnet.hudson.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * {@link Formatter} that prints out milliseconds.
 *
 * Useful when one needs to understand sub-second timing issues.
 *
 * @author Kohsuke Kawaguchi
 */
public class MilliSecLogFormatter extends Formatter {
    private final Date dat = new Date();

    public synchronized String format(LogRecord record) {
        dat.setTime(record.getMillis());
        String source;
        if (record.getSourceClassName() != null) {
            source = record.getSourceClassName();
            if (record.getSourceMethodName() != null) {
               source += " " + record.getSourceMethodName();
            }
        } else {
            source = record.getLoggerName();
        }
        String message = formatMessage(record);
        String throwable = "";
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }
        return String.format("%1$tl:%1$tM:%1$tS.%1$tL %1$Tp %2$s%n%4$s: %5$s%6$s%n",
                             dat,
                             source,
                             record.getLoggerName(),
                             record.getLevel().getLocalizedName(),
                             message,
                             throwable);
    }
}
