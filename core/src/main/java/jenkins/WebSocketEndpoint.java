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
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.lang.reflect.Proxy;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
                return Proxy.newProxyInstance(cl, new Class<?>[] {cl.loadClass("org.eclipse.jetty.websocket.api.WebSocketListener")}, (proxy2, method2, args2) -> {
                    System.err.println("TODO running " + method2.getName());
                    return null;
                });
            }));
        }
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws Exception {
        init();
        if (!((Boolean) webSocketServletFactoryClass.getMethod("isUpgradeRequest", HttpServletRequest.class, HttpServletResponse.class).invoke(factory, req, rsp))) {
            throw HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "only WS connections accepted here");
        }
        if (!((Boolean) webSocketServletFactoryClass.getMethod("acceptWebSocket", HttpServletRequest.class, HttpServletResponse.class).invoke(factory, req, rsp))) {
            throw HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "did not manage to upgrade");
        }
        // OK!
    }

}
