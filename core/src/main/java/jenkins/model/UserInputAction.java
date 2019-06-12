/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hudson.model.InvisibleAction;
import hudson.model.ParameterValue;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class UserInputAction extends InvisibleAction implements Iterable<Map.Entry<String, Collection<ParameterValue>>> {

    private final Map<String, Collection<ParameterValue>> userInputValues;

    public UserInputAction(@Nonnull Collection<ParameterValue> inputValues) {
        this(ImmutableMap.of(Jenkins.getAuthentication().getName(), ImmutableList.copyOf(inputValues)));
    }

    public UserInputAction(@Nonnull Map<String, Collection<ParameterValue>> userInputValues) {
        ImmutableMap.Builder<String, Collection<ParameterValue>> builder = ImmutableMap.builder();
        for (Map.Entry<String, Collection<ParameterValue>> entry : userInputValues.entrySet()) {
            builder.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
        }
        this.userInputValues = builder.build();
    }

    @Override
    public @Nonnull Iterator<Map.Entry<String, Collection<ParameterValue>>> iterator() {
        return userInputValues.entrySet().iterator();
    }
}
