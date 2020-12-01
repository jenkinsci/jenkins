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
package org.jenkins.ui.icon;

import org.apache.commons.jelly.JellyContext;
import org.kohsuke.stapler.Stapler;

/**
 * Icon type.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.0
 */
public enum IconType {
    CORE,
    PLUGIN,;

    /**
     * Qualify the supplied icon url.
     * <br>
     * Qualifying the URL involves prefixing it depending on whether the icon is a core or plugin icon.
     *
     * @param url The url to be qualified.
     * @param context The JellyContext.
     * @return The qualified icon url.
     */
    public String toQualifiedUrl(String url, JellyContext context) {
        String resURL = context.getVariable("resURL").toString();

        switch (this) {
            case CORE: {
                return resURL + "/images/" + url;
            }
            case PLUGIN: {
                return resURL + "/plugin/" + url;
            }
        }

        return null;
    }
}
