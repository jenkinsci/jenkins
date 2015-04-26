/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
package jenkins.widgets.buildsearch;

import hudson.model.Queue;
import hudson.model.Run;

/**
 * Search param/term processor.
 *
 * <p>
 * Implementations created through a {@link BuildSearchParamProcessorFactory} implementation.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class BuildSearchParamProcessor<T> {

    /**
     * Does the supplied {@link Queue.Item} fit the {@link BuildSearchParams} used to create this
     *
     * <p>
     * Implementations should call {@link #fitsSearchParams(Object)} after extracting the appropriate data from
     * the {@link Queue.Item}.
     *
     * {@link BuildSearchParamProcessor} instance.
     * @param item The {@link Queue.Item} to test.
     * @return {@code true} if the {@link Queue.Item} fits, otherwise {@code false}.
     */
    public abstract boolean fitsSearchParams(Queue.Item item);

    /**
     * Does the supplied {@link Run} fit the {@link BuildSearchParams} used to create this
     *
     * <p>
     * Implementations should call {@link #fitsSearchParams(Object)} after extracting the appropriate data from
     * the {@link Run}.
     *
     * {@link BuildSearchParamProcessor} instance.
     * @param run The {@link Run} to test.
     * @return {@code true} if the {@link Run} fits, otherwise {@code false}.
     */
    public abstract boolean fitsSearchParams(Run run);

    /**
     * Does the supplied "data" (extracted from a {@link Queue.Item} or {@link Run}) fit the {@link BuildSearchParams}
     * used to create this {@link BuildSearchParamProcessor} instance.
     *
     * <p>
     * This method implementation should do all of the work in terms of testing the extracted {@link Queue.Item}
     * or {@link Run} data against the search criteria used to create the instance. The
     * {@link #fitsSearchParams(hudson.model.Queue.Item)} and {@link #fitsSearchParams(hudson.model.Run)}
     * implementations should call this function after extracting the appropriate data from the {@link Queue.Item}
     * or {@link Run}.
     *
     * <p>
     * This method makes unit testing of the {@link BuildSearchParamProcessor} implementation a little easier in
     * that it allows the implementation to be hidden in the parent {@link BuildSearchParamProcessorFactory factory}
     * class, plus allows testing without the need to create {@link Queue.Item} or {@link Run} instances, which can't
     * be created without spinning a {@link jenkins.model.Jenkins} instance via a JenkinsRule (which in turn requires
     * test classes to be in the test harness etc).
     *
     *
     * @param data The data to test.
     * @return {@code true} if the {@link Run} fits, otherwise {@code false}.
     */
    public abstract boolean fitsSearchParams(T data);
}
