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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
     * This value must be lower than the <code>jenkins.websocket.idleTimeout</code>.
     * <p><a href="https://nginx.org/en/docs/http/websocket.html">nginx docs</a> claim 60s timeout and this seems to match experiments.
     * <a href="https://cloud.google.com/kubernetes-engine/docs/concepts/ingress#support_for_websocket">GKE docs</a> says 30s
     * but this is a total timeout, not inactivity, so you need to set {@code BackendConfigSpec.timeoutSec} anyway.
     * <p>This is set for the whole Jenkins session rather than a particular service,
     * since it has more to do with the environment than anything else.
     * Certain services may have their own “keep alive” semantics,
     * but for example {@link hudson.remoting.PingThread} may be too infrequent.
     */
    private static Duration PING_INTERVAL = SystemProperties.getDuration("jenkins.websocket.pingInterval", ChronoUnit.SECONDS, Duration.ofSeconds(30));

    private static final Logger LOGGER = Logger.getLogger(WebSocketSession.class.getName());

    Provider.Handler handler;
    private ScheduledFuture<?> pings;

    protected WebSocketSession() {}

    void startPings() {
        if (PING_INTERVAL.compareTo(Duration.ZERO) > 0) {
            pings = Timer.get().scheduleAtFixedRate(() -> {
                try {
                    Future<Void> f = handler.sendPing(ByteBuffer.wrap(new byte[0]));
                    // TODO do something interesting with the future
                } catch (Exception x) {
                    error(x);
                    pings.cancel(true);
                }
            }, PING_INTERVAL.dividedBy(2).toSeconds(), PING_INTERVAL.toSeconds(), TimeUnit.SECONDS);
        }
    }

    void stopPings() {
        if (pings != null) {
            pings.cancel(true);
            // alternately, check Session.isOpen each time
        }
    }

    protected void opened() {
    }

    protected void closed(int statusCode, String reason) {
    }

    protected void error(Throwable cause) {
        LOGGER.log(Level.WARNING, "unhandled WebSocket service error", cause);
    }

    protected void binary(byte[] payload, int offset, int len) throws IOException {
        LOGGER.warning("unexpected binary frame");
    }

    protected void text(String message) throws IOException {
        LOGGER.warning("unexpected text frame");
    }

    protected final Future<Void> sendBinary(ByteBuffer data) throws IOException {
        return handler.sendBinary(data);
    }

    protected final Future<Void> sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
        return handler.sendBinary(partialByte, isLast);
    }

    protected final Future<Void> sendText(String text) throws IOException {
        return handler.sendText(text);
    }

    protected final void close() throws IOException {
        handler.close();
    }

}
