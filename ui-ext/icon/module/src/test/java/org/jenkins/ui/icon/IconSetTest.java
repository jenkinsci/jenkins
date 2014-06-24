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
public class IconSetTest {

    @Test
    public void test() {
        test("icon-sm", "16x16", Icon.ICON_SMALL_STYLE);
        test("icon-md", "24x24", Icon.ICON_MEDIUM_STYLE);
        test("icon-lg", "32x32", Icon.ICON_LARGE_STYLE);
        test("icon-xlg", "48x48", Icon.ICON_XLARGE_STYLE);
    }

    @Test
    public void test_toNormalizedIconUrl() {
        Assert.assertEquals(null, IconSet.toNormalizedIconUrl((String)null));
        Assert.assertEquals("aaaa", IconSet.toNormalizedIconUrl("aaaa"));
        Assert.assertEquals("aaaa", IconSet.toNormalizedIconUrl("images/aaaa"));
        Assert.assertEquals("aaaa", IconSet.toNormalizedIconUrl("plugin/aaaa"));
        Assert.assertEquals("aaaa", IconSet.toNormalizedIconUrl("/images/aaaa"));
        Assert.assertEquals("aaaa", IconSet.toNormalizedIconUrl("/plugin/aaaa"));
    }

    private void test(String sizeClass, String sizeDims, String expectedStyle) {
        Icon icon;

        icon = IconSet.icons.getIconByClassSpec("icon-blue " + sizeClass);
        Assert.assertEquals(sizeDims + "/blue.png", icon.getUrl());
        icon = IconSet.icons.getIconByUrl(sizeDims + "/blue.png");
        Assert.assertEquals(".icon-blue." + sizeClass, icon.getNormalizedSelector());
        Assert.assertEquals(expectedStyle, icon.getStyle());

        icon = IconSet.icons.getIconByClassSpec("icon-blue-anime " + sizeClass);
        Assert.assertEquals(sizeDims + "/blue_anime.gif", icon.getUrl());
        icon = IconSet.icons.getIconByUrl(sizeDims + "/blue_anime.gif");
        Assert.assertEquals(".icon-blue-anime." + sizeClass, icon.getNormalizedSelector());
        Assert.assertEquals(expectedStyle, icon.getStyle());
    }
}
