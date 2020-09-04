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

import hudson.Extension;
import hudson.ExtensionList;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;

/**
 * Support for serving WebSocket responses.
 * @since 2.216
 */
@Restricted(Beta.class)
@Extension
public class WebSockets {

    private static final Logger LOGGER = Logger.getLogger(WebSockets.class.getName());

    private static final String ATTR_SESSION = WebSockets.class.getName() + ".session";

    // TODO ability to handle subprotocols?

    public static HttpResponse upgrade(WebSocketSession session) {
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

    public static boolean isSupported() {
        try {
            staticInit();
            return true;
        } catch (Exception x) {
            LOGGER.log(Level.FINE, null, x);
            return false;
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
        WebSocketSession session = (WebSocketSession) servletUpgradeRequest.getClass().getMethod("getServletAttribute", String.class).invoke(servletUpgradeRequest, ATTR_SESSION);
        return Proxy.newProxyInstance(cl, new Class<?>[] {cl.loadClass("org.eclipse.jetty.websocket.api.WebSocketListener")}, session::onWebSocketSomething);
    }

}
