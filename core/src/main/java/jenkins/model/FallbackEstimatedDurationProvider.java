/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins contributors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Job;

/**
 * Extension point allowing plugins to provide a fallback estimated build duration for a {@link Job}
 * when {@link Job#getEstimatedDuration()} has no completed build history to compute an estimate from.
 *
 * <p>Registered providers are consulted in {@link ExtensionList} order (highest {@link Extension#ordinal()} first)
 * only when {@link Job#getEstimatedDurationCandidates()} is empty. The first positive duration returned is used;
 * if no provider returns a positive duration, {@link Job#getEstimatedDuration()} returns {@code -1}.
 *
 * <p>{@link Job#getEstimatedDuration()} guards against re-entrant cycles on the current thread
 * (returning {@code -1} if the same job is re-entered), so implementations may safely delegate to a
 * related job's {@link Job#getEstimatedDuration()}.
 *
 * @see Job#getEstimatedDuration()
 * @since TODO
 */
public interface FallbackEstimatedDurationProvider extends ExtensionPoint {

    /**
     * Returns a fallback estimated build duration for the given job in milliseconds.
     *
     * @param job the job without completed build history
     * @return positive estimated duration in milliseconds, or {@code -1} (or any non-positive value)
     *         if this provider cannot supply an estimate for the job
     */
    long getEstimatedDuration(@NonNull Job<?, ?> job);

    /**
     * Returns all registered {@link FallbackEstimatedDurationProvider} implementations.
     *
     * @return registered providers in extension ordinal order
     */
    static @NonNull ExtensionList<FallbackEstimatedDurationProvider> all() {
        return ExtensionList.lookup(FallbackEstimatedDurationProvider.class);
    }
}
