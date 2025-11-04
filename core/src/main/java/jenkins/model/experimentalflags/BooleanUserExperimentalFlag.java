/*
 * The MIT License
 *
 * Copyright (c) 2022, CloudBees, Inc.
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

package jenkins.model.experimentalflags;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.util.SystemProperties;

/**
 * @since 2.395
 */
public abstract class BooleanUserExperimentalFlag extends UserExperimentalFlag<Boolean> {
    protected BooleanUserExperimentalFlag(@NonNull String flagKey) {
        super(flagKey);
    }

    @Override
    public @NonNull Boolean getDefaultValue() {
        return SystemProperties.getBoolean(getFlagKey() + ".defaultValue");
    }

    @Override
    public Object serializeValue(Boolean rawValue) {
        if (rawValue == null) {
            return null;
        }
        return rawValue ? "true" : "false";
    }

    @Override
    protected Boolean deserializeValue(Object serializedValue) {
        if (serializedValue.equals("true")) {
            return Boolean.TRUE;
        }
        if (serializedValue.equals("false")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
