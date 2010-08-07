package hudson.model;

/**
 * Boolean expression of labels.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.COMPOSITELABEL
 */
public abstract class LabelExpression extends Label {


    public static final class And extends LabelExpression {
        private final Label lhs,rhs;
    }
}
