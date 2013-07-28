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
package hudson.model;

import jenkins.model.RunAction2;

/**
 * @deprecated Use {@link RunAction2} instead: {@link #onLoad} does not work well with lazy loading if you are trying to persist the owner; and {@link #onBuildComplete} was never called.
 */
@Deprecated
public interface RunAction extends Action {
    /**
     * Called after the build is loaded and the object is added to the build list.
     * 
     * Because {@link RunAction}s are persisted with {@link Run}, the implementation
     * can keep a reference to {@link Run} in a field (which is set via {@link #onAttached(Run)})
     */
    void onLoad();

    /**
     * Called when the action is aded to the {@link Run} object.
     * @since 1.376
     */
    void onAttached(Run r);

    /**
     * Called after the build is finished.
     */
    void onBuildComplete();
}
