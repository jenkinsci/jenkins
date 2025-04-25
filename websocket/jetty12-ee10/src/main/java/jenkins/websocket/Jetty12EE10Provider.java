/*
 * The MIT License
 *
 * Copyright 2022, 2024 CloudBees, Inc.
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
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Future;
import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@MetaInfServices(Provider.class)
public class Jetty12EE10Provider implements Provider {

    /**
     * Number of seconds a WebsocketConnection may stay idle until it expires. Zero to disable. This
     * value must be higher than the <code>jenkins.websocket.pingInterval</code>. Per <a
     * href=https://eclipse.dev/jetty/documentation/jetty-12/programming-guide/index.html#pg-websocket-session-ping>Jetty
     * 12 documentation</a> a ping mechanism should keep the websocket active. Therefore, the idle
     * timeout must be higher than the ping interval to avoid timeout issues.
     */
    private static long IDLE_TIMEOUT_SECONDS = Long.getLong("jenkins.websocket.idleTimeout", 60L);

    private static final String ATTR_LISTENER = Jetty12EE10Provider.class.getName() + ".listener";

    private boolean initialized = false;

    public Jetty12EE10Provider() {
        JettyWebSocketServerContainer.class.hashCode();
    }

    private void init(HttpServletRequest req) {
        if (!initialized) {
            JettyWebSocketServerContainer.getContainer(req.getServletContext()).setIdleTimeout(Duration.ofSeconds(IDLE_TIMEOUT_SECONDS));
            initialized = true;
        }
    }

    @Override
    public Handler handle(HttpServletRequest req, HttpServletResponse rsp, Listener listener) throws Exception {
        init(req);
        req.setAttribute(ATTR_LISTENER, listener);
        // TODO Jetty 10+ has no obvious equivalent to WebSocketServerFactory.isUpgradeRequest; RFC6455Negotiation?
        if (!"websocket".equalsIgnoreCase(req.getHeader("Upgrade"))) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "only WS connections accepted here");
            return null;
        }
        if (!JettyWebSocketServerContainer.getContainer(req.getServletContext()).upgrade(Jetty12EE10Provider::createWebSocket, req, rsp)) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "did not manage to upgrade");
            return null;
        }
        return new Handler() {
            @Override
            public Future<Void> sendBinary(ByteBuffer data) {
                Callback.Completable f = new Callback.Completable();
                session().sendBinary(data, f);
                return f;
            }

            @Override
            public Future<Void> sendBinary(ByteBuffer partialByte, boolean isLast) {
                Callback.Completable f = new Callback.Completable();
                session().sendPartialBinary(partialByte, isLast, f);
                return f;
            }

            @Override
            public Future<Void> sendText(String text) {
                Callback.Completable f = new Callback.Completable();
                session().sendText(text, f);
                return f;
            }

            @Override
            public Future<Void> sendPing(ByteBuffer applicationData) {
                Callback.Completable f = new Callback.Completable();
                session().sendPing(applicationData, f);
                return f;
            }

            @Override
            public void close() {
                session().close();
            }

            private Session session() {
                Session session = (Session) listener.getProviderSession();
                if (session == null) {
                    throw new IllegalStateException("missing session");
                }
                return session;
            }
        };
    }

    private static Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp) {
        Listener listener = (Listener) req.getHttpServletRequest().getAttribute(ATTR_LISTENER);
        if (listener == null) {
            throw new IllegalStateException("missing listener attribute");
        }
        return new SessionListener(listener);
    }

    public static class SessionListener implements Session.Listener.AutoDemanding {
        private final Listener listener;

        public SessionListener(Listener listener) {
            this.listener = Objects.requireNonNull(listener);
        }

        @Override
        public void onWebSocketOpen(Session session) {
            listener.onWebSocketConnect(session);
        }

        @Override
        public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
            try {
                int len = payload.remaining();
                if (payload.hasArray()) {
                    listener.onWebSocketBinary(payload.array(), payload.arrayOffset() + payload.position(), len);
                    payload.position(payload.position() + len);
                } else {
                    byte[] bytes = new byte[len];
                    payload.get(bytes);
                    listener.onWebSocketBinary(bytes, 0, len);
                }
                callback.succeed();
            } catch (Throwable t) {
                callback.fail(t);
            }
        }

        @Override
        public void onWebSocketText(String message) {
            listener.onWebSocketText(message);
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            listener.onWebSocketError(cause);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            listener.onWebSocketClose(statusCode, reason);
        }
    }
}
