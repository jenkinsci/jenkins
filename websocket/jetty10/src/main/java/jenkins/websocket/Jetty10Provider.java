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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketServerContainer;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@MetaInfServices(Provider.class)
public class Jetty10Provider implements Provider {

    private static final String ATTR_LISTENER = Jetty10Provider.class.getName() + ".listener";

    // TODO does not seem possible to use HttpServletRequest.get/setAttribute for this
    private static final Map<Listener, Session> sessions = Collections.synchronizedMap(new WeakHashMap<>());

    public Jetty10Provider() {
        JettyWebSocketServerContainer.class.hashCode();
    }

    @Override
    public Handler handle(HttpServletRequest req, HttpServletResponse rsp, Listener listener) throws Exception {
        req.setAttribute(ATTR_LISTENER, listener);
        // TODO Jetty 10 has no obvious equivalent to WebSocketServerFactory.isUpgradeRequest; RFC6455Negotiation?
        if (!"websocket".equalsIgnoreCase(req.getHeader("Upgrade"))) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "only WS connections accepted here");
            return null;
        }
        if (!JettyWebSocketServerContainer.getContainer(req.getServletContext()).upgrade(Jetty10Provider::createWebSocket, req, rsp)) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "did not manage to upgrade");
            return null;
        }
        return new Handler() {
            @Override
            public Future<Void> sendBinary(ByteBuffer data) throws IOException {
                CompletableFuture<Void> f = new CompletableFuture<>();
                session().getRemote().sendBytes(data, new WriteCallbackImpl(f));
                return f;
            }

            @Override
            public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
                session().getRemote().sendPartialBytes(partialByte, isLast);
            }

            @Override
            public Future<Void> sendText(String text) throws IOException {
                CompletableFuture<Void> f = new CompletableFuture<>();
                session().getRemote().sendString(text, new WriteCallbackImpl(f));
                return f;
            }

            @Override
            public void sendPing(ByteBuffer applicationData) throws IOException {
                session().getRemote().sendPing(applicationData);
            }

            @Override
            public void close() throws IOException {
                session().close();
            }

            private Session session() {
                Session session = sessions.get(listener);
                if (session == null) {
                    throw new IllegalStateException("missing session");
                }
                return session;
            }
        };
    }

    private static final class WriteCallbackImpl implements WriteCallback {
        private final CompletableFuture<Void> f;

        WriteCallbackImpl(CompletableFuture<Void> f) {
            this.f = f;
        }

        @Override
        public void writeSuccess() {
            f.complete(null);
        }

        @Override
        public void writeFailed(Throwable x) {
            f.completeExceptionally(x);
        }
    }

    private static Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp) {
        Listener listener = (Listener) req.getHttpServletRequest().getAttribute(ATTR_LISTENER);
        if (listener == null) {
            throw new IllegalStateException("missing listener attribute");
        }
        return new WebSocketListener() {
            @Override
            public void onWebSocketBinary(byte[] payload, int offset, int length) {
                listener.onWebSocketBinary(payload, offset, length);
            }

            @Override
            public void onWebSocketText(String message) {
                listener.onWebSocketText(message);
            }

            @Override
            public void onWebSocketClose(int statusCode, String reason) {
                listener.onWebSocketClose(statusCode, reason);
            }

            @Override
            public void onWebSocketConnect(Session session) {
                sessions.put(listener, session);
                listener.onWebSocketConnect();
            }

            @Override
            public void onWebSocketError(Throwable cause) {
                listener.onWebSocketError(cause);
            }
        };
    }

}
