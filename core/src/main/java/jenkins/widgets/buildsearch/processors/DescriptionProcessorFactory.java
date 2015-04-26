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
package jenkins.widgets.buildsearch.processors;

import hudson.model.Queue;
import hudson.model.Run;
import jenkins.widgets.buildsearch.BuildSearchParamProcessor;
import jenkins.widgets.buildsearch.BuildSearchParamProcessorFactory;
import jenkins.widgets.buildsearch.BuildSearchParams;

import java.util.List;

/**
 * Search build history by {@link hudson.model.Run} description.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DescriptionProcessorFactory extends BuildSearchParamProcessorFactory {

    /**
     * "desc" (description) search term.
     */
    public static final String DESC_ST = "desc";

    private static final String[] SEARCH_TERMS = new String[] {DESC_ST}; // Build description

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
        final List<BuildSearchParams.BuildSearchParam> descParams = searchParams.getParams(DESC_ST);

        if (descParams.isEmpty()) {
            // "desc" search term not specified in search
            return null;
        }

        return new BuildSearchParamProcessor<String>() {

            @Override
            public boolean fitsSearchParams(String description) {
                // It fits if it contains any of the specified "desc" search terms.
                for (BuildSearchParams.BuildSearchParam nameParam : descParams) {
                    if (description.contains(nameParam.get())) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean fitsSearchParams(Queue.Item item) {
                // Queue items don't have a description.
                return false;
            }
            @Override
            public boolean fitsSearchParams(Run run) {
                return fitsSearchParams(run.getDescription());
            }
        };
    }
}
