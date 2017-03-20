/*
 * The MIT License
 *
 * Copyright (c) 2017 Oleg Nenashev.
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
package org.kohsuke.stapler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Mocked version of {@link StaplerRequest}.
 * @author Oleg Nenashev
 */
public class MockStaplerRequestBuilder{
    
    private final JenkinsRule r;
    
    public MockStaplerRequestBuilder(@Nonnull JenkinsRule r) {
        this.r = r;
    }
    
    public StaplerRequest build() throws AssertionError {
        RequestImpl currentRequest = (RequestImpl) Stapler.getCurrentRequest();
        HttpServletRequest original = (HttpServletRequest) currentRequest.getRequest();
        final Map<String,Object> getters = new HashMap<>();
        for (Method method : HttpServletRequest.class.getMethods()) {
            String m = method.getName();
            if ((m.startsWith("get") || m.startsWith("is")) && method.getParameterTypes().length == 0) {
                Class<?> type = method.getReturnType();
                // TODO could add other types which are known to be safe to copy: Cookie[], Principal, HttpSession, etc.
                if (type.isPrimitive() || type == String.class || type == Locale.class) {
                    try {
                        getters.put(m, method.invoke(original));
                    } catch (Exception ex) {
                        throw new AssertionError("Cannot mock the StaplerRequest", ex);
                    }
                }
            }
        }
        List<AncestorImpl> ancestors = currentRequest.ancestors;
        TokenList tokens = currentRequest.tokens;
        return new RequestImpl(Stapler.getCurrent(), 
                (HttpServletRequest) Proxy.newProxyInstance(
                        Jenkins.class.getClassLoader(), 
                        new Class<?>[] {HttpServletRequest.class}, 
                        new InvocationHandler() {
                            @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                String m = method.getName();
                                if (getters.containsKey(m)) {
                                    return getters.get(m);
                                } else {
                                    throw new UnsupportedOperationException(m);
                                }
                            }
        }), ancestors, tokens);
    }
    
}
