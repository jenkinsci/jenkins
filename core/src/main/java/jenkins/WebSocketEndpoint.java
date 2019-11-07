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
import hudson.ExtensionPoint;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jenkins.util.Timer;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
public final class WebSocketEndpoint extends InvisibleAction implements UnprotectedRootAction {

    private Class<?> webSocketServletFactoryClass;
    private /*WebSocketServletFactory*/Object factory;

    @Override
    public String getUrlName() {
        return "ws";
    }

    private synchronized void init() throws Exception {
        if (factory == null) {
            ClassLoader cl = ServletContext.class.getClassLoader();
            webSocketServletFactoryClass = cl.loadClass("org.eclipse.jetty.websocket.servlet.WebSocketServletFactory");
            Class<?> webSocketPolicyClass = cl.loadClass("org.eclipse.jetty.websocket.api.WebSocketPolicy");
            factory = cl.loadClass("org.eclipse.jetty.websocket.servlet.WebSocketServletFactory$Loader").getMethod("load", ServletContext.class, webSocketPolicyClass).invoke(null, Stapler.getCurrent().getServletContext(), webSocketPolicyClass.getMethod("newServerPolicy").invoke(null));
            webSocketServletFactoryClass.getMethod("start").invoke(factory);
            Class<?> webSocketCreatorClass = cl.loadClass("org.eclipse.jetty.websocket.servlet.WebSocketCreator");
            webSocketServletFactoryClass.getMethod("setCreator", webSocketCreatorClass).invoke(factory, Proxy.newProxyInstance(cl, new Class<?>[] {webSocketCreatorClass}, (proxy1, method1, args1) -> {
                Object servletUpgradeRequest = args1[0];
                String requestPath = (String) servletUpgradeRequest.getClass().getMethod("getRequestPath").invoke(servletUpgradeRequest);
                assert requestPath.startsWith("/ws/");
                Service service = ExtensionList.lookup(Service.class).stream().filter(s -> requestPath.substring(4).equals(s.name())).findFirst().get();
                Session session = service.start();
                return Proxy.newProxyInstance(cl, new Class<?>[] {cl.loadClass("org.eclipse.jetty.websocket.api.WebSocketListener")}, (proxy2, method2, args2) -> {
                    switch (method2.getName()) {
                    case "onWebSocketConnect":
                        session.started(args2[0].getClass().getMethod("getRemote").invoke(args2[0]), service.serverKeepAlive());
                        return null;
                    case "onWebSocketClose":
                        session._closed((Integer) args2[0], (String) args2[1]);
                        return null;
                    case "onWebSocketError":
                        session.error((Throwable) args2[0]);
                        return null;
                    case "onWebSocketBinary":
                        session.binary((byte[]) args2[0], (Integer) args2[1], (Integer) args2[2]);
                        return null;
                    case "onWebSocketText":
                        session.text((String) args2[0]);
                        return null;
                    default:
                        throw new AssertionError();
                    }
                });
            }));
        }
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws Exception {
        // TODO check rest of path against registered Service.name
        init();
        if (!((Boolean) webSocketServletFactoryClass.getMethod("isUpgradeRequest", HttpServletRequest.class, HttpServletResponse.class).invoke(factory, req, rsp))) {
            throw HttpResponses.errorWithoutStack(HttpServletResponse.SC_BAD_REQUEST, "only WS connections accepted here");
        }
        if (!((Boolean) webSocketServletFactoryClass.getMethod("acceptWebSocket", HttpServletRequest.class, HttpServletResponse.class).invoke(factory, req, rsp))) {
            throw HttpResponses.errorWithoutStack(HttpServletResponse.SC_BAD_REQUEST, "did not manage to upgrade");
        }
        // OK!
    }

    public interface Service extends ExtensionPoint {
        String name();
        Session start();
        default boolean serverKeepAlive() {
            return false;
        }
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
        void started(Object remoteEndpoint, boolean keepAlive) {
            this.remoteEndpoint = remoteEndpoint;
            if (keepAlive) {
                pings = Timer.get().scheduleAtFixedRate(() -> {
                    try {
                        remoteEndpoint.getClass().getMethod("sendPing", ByteBuffer.class).invoke(remoteEndpoint, ByteBuffer.wrap(new byte[0]));
                        System.err.println("TODO sent ping");
                    } catch (Exception x) {
                        error(x);
                        pings.cancel(true);
                    }
                }, PING_INTERVAL_SECONDS / 2, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
            }
        }
        void _closed(int statusCode, String reason) {
            if (pings != null) {
                pings.cancel(true);
                // alternately, check Session.isOpen each time
            }
            closed(statusCode, reason);
        }
        protected void closed(int statusCode, String reason) {}
        protected void error(Throwable cause) {}
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
    public static final class Example implements Service {
        @Override
        public String name() {
            return "example";
        }
        @Override
        public boolean serverKeepAlive() {
            return true;
        }
        @Override
        public Session start() {
            return new Session() {
                @Override
                protected void text(String message) {
                    sendText("hello " + message);
                }
            };
        }
    }

}
