package com.gargoylesoftware.htmlunit;

import org.junit.Assert;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public interface WebResponseListener {

    void onLoadWebResponse(WebRequest webRequest, WebResponse webResponse) throws IOException;

    public final class StatusListener implements WebResponseListener {

        private final int statusCode;
        private final List<WebResponse> responses = new CopyOnWriteArrayList<>();

        public StatusListener(final int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public void onLoadWebResponse(WebRequest webRequest, WebResponse webResponse) throws IOException {
            if (webResponse.getStatusCode() == statusCode) {
                responses.add(webResponse);
            }
        }

        public void assertHasResponses() {
            Assert.assertTrue(!responses.isEmpty());
        }

        public List<WebResponse> getResponses() {
            return responses;
        }
    }
}
