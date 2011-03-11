/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package hudson.widgets;

import hudson.Util;
import hudson.util.IOException2;
import hudson.util.PackedMap;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.jelly.DefaultScriptInvoker;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Captured Jelly {@link Script} that can be rendered later on demand from JavaScript.
 *
 * @author Kohsuke Kawaguchi
 */
public class RenderOnDemandClosure {
    private final Script body;
    private final Map<String,Object> variables;

    public RenderOnDemandClosure(JellyContext context, String attributesToCapture) {
        body = (Script) context.getVariable("org.apache.commons.jelly.body");

        Map<String,Object> variables = new HashMap<String, Object>();
        for (String v : Util.fixNull(attributesToCapture).split(","))
            variables.put(v,context.getVariable(v));
        this.variables = PackedMap.of(variables);
    }

    /**
     * Renders the captured fragment.
     */
    @JavaScriptMethod
    public HttpResponse render() {
        return new HttpResponse() {
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                try {
                    new DefaultScriptInvoker() {
                        @Override
                        protected void exportVariables(StaplerRequest req, StaplerResponse rsp, Script script, Object it, JellyContext context) {
                            super.exportVariables(req, rsp, script, it, context);
                            context.setVariables(variables);
                        }
                    }.invokeScript(req,rsp,body,null);
                } catch (JellyTagException e) {
                    throw new IOException2("Failed to evaluate the template closure",e);
                }
            }
        };
    }
}
