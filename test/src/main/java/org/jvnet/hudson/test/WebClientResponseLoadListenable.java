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
package org.jvnet.hudson.test;

import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebWindow;

import javax.annotation.Nonnull;

/**
 * Hook into the HtmlUnit {@link org.jvnet.hudson.test.JenkinsRule.WebClient}
 * response loading into a "window".
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public interface WebClientResponseLoadListenable {

    /**
     * Add a listener.
     * @param listener The listener.
     */
    void addResponseLoadListener(@Nonnull WebClientResponseLoadListener listener);

    /**
     * Removing a listener.
     * @param listener The listener.
     */
    void removeResponseLoadListener(@Nonnull WebClientResponseLoadListener listener);

    /**
     * Load listener interface.
     */
    public interface WebClientResponseLoadListener {
        /**
         * Response load event.
         * @param webResponse The response.
         * @param webWindow The window into which the response is being loaded.
         */
        void onLoad(@Nonnull WebResponse webResponse, @Nonnull WebWindow webWindow);
    }
}
