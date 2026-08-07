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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Support for serving WebSocket responses.
 * @since 2.216
 */
@Restricted(Beta.class)
public class WebSockets {

    private static final Logger LOGGER = Logger.getLogger(WebSockets.class.getName());

    private static final Provider provider = findProvider();

    private static Provider findProvider() {
        Iterator<Provider> it = ServiceLoader.load(Provider.class).iterator();
        while (it.hasNext()) {
            try {
                return it.next();
            } catch (ServiceConfigurationError x) {
                LOGGER.log(Level.FINE, null, x);
            }
        }
        return null;
    }

    // TODO ability to handle subprotocols?

    public static HttpResponse upgrade(WebSocketSession session) {
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                upgradeResponse(session, req, rsp);
            }
        };
    }

    /**
     * Variant of {@link #upgrade} that does not presume a {@link StaplerRequest2}.
     * @since 2.446
     */
    public static void upgradeResponse(WebSocketSession session, HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
        if (provider == null) {
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            session.handler = provider.handle(req, rsp, new Provider.Listener() {
                private Object providerSession;

                @Override
                public void onWebSocketConnect(Object providerSession) {
                    this.providerSession = providerSession;
                    session.startPings();
                    session.opened();
                }

                @Override
                public Object getProviderSession() {
                    return providerSession;
                }

                @Override
                public void onWebSocketClose(int statusCode, String reason) {
                    session.stopPings();
                    session.closed(statusCode, reason);
                }

                @Override
                public void onWebSocketError(Throwable cause) {
                    if (cause instanceof ClosedChannelException) {
                        onWebSocketClose(0, cause.toString());
                    } else {
                        session.error(cause);
                    }
                }

                @Override
                public void onWebSocketBinary(byte[] payload, int offset, int length) {
                    try {
                        session.binary(payload, offset, length);
                    } catch (IOException x) {
                        session.error(x);
                    }
                }

                @Override
                public void onWebSocketText(String message) {
                    try {
                        session.text(message);
                    } catch (IOException x) {
                        session.error(x);
                    }
                }
            });
            // OK, unless handler is null in which case we expect an error was already sent.
        } catch (IOException | ServletException x) {
            throw x;
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
            rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public static boolean isSupported() {
        return provider != null;
    }

    private WebSockets() {}

}
