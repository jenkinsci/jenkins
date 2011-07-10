/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model.labels;

import hudson.model.Label;
import hudson.util.VariableResolver;

/**
 * Boolean expression of labels.
 * 
 * @author Kohsuke Kawaguchi
 * @since  1.372
 */
public abstract class LabelExpression extends Label {
    protected LabelExpression(String name) {
        super(name);
    }

    @Override
    public String getExpression() {
        return getDisplayName();
    }

    public static class Not extends LabelExpression {
        public final Label base;

        public Not(Label base) {
            super('!'+paren(LabelOperatorPrecedence.NOT,base));
            this.base = base;
        }

        @Override
        public boolean matches(VariableResolver<Boolean> resolver) {
            return !base.matches(resolver);
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onNot(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.NOT;
        }
    }

    /**
     * No-op but useful for preserving the parenthesis in the user input.
     */
    public static class Paren extends LabelExpression {
        public final Label base;

        public Paren(Label base) {
            super('('+base.getExpression()+')');
            this.base = base;
        }

        @Override
        public boolean matches(VariableResolver<Boolean> resolver) {
            return base.matches(resolver);
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onParen(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.ATOM;
        }
    }

    /**
     * Puts the label name into a parenthesis if the given operator will have a higher precedence.
     */
    static String paren(LabelOperatorPrecedence op, Label l) {
        if (op.compareTo(l.precedence())<0)
            return '('+l.getExpression()+')';
        return l.getExpression();
    }

    public static abstract class Binary extends LabelExpression {
        public final Label lhs,rhs;

        public Binary(Label lhs, Label rhs, LabelOperatorPrecedence op) {
            super(combine(lhs, rhs, op));
            this.lhs = lhs;
            this.rhs = rhs;
        }

        private static String combine(Label lhs, Label rhs, LabelOperatorPrecedence op) {
            return paren(op,lhs)+op.str+paren(op,rhs);
        }

        /**
         * Note that we evaluate both branches of the expression all the time.
         * That is, it behaves like "a|b" not "a||b"
         */
        @Override
        public boolean matches(VariableResolver<Boolean> resolver) {
            return op(lhs.matches(resolver),rhs.matches(resolver));
        }

        protected abstract boolean op(boolean a, boolean b);
    }

    public static final class And extends Binary {
        public And(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.AND);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return a && b;
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onAnd(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.AND;
        }
    }

    public static final class Or extends Binary {
        public Or(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.OR);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return a || b;
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onOr(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.OR;
        }
    }

    public static final class Iff extends Binary {
        public Iff(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.IFF);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return !(a ^ b);
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onIff(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.IFF;
        }
    }

    public static final class Implies extends Binary {
        public Implies(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.IMPLIES);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return !a || b;
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onImplies(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.IMPLIES;
        }
    }
}
