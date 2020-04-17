package jenkins.util;

import org.apache.commons.beanutils.Converter;
import org.kohsuke.stapler.QueryParameter;

import java.util.concurrent.TimeUnit;
import edu.umd.cs.findbugs.annotations.CheckForNull;

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

    /**
     * Returns the duration of this instance in <em>milliseconds</em>.
     * @deprecated use {@link #getTimeInMillis()} instead.
     *
     * This method has always returned a time in milliseconds, when various callers incorrectly assumed seconds.
     * And this spread through the codebase. So this has been deprecated for clarity in favour of more explicitly named
     * methods.
     */
    @Deprecated
    public int getTime() {
        return (int)millis;
    }

    /**
     * Returns the duration of this instance in <em>milliseconds</em>.
     */
    public long getTimeInMillis() {
        return millis;
    }

    /**
     * Returns the duration of this instance in <em>seconds</em>.
     * @since 2.82
     */
    public int getTimeInSeconds() {
        return (int) (millis / 1000L);
    }


    public long as(TimeUnit t) {
        return t.convert(millis,TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@link TimeDuration} from the delay passed in parameter
     * @param delay the delay either in milliseconds without unit, or in seconds if suffixed by sec or secs.
     * @return the TimeDuration created from the delay expressed as a String.
     */
    @CheckForNull
    public static TimeDuration fromString(@CheckForNull String delay) {
        if (delay == null) {
            return null;
        }

        long unitMultiplier = 1L;
        delay = delay.trim();
        try {
            // TODO: more unit handling
            if (delay.endsWith("sec") || delay.endsWith("secs")) {
                delay = delay.substring(0, delay.lastIndexOf("sec"));
                delay = delay.trim();
                unitMultiplier = 1000L;
            }
            return new TimeDuration(Long.parseLong(delay.trim()) * unitMultiplier);
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
