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
package jenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Fingerprint;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class TransientFingerprintFacetFactory implements ExtensionPoint {
    /**
     * Creates actions for the given project.
     *
     * @param target
     *      The project for which the action objects are requested. Never null.
     * @param result
     *      The created transient facets should be added to this collection. Never null.
     */
    public abstract void createFor(Fingerprint target, List<FingerprintFacet> result);

    /**
     * Returns all the registered {@link TransientFingerprintFacetFactory}s.
     */
    public static ExtensionList<TransientFingerprintFacetFactory> all() {
        return ExtensionList.lookup(TransientFingerprintFacetFactory.class);
    }
}
