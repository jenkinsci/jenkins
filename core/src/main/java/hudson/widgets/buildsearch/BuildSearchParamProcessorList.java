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
package hudson.widgets.buildsearch;

import hudson.model.Queue;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * Wrapper class for the list of {@link hudson.widgets.buildsearch.BuildSearchParamProcessor} needed to process/apply
 * a set of {@link hudson.widgets.buildsearch.BuildSearchParams}.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class BuildSearchParamProcessorList {

    private final ArrayList<BuildSearchParamProcessor> processors;

    /**
     * Create a {@link BuildSearchParamProcessorList} instance from a set of {@link hudson.widgets.buildsearch.BuildSearchParams}.
     * @param searchParams The search parameters to use for creating the {@link BuildSearchParamProcessorList}.
     */
    public BuildSearchParamProcessorList(@Nonnull BuildSearchParams searchParams) {
        processors = new ArrayList<BuildSearchParamProcessor>();
        for (BuildSearchParamProcessorFactory factory : BuildSearchParamProcessorFactory.all()) {
            BuildSearchParamProcessor processor = factory.createProcessor(searchParams);
            if (processor != null) {
                processors.add(processor);
            }
        }
    }

    public ArrayList<BuildSearchParamProcessor> getProcessors() {
        return processors;
    }

    /**
     * Does the supplied {@link hudson.model.Queue.Item} fit the {@link BuildSearchParams} used to create this
     * {@link BuildSearchParamProcessorList} instance.
     * @param item The {@link hudson.model.Queue.Item} to test.
     * @return {@code true} if the {@link hudson.model.Queue.Item} fits all of the {@link BuildSearchParamProcessor}s
     * on this {@link BuildSearchParamProcessorList}, otherwise {@code false}.
     */
    public boolean fitsSearchParams(@Nonnull Queue.Item item) {
        for (BuildSearchParamProcessor processor : processors) {
            if (!processor.fitsSearchParams(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Does the supplied {@link hudson.model.Run} fit the {@link BuildSearchParams} used to create this
     * {@link BuildSearchParamProcessorList} instance.
     * @param run The {@link hudson.model.Run} to test.
     * @return {@code true} if the {@link hudson.model.Run} fits all of the {@link BuildSearchParamProcessor}s
     * on this {@link BuildSearchParamProcessorList}, otherwise {@code false}.
     */
    public boolean fitsSearchParams(@Nonnull Run run) {
        for (BuildSearchParamProcessor processor : processors) {
            if (!processor.fitsSearchParams(run)) {
                return false;
            }
        }
        return true;
    }
}
