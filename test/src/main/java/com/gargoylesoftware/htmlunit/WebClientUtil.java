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
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class WebClientUtil {

    public static void waitForJSExec(WebClient webClient) {
        waitForJSExec(webClient, 10000);
    }

    public static void waitForJSExec(WebClient webClient, long timeout) {
        webClient.getJavaScriptEngine().processPostponedActions();
        webClient.waitForBackgroundJavaScript(timeout);
    }

    public static ExceptionListener addExceptionListener(WebClient webClient) {
        ExceptionListener exceptionListener = new ExceptionListener(webClient);
        webClient.setJavaScriptErrorListener(exceptionListener);
        return exceptionListener;
    }

    public static class ExceptionListener implements JavaScriptErrorListener {

        private final WebClient webClient;
        private ScriptException scriptException;

        private ExceptionListener(WebClient webClient) {
            this.webClient = webClient;
        }

        public ScriptException getScriptException() {
            return scriptException;
        }

        public ScriptException getExpectedScriptException() {
            assertHasException();
            return scriptException;
        }

        @Override
        public void scriptException(InteractivePage htmlPage, ScriptException scriptException) {
            this.scriptException = scriptException;
        }

        public void assertHasException() {
            WebClientUtil.waitForJSExec(webClient);
            Assert.assertNotNull("A JavaScript Exception was expected.", scriptException);
        }

        @Override
        public void timeoutError(InteractivePage htmlPage, long allowedTime, long executionTime) {
        }
        @Override
        public void malformedScriptURL(InteractivePage htmlPage, String url, MalformedURLException malformedURLException) {
        }
        @Override
        public void loadScriptError(InteractivePage htmlPage, URL scriptUrl, Exception exception) {
        }
    }
}
