package jenkins.util;

import org.jvnet.localizer.Localizable;

import java.util.Locale;

/**
 * {@link Localizable} implementation that actually doesn't localize.
 */
public class NonLocalizable extends Localizable {
    /**
     * The string that we don't know how to localize
     */
    private final String nonLocalizable;

    /**
     * Creates a non-localizable string.
     *
     * @param nonLocalizable the string.
     */
    public NonLocalizable(String nonLocalizable) {
        super(null, null);
        this.nonLocalizable = nonLocalizable;
    }

    @Override
    public String toString(Locale locale) {
        return nonLocalizable;
    }

    @Override
    public String toString() {
        return nonLocalizable;
    }
}
