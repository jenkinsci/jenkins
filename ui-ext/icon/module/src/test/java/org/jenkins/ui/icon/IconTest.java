/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Yahoo! Inc., Stephen Connolly, Tom Huybrechts, Alan Harder, Manufacture
 * Francaise des Pneumatiques Michelin, Romain Seguy
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
package org.jenkins.ui.icon;


import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class IconTest {

    @Test
    public void test_toNormalizedCSSSelector() {
        Assert.assertEquals(".a.b.c", Icon.toNormalizedCSSSelector(" b c   a"));
    }

    @Test
    public void test_toNormalizedIconNameClass() {
        Assert.assertEquals("icon-blue-anime", Icon.toNormalizedIconNameClass("blue_anime"));
        Assert.assertEquals("icon-blue-anime", Icon.toNormalizedIconNameClass("blue_anime.gif"));
        Assert.assertEquals("icon-blue", Icon.toNormalizedIconNameClass("blue.png"));
    }

    @Test
    public void test_toNormalizedIconSizeClass() {
        Assert.assertEquals("icon-sm", Icon.toNormalizedIconSizeClass("16x16"));
        Assert.assertEquals("xxx", Icon.toNormalizedIconSizeClass("xxx"));
    }
}
