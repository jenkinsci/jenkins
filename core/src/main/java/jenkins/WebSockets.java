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

package jenkins;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jenkins.util.Timer;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;

@Extension
public final class WebSockets {

    private static final Logger LOGGER = Logger.getLogger(WebSockets.class.getName());

    private static final String ATTR_SESSION = WebSockets.class.getName() + ".session";

    // TODO method to see if WS are supported in this container

    public static HttpResponse upgrade(Session session) {
        return (req, rsp, node) -> {
            try {
                Object factory = ExtensionList.lookupSingleton(WebSockets.class).init();
                if (!((Boolean) webSocketServletFactoryClass.getMethod("isUpgradeRequest", HttpServletRequest.class, HttpServletResponse.class).invoke(factory, req, rsp))) {
                    throw HttpResponses.errorWithoutStack(HttpServletResponse.SC_BAD_REQUEST, "only WS connections accepted here");
                }
                req.setAttribute(ATTR_SESSION, session);
                if (!((Boolean) webSocketServletFactoryClass.getMethod("acceptWebSocket", HttpServletRequest.class, HttpServletResponse.class).invoke(factory, req, rsp))) {
                    throw HttpResponses.errorWithoutStack(HttpServletResponse.SC_BAD_REQUEST, "did not manage to upgrade");
                }
            } catch (HttpResponses.HttpResponseException x) {
                throw x;
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, null, x);
                throw HttpResponses.error(x);
            }
            // OK!
        };
    }

    private static ClassLoader cl;
    private static Class<?> webSocketServletFactoryClass;

    private static synchronized void staticInit() throws Exception {
        if (webSocketServletFactoryClass == null) {
            cl = ServletContext.class.getClassLoader();
            webSocketServletFactoryClass = cl.loadClass("org.eclipse.jetty.websocket.servlet.WebSocketServletFactory");
        }
    }

    private /*WebSocketServletFactory*/Object factory;

    private synchronized Object init() throws Exception {
        if (factory == null) {
            staticInit();
            Class<?> webSocketPolicyClass = cl.loadClass("org.eclipse.jetty.websocket.api.WebSocketPolicy");
            factory = cl.loadClass("org.eclipse.jetty.websocket.servlet.WebSocketServletFactory$Loader").getMethod("load", ServletContext.class, webSocketPolicyClass).invoke(null, Stapler.getCurrent().getServletContext(), webSocketPolicyClass.getMethod("newServerPolicy").invoke(null));
            webSocketServletFactoryClass.getMethod("start").invoke(factory);
            Class<?> webSocketCreatorClass = cl.loadClass("org.eclipse.jetty.websocket.servlet.WebSocketCreator");
            webSocketServletFactoryClass.getMethod("setCreator", webSocketCreatorClass).invoke(factory, Proxy.newProxyInstance(cl, new Class<?>[] {webSocketCreatorClass}, this::createWebSocket));
        }
        return factory;
    }

    private Object createWebSocket(Object proxy, Method method, Object[] args) throws Exception {
        Object servletUpgradeRequest = args[0];
        Session session = (Session) servletUpgradeRequest.getClass().getMethod("getServletAttribute", String.class).invoke(servletUpgradeRequest, ATTR_SESSION);
        return Proxy.newProxyInstance(cl, new Class<?>[] {cl.loadClass("org.eclipse.jetty.websocket.api.WebSocketListener")}, session::onWebSocketSomething);
    }

    /**
     * Number of seconds between server-sent pings, if enabled.
     * <a href="http://nginx.org/en/docs/http/websocket.html">nginx docs</a> claim 60s timeout and this seems to match experiments.
     * <a href="https://cloud.google.com/kubernetes-engine/docs/concepts/ingress#support_for_websocket">GKE docs</a> says 30s
     * but this is a total timeout, not inactivity, so you need to set {@code BackendConfigSpec.timeoutSec} anyway.
     */
    private static final long PING_INTERVAL_SECONDS = 30;
    public static abstract class Session {
        private Object remoteEndpoint;
        private ScheduledFuture<?> pings;
        Object onWebSocketSomething(Object proxy, Method method, Object[] args) throws Exception {
            switch (method.getName()) {
            case "onWebSocketConnect":
                this.remoteEndpoint = args[0].getClass().getMethod("getRemote").invoke(args[0]);
                if (keepAlive()) {
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
        protected boolean keepAlive() {
            return false;
        }
        protected void opened() {}
        protected void closed(int statusCode, String reason) {}
        protected void error(Throwable cause) {
            LOGGER.log(Level.WARNING, "unhandled WebSocket service error", cause);
        }
        protected void binary(byte[] payload, int offset, int len) {}
        protected void text(String message) {}
        @SuppressWarnings("unchecked")
        protected final Future<Void> sendBinary(ByteBuffer data) {
            try {
                return (Future<Void>) remoteEndpoint.getClass().getMethod("sendBytesByFuture", ByteBuffer.class).invoke(remoteEndpoint, data);
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
    }

    @Extension
    public static final class EchoExample extends InvisibleAction implements UnprotectedRootAction {

        @Override
        public String getUrlName() {
            return "wsecho";
        }

        public HttpResponse doIndex() {
            return upgrade(new Session() {
                @Override
                protected boolean keepAlive() {
                    return true;
                }
                @Override
                protected void text(String message) {
                    sendText("hello " + message);
                }
                @Override
                protected void binary(byte[] payload, int offset, int len) {
                    ByteBuffer data = ByteBuffer.allocate(len);
                    for (int i = 0; i < len; i++) {
                        byte b = payload[offset + i];
                        if (b >= 'a' && b <= 'z') {
                            b += 'A' - 'a';
                        }
                        data.put(i, b);
                    }
                    sendBinary(data);
                }
            });
        }

    }

}
