package jenkins.util;

import org.apache.commons.beanutils.Converter;
import org.kohsuke.stapler.QueryParameter;

import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;

/**
 * Represents a length of something, like {@code 3sec}.
 *
 * This supports parameter injection, such as via {@link QueryParameter}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.489
 */
public class TimeDuration {
    private final long millis;

    public TimeDuration(long millis) {
        this.millis = millis;
    }

    public int getTime() {
        return (int)millis;
    }

    public long getTimeInMillis() {
        return millis;
    }

    public long as(TimeUnit t) {
        return t.convert(millis,TimeUnit.MILLISECONDS);
    }

    public static @CheckForNull TimeDuration fromString(@CheckForNull String delay) {
        if (delay==null)
            return null;

        try {
            // TODO: more unit handling
            if(delay.endsWith("sec"))   delay=delay.substring(0,delay.length()-3);
            if(delay.endsWith("secs"))  delay=delay.substring(0,delay.length()-4);
            return new TimeDuration(Long.parseLong(delay));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid time duration value: "+delay);
        }
    }

    public static class StaplerConverterImpl implements Converter {
        public Object convert(Class type, Object value) {
            if (value==null)
                return null;
            if (value instanceof String) {
                return fromString((String) value);
            }
            throw new UnsupportedOperationException();
        }
    }
}
