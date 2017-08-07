/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts,
 * Yahoo!, Inc.
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
package hudson.model.view.operations;


/**
 * Created by haswell on 8/7/17.
 *
 * Reusable headers and sets of headers
 */
public class Headers {

    public static String EXPIRES = "Expires";

    public static String PRAGMA = "Pragma";

    public static String CACHE_CONTROL = "Cache Control";


    /**
     * Indicate that a response must not be cached
     */
    public static final HeaderSet NO_CACHE = new HeaderSet(
            new Header(
                    CACHE_CONTROL,
                    Values.NO_CACHE,
                    Values.NO_STORE,
                    Values.MUST_REVALIDATE
            ),
            new Header(PRAGMA, Values.NO_CACHE),
            new Header(EXPIRES, "0")
    );


    static final class Values {

        static final String NO_CACHE = "no cache";

        static final String NO_STORE = "no store";

        static final String MUST_REVALIDATE  = "must revalidate";


    }
}
