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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper class for the list of {@link jenkins.widgets.buildsearch.BuildSearchParamProcessor} needed to process/apply
 * a set of {@link jenkins.widgets.buildsearch.BuildSearchParams}.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class BuildSearchParamProcessorList extends BuildSearchParamProcessor {

    private final String searchString;
    private final List<BuildSearchParamProcessor> processors;

    /**
     * Create a {@link BuildSearchParamProcessorList} instance from a set of {@link jenkins.widgets.buildsearch.BuildSearchParams}.
     * @param searchParams The search parameters to use for creating the {@link BuildSearchParamProcessorList}.
     */
    public BuildSearchParamProcessorList(@Nonnull BuildSearchParams searchParams) {
        this.searchString = searchParams.getSearchString().toLowerCase();
        if (searchParams.size() != 0) {
            processors = new ArrayList();
            for (BuildSearchParamProcessorFactory factory : BuildSearchParamProcessorFactory.all()) {
                BuildSearchParamProcessor processor = factory.createProcessor(searchParams);
                if (processor != null) {
                    processors.add(processor);
                }
            }
        } else {
            processors = Collections.emptyList();
        }
    }

    public List<BuildSearchParamProcessor> getProcessors() {
        return processors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean fitsSearchParams(@Nonnull Queue.Item item) {
        if (!processors.isEmpty()) {
            for (BuildSearchParamProcessor processor : processors) {
                if (!processor.fitsSearchParams(item)) {
                    return false;
                }
            }
            // All the selected processors "liked" the search term. 
            return true;
        } else {
            if (fitsSearchParams(item.getDisplayName())) {
                return true;
            } else if (fitsSearchParams(item.getId())) {
                return true;
            }
            // Non of the fuzzy matches "liked" the search term. 
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean fitsSearchParams(@Nonnull Run run) {
        if (!processors.isEmpty()) {
            for (BuildSearchParamProcessor processor : processors) {
                if (!processor.fitsSearchParams(run)) {
                    return false;
                }
            }
            // All the selected processors "liked" the search term. 
            return true;
        } else {
            if (fitsSearchParams(run.getDisplayName())) {
                return true;
            } else if (fitsSearchParams(run.getDescription())) {
                return true;
            } else if (fitsSearchParams(run.getNumber())) {
                return true;
            } else if (fitsSearchParams(run.getQueueId())) {
                return true;
            } else if (fitsSearchParams(run.getResult())) {
                return true;
            }
            // Non of the fuzzy matches "liked" the search term. 
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean fitsSearchParams(Object data) {
        if (data != null) {
            if (data instanceof Number) {
                return data.toString().equals(searchString);
            } else {
                return data.toString().toLowerCase().contains(searchString);
            }
        }
        return false;
    }
}
