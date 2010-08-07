package hudson.model;

/**
 * Atomic single token label, like "foo" or "bar".
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.COMPOSITELABEL
 */
public class LabelToken extends Label {
    public LabelToken(String name) {
        super(name);
    }
}
