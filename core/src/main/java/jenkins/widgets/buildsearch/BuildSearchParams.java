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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class BuildSearchParams {

    private final String searchString;
    private final Map<String, List<BuildSearchParam>> searchParamsSets = newParamMap();
    private boolean parsingComplete = false;

    private static Map<String, List<BuildSearchParam>> newParamMap() {
        Map<String, List<BuildSearchParam>> map = new LinkedHashMap<String, List<BuildSearchParam>>();
        for (String searchTerm: BuildSearchParamProcessorFactory.getAllSearchTerms()) {
            map.put(searchTerm, new ArrayList<BuildSearchParam>());
        }
        return Collections.unmodifiableMap(map);
    }

    public class BuildSearchParam {

        public String searchTerm;
        public String searchTermToken;

        private String param;
        // The start index (in this.searchString) of the search parameter (including the searchTermToken itself).
        int startIndex;
        // The end index (in this.searchString) of the search parameter, -1 being the end of the string.
        int endIndex = -1;

        BuildSearchParam(String searchTerm, String searchTermToken, int startIndex) {
            this.searchTerm = searchTerm;
            this.searchTermToken = searchTermToken;
            this.startIndex = startIndex;
        }

        public String get() {
            if (this.param != null) {
                return this.param;
            }

            String param = searchString.substring(getEndOfTokenIndex(), getParamEndIndex());
            param = param.trim();

            if (parsingComplete) {
                // We can cache the value if parsing is complete.
                this.param = param;
            }

            return param;
        }

        private boolean isWhitespace() {
            int paramStartIndex = getEndOfTokenIndex();
            int paramEndIndex = getParamEndIndex();
            for (int i = paramStartIndex; i < paramEndIndex; i++) {
                if (!Character.isWhitespace(searchString.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private int getEndOfTokenIndex() {
            return (startIndex + searchTermToken.length());
        }

        private int getParamEndIndex() {
            return (endIndex != -1 ? endIndex : searchString.length());
        }

        @Override
        public String toString() {
            return searchTerm + ":'" + get() + "'";
        }
    }

    public BuildSearchParams(@Nonnull String searchString) {
        this.searchString = searchString;
        if (searchString.trim().length() > 0) {
            parseSearchString();
        }
    }

    public List<BuildSearchParam> getParams(@Nonnull String searchTerm) {
        List<BuildSearchParam> params = searchParamsSets.get(searchTerm);
        if (params == null) {
            throw new IllegalArgumentException(String.format("Unknown search term '%s'.", searchTerm));
        }
        return params;
    }

    @Override
    public String toString() {
        return searchParamsSets.toString();
    }

    private void parseSearchString() {
        // Step 1: find all search params in the searchString, recording their startIndex
        List<BuildSearchParam> allSearchParams = new ArrayList<BuildSearchParam>();
        String searchStringLower = searchString.toLowerCase();
        for (String searchTerm : BuildSearchParamProcessorFactory.getAllSearchTerms()) {
            String searchTermToken = searchTerm + ":";

            int startIndex = 0;
            while((startIndex = searchStringLower.indexOf(searchTermToken, startIndex)) != -1) {
                allSearchParams.add(new BuildSearchParam(searchTerm, searchTermToken, startIndex));
                startIndex += searchTerm.length();
            }
        }

        // Step 2: Remove all empty/whitespace params i.e. token defined but no param

        // Step 2: sort all search params by their startIndex. This will then make it easy to find the endIndexes (step 3)
        Collections.sort(allSearchParams, new Comparator<BuildSearchParam>() {
            @Override
            public int compare(BuildSearchParam param1, BuildSearchParam param2) {
                return param1.startIndex - param2.startIndex;
            }
        });

        // Step 3: find the endIndex for each search param.
        // That's easy: it's the startIndex of the next search param, or the end of the searchString for the last.
        int numParams = allSearchParams.size();
        for (int i = 0; i < numParams; i++) {
            BuildSearchParam searchParam = allSearchParams.get(i);

            if (i == numParams - 1) {
                // last one, so leave endIndex as -1
            } else {
                BuildSearchParam nextSearchParam = allSearchParams.get(i + 1);
                searchParam.endIndex = nextSearchParam.startIndex;
            }

            // And add it to the top level searchParamsSets Map, but only if it's not whitespace.
            if (!searchParam.isWhitespace()) {
                searchParamsSets.get(searchParam.searchTerm).add(searchParam);
            }
        }

        parsingComplete = true;
    }
}
