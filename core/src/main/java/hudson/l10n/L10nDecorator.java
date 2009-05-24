package hudson.l10n;

import hudson.model.PageDecorator;
import hudson.Extension;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class L10nDecorator extends PageDecorator {
    public L10nDecorator() {
        super(L10nDecorator.class);
    }
}
