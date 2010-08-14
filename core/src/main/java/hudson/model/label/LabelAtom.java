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
package hudson.model.label;

import hudson.model.Failure;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.util.VariableResolver;

/**
 * Atomic single token label, like "foo" or "bar".
 * 
 * @author Kohsuke Kawaguchi
 * @since  1.372
 */
public class LabelAtom extends Label {
    public LabelAtom(String name) {
        super(name);
    }

    @Override
    public boolean matches(VariableResolver<Boolean> resolver) {
        return resolver.resolve(name);
    }

    @Override
    public LabelOperatorPrecedence precedence() {
        return LabelOperatorPrecedence.ATOM;
    }

    /**
     * Obtains an atom by its {@linkplain #getName() name}.
     */
    public static LabelAtom get(String l) {
        return Hudson.getInstance().getLabelAtom(l);
    }

    public static boolean isValidName(String name) {
        try {
            Hudson.checkGoodName(name);
            return true;
        } catch (Failure failure) {
            return false;
        }
    }
}
