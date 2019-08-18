/*
 * The MIT License
 *
 * Copyright (c) 2012, Vincent Latombe.
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
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.util.Collection;
import java.util.Collections;

/**
 * Extension point for inserting transient {@link Action}s into {@link User}s.
 *
 * To register your implementation, put {@link Extension} on your subtype.
 *
 * @author Vincent Latombe
 * @since 1.477
 * @see Action
 */

public abstract class TransientUserActionFactory implements ExtensionPoint {
    /**
     * Creates actions for the given user.
     *
     * @param target for which the action objects are requested. Never null.
     * @return Can be empty but must not be null.
     */
    public Collection<? extends Action> createFor(User target) {
        return Collections.emptyList();
    }

    /**
     * Returns all the registered {@link TransientUserActionFactory}s.
     */
    public static ExtensionList<TransientUserActionFactory> all() {
        return ExtensionList.lookup(TransientUserActionFactory.class);
    }
}
