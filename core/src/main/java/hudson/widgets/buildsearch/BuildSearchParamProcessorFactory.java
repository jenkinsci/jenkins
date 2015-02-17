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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.widgets.buildsearch.processors.DateProcessorFactory;
import hudson.widgets.buildsearch.processors.DescriptionProcessorFactory;
import hudson.widgets.buildsearch.processors.NameProcessorFactory;
import hudson.widgets.buildsearch.processors.ResultProcessorFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementations of this class create {@link BuildSearchParamProcessor} instances from a set
 * of {@link BuildSearchParams}. The set of {@link BuildSearchParamProcessor} instances are then used
 * by the {@link hudson.widgets.HistoryPageFilter} class to filter the build history (via an instance of
 * {@link BuildSearchParamProcessorList}).
 *
 * <p>
 * Each {@link BuildSearchParamProcessor} implementation processes one or more search terms e.g. the
 * {@link BuildSearchParamProcessor} instance created by the {@link NameProcessorFactory} checks the
 * build {@link hudson.model.Queue.Item} or {@link hudson.model.Run} name using the "name:" search token(s)
 * provided in the search, while the {@link DateProcessorFactory} checks date using one or both of the
 * "date-from:" and "date-to:" tokens.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class BuildSearchParamProcessorFactory implements ExtensionPoint {

    private static List<BuildSearchParamProcessorFactory> coreParamProcessorFactories = new ArrayList<BuildSearchParamProcessorFactory>();

    static {
        // Statically adding the core BuildSearchParamProcessorFactory impls Vs adding via @Extension.
        // Makes testing easier coz no need to create a JenkinsRule instance + put in test harness etc.
        // Plugins etc can still contribute via @Extension.
        coreParamProcessorFactories.add(new NameProcessorFactory());
        coreParamProcessorFactories.add(new DescriptionProcessorFactory());
        coreParamProcessorFactories.add(new ResultProcessorFactory());
        coreParamProcessorFactories.add(new DateProcessorFactory());
    }

    /**
     * Get the list of search terms that this processor is interested in.
     *
     * @return List of search terms.
     */
    public abstract String[] getSearchTerms();

    /**
     * Create the {@link BuildSearchParamProcessor} instance using the supplied set of search parameters.
     *
     * @param searchParams The parsed search parameters.
     * @return The {@link BuildSearchParamProcessor} instance to be used for searching, or {@code null} if the search
     *         parameters are such that {@link BuildSearchParamProcessor} instance of this type is not needed.
     */
    public abstract @CheckForNull BuildSearchParamProcessor createProcessor(@Nonnull BuildSearchParams searchParams);

    public static List<BuildSearchParamProcessorFactory> all() {

        // TODO: Not convinced we want to bother with @Extension

        ExtensionList<BuildSearchParamProcessorFactory> extensions = ExtensionList.lookup(BuildSearchParamProcessorFactory.class);
        if (extensions.isEmpty()) {
            // No BuildSearchParamProcessorFactory defined by @Extension
            return coreParamProcessorFactories;
        } else {
            // One or more BuildSearchParamProcessorFactory defined by @Extension.
            List<BuildSearchParamProcessorFactory> combined = new ArrayList<BuildSearchParamProcessorFactory>(coreParamProcessorFactories);
            combined.addAll(extensions);
            return combined;
        }
    }

    public static Set<String> getAllSearchTerms() {
        Set<String> allSearchTerms = new LinkedHashSet<String>();
        for (BuildSearchParamProcessorFactory paramProcessorFactory : all()) {
            allSearchTerms.addAll(Arrays.asList(paramProcessorFactory.getSearchTerms()));
        }
        return allSearchTerms;
    }
}
