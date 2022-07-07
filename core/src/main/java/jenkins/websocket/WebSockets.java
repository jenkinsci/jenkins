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
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

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
        if (provider == null) {
            throw HttpResponses.notFound();
        }
        return (req, rsp, node) -> {
            try {
                session.handler = provider.handle(req, rsp, new Provider.Listener() {
                    @Override
                    public void onWebSocketConnect() {
                        session.startPings();
                        session.opened();
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
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, null, x);
                throw HttpResponses.error(x);
            }
            // OK, unless handler is null in which case we expect an error was already sent.
        };
    }

    public static boolean isSupported() {
        return provider != null;
    }

    private WebSockets() {}

}
