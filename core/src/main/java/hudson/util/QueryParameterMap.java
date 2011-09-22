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
package hudson.util;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the query string of the URL into a key/value pair.
 *
 * <p>
 * This class is even useful on the server side, as {@link HttpServletRequest#getParameter(String)}
 * can try to parse into the payload (and that can cause an exception if the payload is already consumed.
 * See HUDSON-8056.)
 *
 * <p>
 * So if you are handling the payload yourself and only want to access the query parameters,
 * use this class.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.394
 */
public class QueryParameterMap {
    private final Map<String,List<String>> store = new HashMap<String, List<String>>();

    /**
     * @param queryString
     *      String that looks like "abc=def&ghi=jkl"
     */
    public QueryParameterMap(String queryString) {
        if (queryString==null || queryString.length()==0)   return;
        try {
            for (String param : queryString.split("&")) {
                String[] kv = param.split("=");
                String key = URLDecoder.decode(kv[0], "UTF-8");
                String value = URLDecoder.decode(kv[1], "UTF-8");
                List<String> values = store.get(key);
                if (values == null)
                    store.put(key, values = new ArrayList<String>());
                values.add(value);
            }
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public QueryParameterMap(HttpServletRequest req) {
        this(req.getQueryString());
    }

    public String get(String name) {
        List<String> v = store.get(name);
        return v!=null?v.get(0):null;
    }

    public List<String> getAll(String name) {
        List<String> v = store.get(name);
        return v!=null? Collections.unmodifiableList(v) : Collections.<String>emptyList();
    }
}
