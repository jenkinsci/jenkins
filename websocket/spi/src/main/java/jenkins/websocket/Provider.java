/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

package jenkins.websocket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * Defines a way for Jenkins core to serve WebSocket connections.
 * This permits core to use one or more implementations supplied by the servlet container,
 * based on static compilation while not having a hard dependency on any one.
 * ({@code javax.websocket.*} APIs are not suited to usage from Stapler.)
 * The constructor should try to link against everything necessary so any errors are thrown up front.
 */
interface Provider {

    /**
     * Handle a WebSocket server request.
     * @return a handler, unless an error code was already set
     */
    Handler handle(HttpServletRequest req, HttpServletResponse rsp, Listener listener) throws Exception;

    // interface listener
    interface Listener {

        void onWebSocketConnect(Object providerSession);

        Object getProviderSession();

        void onWebSocketClose(int statusCode, String reason);

        void onWebSocketError(Throwable cause);

        void onWebSocketBinary(byte[] payload, int offset, int length);

        void onWebSocketText(String message);

    }

    // interface handler
    interface Handler {

        Future<Void> sendBinary(ByteBuffer data) throws IOException;

        void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException;

        Future<Void> sendText(String text) throws IOException;

        Future<Void> sendPing(ByteBuffer applicationData) throws IOException;

        void close() throws IOException;

    }

}
