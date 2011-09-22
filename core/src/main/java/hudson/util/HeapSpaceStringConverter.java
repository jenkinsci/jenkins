/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;

/**
 * Up to XStream 1.3 the default {@link StringConverter} in XStream
 * used {@link String#intern()}, which stressed the
 * (rather limited) PermGen space with a large XML file.
 * 
 * Since XStream 1.3 it use a WeakHashMap cache to always use the same String instances, but
 * this has also major problems with a single long living XStream instance (as we have in Jenkins)
 * See http://jira.codehaus.org/browse/XSTR-604
 *
 * <p>
 * Use this to avoid that (instead those strings will
 * now be allocated to the heap space.)
 *
 * @author Kohsuke Kawaguchi
 */
public class HeapSpaceStringConverter extends AbstractSingleValueConverter {

    public boolean canConvert(Class type) {
        return type.equals(String.class);
    }

    public Object fromString(String str) {
        return str;
    }
}
