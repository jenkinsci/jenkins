/*
 * The MIT License
 *
 * Copyright (c) 2025, Jan Faracik
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

import hudson.model.Action;
import hudson.model.Actionable;

/**
 * Represents a tab element shown on {@link Actionable} views.
 * <p>
 * A {@code Tab} is an {@link Action} that can be attached to an {@link Actionable} object
 * (such as a job or build) and displayed as a separate tab in the UI.
 * </p>
 *
 * <p>
 * Tabs may also implement {@link Badgeable} to display a visual badge associated
 * with the tab’s action
 * </p>
 *
 * @since 2.532
 */
public abstract class Tab implements Action, Badgeable {

    protected transient Actionable object;

    public Tab(Actionable object) {
        this.object = object;
    }

    public Actionable getObject() {
        return object;
    }
}
