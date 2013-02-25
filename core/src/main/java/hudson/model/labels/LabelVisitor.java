package hudson.model.labels;

/**
 * Visitor pattern for {@link LabelExpression}.
 *
 * @author Kohsuke Kawaguchi
 * @see LabelExpression#accept(LabelVisitor, Object)
 * @since 1.420
 */
public abstract class LabelVisitor<V,P> {
    public abstract V onAtom(LabelAtom a, P param);

    public abstract V onParen(LabelExpression.Paren p, P param);

    public abstract V onNot(LabelExpression.Not p, P param);

    public abstract V onAnd(LabelExpression.And p, P param);

    public abstract V onOr(LabelExpression.Or p, P param);

    public abstract V onIff(LabelExpression.Iff p, P param);

    public abstract V onImplies(LabelExpression.Implies p, P param);
}
