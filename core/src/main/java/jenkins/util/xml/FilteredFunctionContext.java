
/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc. All rights reserved.
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
package jenkins.util.xml;

import org.jaxen.Function;
import org.jaxen.FunctionContext;
import org.jaxen.UnresolvableException;
import org.jaxen.XPathFunctionContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * {@link org.jaxen.FunctionContext} that removes some {@link org.dom4j.XPath}
 * function names that are deemed bad as user input.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 * @see org.jaxen.FunctionContext
 * @see org.dom4j.XPath
 * @see hudson.model.Api
 */
@Restricted(NoExternalUse.class)
public class FilteredFunctionContext implements FunctionContext {

    /**
     * Default set of "bad" function names.
     */
    private static final Set<String> DEFAULT_ILLEGAL_FUNCTIONS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("document")
    ));
    private final FunctionContext base;
    private final Set<String> illegalFunctions;

    public FilteredFunctionContext(Set<String> illegalFunctions) {
        this.illegalFunctions = illegalFunctions;
        base = XPathFunctionContext.getInstance();
    }

    public FilteredFunctionContext() {
        this(DEFAULT_ILLEGAL_FUNCTIONS);
    }

    @Override
    public Function getFunction(String namespaceURI, String prefix, String localName) throws UnresolvableException {
        if (localName != null && illegalFunctions.contains(localName.toLowerCase(Locale.ENGLISH))) {
            throw new UnresolvableException("Illegal function: " + localName);
        }
        return base.getFunction(namespaceURI, prefix, localName);
    }
}
