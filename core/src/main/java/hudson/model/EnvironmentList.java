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
package hudson.model;

import java.util.AbstractList;
import java.util.List;

/**
 * List of {@link Environment}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.437
 */
public final class EnvironmentList extends AbstractList<Environment> {
    private final List<Environment> base;

    public EnvironmentList(List<Environment> base) {
        this.base = base;
    }

    @Override
    public Environment get(int index) {
        return base.get(index);
    }

    @Override
    public int size() {
        return base.size();
    }

    public <T extends Environment> T get(Class<T> type) {
        for (Environment e : this) {
            if (type.isInstance(e))
                return type.cast(e);
        }
        return null;
    }

    @Override
    public Environment set(int index, Environment element) {
        return base.set(index, element);
    }

    @Override
    public void add(int index, Environment element) {
        base.add(index, element);
    }

    @Override
    public Environment remove(int index) {
        return base.remove(index);
    }
}
