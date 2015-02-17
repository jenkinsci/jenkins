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
package hudson.widgets.buildsearch.processors;

import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.widgets.buildsearch.BuildSearchParamProcessor;
import hudson.widgets.buildsearch.BuildSearchParamProcessorFactory;
import hudson.widgets.buildsearch.BuildSearchParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Search build history by {@link hudson.model.Run} result.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ResultProcessorFactory extends BuildSearchParamProcessorFactory {

    private static final String[] SEARCH_TERMS = new String[] {"result"}; // Build result. TODO: Could do "status" too or maybe that's different

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getSearchTerms() {
        return SEARCH_TERMS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildSearchParamProcessor createProcessor(BuildSearchParams searchParams) {
        List<BuildSearchParams.BuildSearchParam> resultParams = searchParams.getParams(SEARCH_TERMS[0]);

        if (resultParams.isEmpty()) {
            // "result" search term not specified in search
            return null;
        }

        final List<Result> interestingResults = new ArrayList<Result>();
        for (BuildSearchParams.BuildSearchParam resultParam : resultParams) {
            Result result = Result.fromString(resultParam.get());
            // Result.fromString returns Result.FAILURE if there's no match. We don't want that.
            if (result.toString().equalsIgnoreCase(resultParam.get())) {
                interestingResults.add(result);
            }
        }

        return new BuildSearchParamProcessor<Result>() {

            @Override
            public boolean fitsSearchParams(Result result) {
                // It fits if it's any of the specified "result" search terms.
                return interestingResults.contains(result);
            }

            @Override
            public boolean fitsSearchParams(Queue.Item item) {
                // Queue items don't have a Result.
                return false;
            }
            @Override
            public boolean fitsSearchParams(Run run) {
                return fitsSearchParams(run.getResult());
            }
        };
    }
}
