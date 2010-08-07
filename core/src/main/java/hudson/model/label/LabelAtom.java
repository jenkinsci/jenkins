package hudson.model.label;

import hudson.model.Hudson;
import hudson.model.Label;
import hudson.util.VariableResolver;

/**
 * Atomic single token label, like "foo" or "bar".
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.COMPOSITELABEL
 */
public class LabelAtom extends Label {
    public LabelAtom(String name) {
        super(name);
    }

    @Override
    public boolean matches(VariableResolver<Boolean> resolver) {
        return resolver.resolve(name);
    }

    /**
     * Obtains an atom by its {@linkplain #getName() name}.
     */
    public static LabelAtom get(String l) {
        return Hudson.getInstance().getLabelAtom(l);
    }
}
