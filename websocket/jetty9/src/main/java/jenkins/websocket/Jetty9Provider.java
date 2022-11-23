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
import java.util.concurrent.Future;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@MetaInfServices(Provider.class)
public class Jetty9Provider implements Provider {

    private static final String ATTR_LISTENER = Jetty9Provider.class.getName() + ".listener";

    private WebSocketServletFactory factory;

    public Jetty9Provider() {
        WebSocketServletFactory.class.hashCode();
    }

    private synchronized void init(HttpServletRequest req) throws Exception {
        if (factory == null) {
            factory = WebSocketServletFactory.Loader.load(req.getServletContext(), WebSocketPolicy.newServerPolicy());
            factory.start();
            factory.setCreator(Jetty9Provider::createWebSocket);
        }
    }

    @Override
    public Handler handle(HttpServletRequest req, HttpServletResponse rsp, Listener listener) throws Exception {
        init(req);
        req.setAttribute(ATTR_LISTENER, listener);
        if (!factory.isUpgradeRequest(req, rsp)) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "only WS connections accepted here");
            return null;
        }
        if (!factory.acceptWebSocket(req, rsp)) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "did not manage to upgrade");
            return null;
        }
        return new Handler() {
            @Override
            public Future<Void> sendBinary(ByteBuffer data) throws IOException {
                return session().getRemote().sendBytesByFuture(data);
            }

            @Override
            public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
                session().getRemote().sendPartialBytes(partialByte, isLast);
            }

            @Override
            public Future<Void> sendText(String text) throws IOException {
                return session().getRemote().sendStringByFuture(text);
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
                Session session = (Session) listener.getProviderSession();
                if (session == null) {
                    throw new IllegalStateException("missing session");
                }
                return session;
            }
        };
    }

    private static Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
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
                listener.onWebSocketConnect(session);
            }

            @Override
            public void onWebSocketError(Throwable cause) {
                listener.onWebSocketError(cause);
            }
        };
    }

}
