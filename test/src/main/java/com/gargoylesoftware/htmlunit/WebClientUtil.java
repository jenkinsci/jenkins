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
package com.gargoylesoftware.htmlunit;

import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;
import org.junit.Assert;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * {@link WebClient} helper methods.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class WebClientUtil {

    /**
     * Wait for all async JavaScript tasks associated with the supplied {@link WebClient} instance
     * to complete.
     * <p>
     * Waits for 10 seconds before timing out.
     * @param webClient The {@link WebClient} instance.
     */
    public static void waitForJSExec(WebClient webClient) {
        waitForJSExec(webClient, 10000);
    }

    /**
     * Wait for all async JavaScript tasks associated with the supplied {@link WebClient} instance
     * to complete.
     * @param webClient The {@link WebClient} instance.
     * @param timeout The timeout in milliseconds.                  
     */
    public static void waitForJSExec(WebClient webClient, long timeout) {
        webClient.getJavaScriptEngine().processPostponedActions();
        webClient.waitForBackgroundJavaScript(timeout);
    }

    /**
     * Create and add an {@link ExceptionListener} to the {@link WebClient} instance.
     * @param webClient The {@link WebClient} instance.
     * @return The {@link ExceptionListener}.
     */
    public static ExceptionListener addExceptionListener(WebClient webClient) {
        ExceptionListener exceptionListener = new ExceptionListener(webClient);
        webClient.setJavaScriptErrorListener(exceptionListener);
        return exceptionListener;
    }

    /**
     * JavaScript Exception listener.
     * @see #addExceptionListener(WebClient)
     */
    public static class ExceptionListener implements JavaScriptErrorListener {

        private final WebClient webClient;
        private ScriptException scriptException;

        private ExceptionListener(WebClient webClient) {
            this.webClient = webClient;
        }

        /**
         * Get the last {@link ScriptException}.
         * @return The last {@link ScriptException}, or {@code null} if none happened.
         */
        public ScriptException getScriptException() {
            return scriptException;
        }

        /**
         * Get the last {@link ScriptException}.
         * <p>
         * Performs a call to {@link #assertHasException()}.
         * @return The last {@link ScriptException}.
         */
        public ScriptException getExpectedScriptException() {
            assertHasException();
            return scriptException;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void scriptException(InteractivePage htmlPage, ScriptException scriptException) {
            this.scriptException = scriptException;
        }

        /**
         * Assert that a {@link ScriptException} occurred within the JavaScript executing
         * on the associated {@link WebClient}.
         */
        public void assertHasException() {
            WebClientUtil.waitForJSExec(webClient);
            Assert.assertNotNull("A JavaScript Exception was expected.", scriptException);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void timeoutError(InteractivePage htmlPage, long allowedTime, long executionTime) {
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public void malformedScriptURL(InteractivePage htmlPage, String url, MalformedURLException malformedURLException) {
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public void loadScriptError(InteractivePage htmlPage, URL scriptUrl, Exception exception) {
        }
    }
}
