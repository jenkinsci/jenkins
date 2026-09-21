/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

package jenkins;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Enum that represents {@link Boolean} state (including null for the absence.)
 *
 * <p>
 * This is for situations where we can't use {@link Boolean}, such as annotation elements.
 *
 * @author Kohsuke Kawaguchi
 */
public enum YesNoMaybe {
    YES,
    NO,
    MAYBE;

    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "bridge method for backward compatibility")
    public static Boolean toBoolean(YesNoMaybe v) {
        if (v == null)    return null;
        return v.toBool();
    }

    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "bridge method for backward compatibility")
    public Boolean toBool() {
        return switch (this) {
            case YES -> true;
            case NO -> false;
            default -> null;
        };
    }
}
