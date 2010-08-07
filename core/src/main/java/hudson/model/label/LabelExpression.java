package hudson.model.label;

import hudson.model.Label;
import hudson.util.VariableResolver;

/**
 * Boolean expression of labels.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.COMPOSITELABEL
 */
public abstract class LabelExpression extends Label {
    protected LabelExpression(String name) {
        super(name);
    }

    public static final class And extends LabelExpression {
        private final Label lhs,rhs;

        public And(Label lhs, Label rhs) {
            super(lhs.getName()+"&&"+rhs.getName());
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public boolean matches(VariableResolver<Boolean> resolver) {
            return lhs.matches(resolver) && rhs.matches(resolver);
        }
    }
}
