/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * One WebSocket connection.
 * @see WebSockets
 * @since 2.216
 */
@Restricted(Beta.class)
public abstract class WebSocketSession {

    /**
     * Number of seconds between server-sent pings.
     * Zero to disable.
     * <p><a href="http://nginx.org/en/docs/http/websocket.html">nginx docs</a> claim 60s timeout and this seems to match experiments.
     * <a href="https://cloud.google.com/kubernetes-engine/docs/concepts/ingress#support_for_websocket">GKE docs</a> says 30s
     * but this is a total timeout, not inactivity, so you need to set {@code BackendConfigSpec.timeoutSec} anyway.
     * <p>This is set for the whole Jenkins session rather than a particular service,
     * since it has more to do with the environment than anything else.
     * Certain services may have their own “keep alive” semantics,
     * but for example {@link hudson.remoting.PingThread} may be too infrequent.
     */
    private static long PING_INTERVAL_SECONDS = SystemProperties.getLong("jenkins.websocket.pingInterval", 30L);

    private static final Logger LOGGER = Logger.getLogger(WebSocketSession.class.getName());

    private Object session;
    // https://www.eclipse.org/jetty/javadoc/9.4.24.v20191120/org/eclipse/jetty/websocket/common/WebSocketRemoteEndpoint.html
    private Object remoteEndpoint;
    private ScheduledFuture<?> pings;

    protected WebSocketSession() {}

    Object onWebSocketSomething(Object proxy, Method method, Object[] args) throws Exception {
        switch (method.getName()) {
        case "onWebSocketConnect":
            this.session = args[0];
            this.remoteEndpoint = session.getClass().getMethod("getRemote").invoke(args[0]);
            if (PING_INTERVAL_SECONDS != 0) {
                pings = Timer.get().scheduleAtFixedRate(() -> {
                    try {
                        remoteEndpoint.getClass().getMethod("sendPing", ByteBuffer.class).invoke(remoteEndpoint, ByteBuffer.wrap(new byte[0]));
                    } catch (Exception x) {
                        error(x);
                        pings.cancel(true);
                    }
                }, PING_INTERVAL_SECONDS / 2, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
            }
            opened();
            return null;
        case "onWebSocketClose":
            if (pings != null) {
                pings.cancel(true);
                // alternately, check Session.isOpen each time
            }
            closed((Integer) args[0], (String) args[1]);
            return null;
        case "onWebSocketError":
            error((Throwable) args[0]);
            return null;
        case "onWebSocketBinary":
            binary((byte[]) args[0], (Integer) args[1], (Integer) args[2]);
            return null;
        case "onWebSocketText":
            text((String) args[0]);
            return null;
        default:
            throw new AssertionError();
        }
    }

    protected void opened() {
    }

    protected void closed(int statusCode, String reason) {
    }

    protected void error(Throwable cause) {
        LOGGER.log(Level.WARNING, "unhandled WebSocket service error", cause);
    }

    protected void binary(byte[] payload, int offset, int len) {
        LOGGER.warning("unexpected binary frame");
    }

    protected void text(String message) {
        LOGGER.warning("unexpected text frame");
    }

    @SuppressWarnings("unchecked")
    protected final Future<Void> sendBinary(ByteBuffer data) {
        try {
            return (Future<Void>) remoteEndpoint.getClass().getMethod("sendBytesByFuture", ByteBuffer.class).invoke(remoteEndpoint, data);
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    protected final void sendBinary(ByteBuffer partialByte, boolean isLast) {
        try {
            remoteEndpoint.getClass().getMethod("sendPartialBytes", ByteBuffer.class, boolean.class).invoke(remoteEndpoint, partialByte, isLast);
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    @SuppressWarnings("unchecked")
    protected final Future<Void> sendText(String text) {
        try {
            return (Future<Void>) remoteEndpoint.getClass().getMethod("sendStringByFuture", String.class).invoke(remoteEndpoint, text);
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    protected final void close() {
        try {
            session.getClass().getMethod("close").invoke(session);
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

}
