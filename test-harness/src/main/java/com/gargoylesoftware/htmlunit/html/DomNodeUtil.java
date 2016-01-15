/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package com.gargoylesoftware.htmlunit.html;

import com.gargoylesoftware.htmlunit.WebClientUtil;
import com.gargoylesoftware.htmlunit.html.xpath.XPathUtils;

import java.util.List;

/**
 * {@link DomNode} helper methods.
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DomNodeUtil {

    /**
     * Evaluates an XPath expression from the specified node, returning the resultant nodes.
     * <p>
     * Calls {@link WebClientUtil#waitForJSExec(com.gargoylesoftware.htmlunit.WebClient)} before
     * executing the query.
     * 
     * @param domNode the node to start searching from
     * @param xpathExpr the XPath expression
     * @return the list of objects found.
     */
    public static <E> List<E> selectNodes(final DomNode domNode, final String xpathExpr) {
        WebClientUtil.waitForJSExec(domNode.getPage().getWebClient());
        return (List) XPathUtils.getByXPath(domNode, xpathExpr, null);
    }

    /**
     * Evaluates the specified XPath expression from this node, returning the first matching element,
     * or <tt>null</tt> if no node matches the specified XPath expression.
     * <p>
     * Calls {@link WebClientUtil#waitForJSExec(com.gargoylesoftware.htmlunit.WebClient)} before
     * executing the query.
     *
     * @param domNode the node to start searching from
     * @param xpathExpr the XPath expression
     * @return the first element matching the specified XPath expression
     */
    public static <X> X selectSingleNode(final DomNode domNode, final String xpathExpr) {
        WebClientUtil.waitForJSExec(domNode.getPage().getWebClient());
        return domNode.<X>getFirstByXPath(xpathExpr);
    }
}
