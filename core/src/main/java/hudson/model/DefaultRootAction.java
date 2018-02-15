/*
 * The MIT License
 * 
 * Copyright (c) 2018, CloudBees, Inc.
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

import hudson.Extension;

import javax.annotation.CheckForNull;

/**
 * Simply {@link RootAction} with default implementation of method.
 *
 * <p>
 * Extend from this interface and put {@link Extension} on your subtype
 * to have them auto-registered to {@link jenkins.model.Jenkins}.
 * 
 * <p>
 * Especially useful in test when you don't have to provide the methods returning null that
 * does not provide any useful information for the tests.
 *
 * @since TODO
 */
public interface DefaultRootAction extends RootAction {
    @Override
    default @CheckForNull String getIconFileName() {
        return null;
    }

    @Override
    default @CheckForNull String getDisplayName() {
        return null;
    }
}
