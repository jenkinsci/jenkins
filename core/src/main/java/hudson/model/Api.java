/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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
package hudson.model;

import hudson.ExtensionList;
import jenkins.util.xml.FilteredFunctionContext;
import jenkins.model.Jenkins;
import jenkins.security.SecureRequester;

import org.dom4j.CharacterData;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.XPath;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.*;
import org.kohsuke.stapler.export.TreePruner.ByDepth;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used to expose remote access API for ".../api/"
 *
 * <p>
 * If the parent object has a <tt>_api.jelly</tt> view, it will be included
 * in the api index page.
 *
 * @author Kohsuke Kawaguchi
 * @see Exported
 * @see SecureRequester
 */
public class Api extends AbstractModelObject {
    /**
     * Model object to be exposed as XML/JSON/etc.
     */
    public final Object bean;

    public Api(Object bean) {
        this.bean = bean;
    }

    public String getDisplayName() {
        return "API";
    }

    public String getSearchUrl() {
        return "api";
    }

    /**
     * Exposes the bean as XML.
     */
    public void doXml(StaplerRequest req, StaplerResponse rsp,
                      @QueryParameter String xpath,
                      @QueryParameter String wrapper,
                      @QueryParameter String tree,
                      @QueryParameter int depth) throws IOException, ServletException {
        setHeaders(rsp);

        String[] excludes = req.getParameterValues("exclude");

        if(xpath==null && excludes==null) {
            // serve the whole thing
            rsp.serveExposedBean(req,bean,Flavor.XML);
            return;
        }

        StringWriter sw = new StringWriter();

        // first write to String
        Model p = MODEL_BUILDER.get(bean.getClass());
        TreePruner pruner = (tree!=null) ? new NamedPathPruner(tree) : new ByDepth(1 - depth);
        p.writeTo(bean,pruner,Flavor.XML.createDataWriter(bean,sw));

        // apply XPath
        FilteredFunctionContext functionContext = new FilteredFunctionContext();
        Object result;
        try {
            Document dom = new SAXReader().read(new StringReader(sw.toString()));
            // apply exclusions
            if (excludes!=null) {
                for (String exclude : excludes) {
                    XPath xExclude = dom.createXPath(exclude);
                    xExclude.setFunctionContext(functionContext);
                    List<org.dom4j.Node> list = (List<org.dom4j.Node>)xExclude.selectNodes(dom);
                    for (org.dom4j.Node n : list) {
                        Element parent = n.getParent();
                        if(parent!=null)
                            parent.remove(n);
                    }
                }
            }
            
            if(xpath==null) {
            	result = dom;
            } else {
                XPath comp = dom.createXPath(xpath);
                comp.setFunctionContext(functionContext);
                List list = comp.selectNodes(dom);
                if (wrapper!=null) {
                    Element root = DocumentFactory.getInstance().createElement(wrapper);
                    for (Object o : list) {
                        if (o instanceof String) {
                            root.addText(o.toString());
                        } else {
                            root.add(((org.dom4j.Node)o).detach());
                        }
                    }
                    result = root;
                } else if (list.isEmpty()) {
                    rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    rsp.getWriter().print(Messages.Api_NoXPathMatch(xpath));
                    return;
                } else if (list.size() > 1) {
                    rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    rsp.getWriter().print(Messages.Api_MultipleMatch(xpath,list.size()));
                    return;
                } else {
                    result = list.get(0);
                }
            }

        } catch (DocumentException e) {
            LOGGER.log(Level.FINER, "Failed to do XPath/wrapper handling. XML is as follows:"+sw, e);
            throw new IOException("Failed to do XPath/wrapper handling. Turn on FINER logging to view XML.",e);
        }


        if (isSimpleOutput(result) && !permit(req)) {
            // simple output prohibited
            rsp.sendError(HttpURLConnection.HTTP_FORBIDDEN, "primitive XPath result sets forbidden; implement jenkins.security.SecureRequester");
            return;
        }

        // switch to gzipped output
        OutputStream o = rsp.getCompressedOutputStream(req);
        try {
            if (isSimpleOutput(result)) {
                // simple output allowed
                rsp.setContentType("text/plain;charset=UTF-8");
                String text = result instanceof CharacterData ? ((CharacterData) result).getText() : result.toString();
                o.write(text.getBytes("UTF-8"));
                return;
            }

            // otherwise XML
            rsp.setContentType("application/xml;charset=UTF-8");
            new XMLWriter(o).write(result);
        } finally {
            o.close();
        }
    }

    private boolean isSimpleOutput(Object result) {
        return result instanceof CharacterData || result instanceof String || result instanceof Number || result instanceof Boolean;
    }

    /**
     * Generate schema.
     */
    public void doSchema(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        setHeaders(rsp);
        rsp.setContentType("application/xml");
        StreamResult r = new StreamResult(rsp.getOutputStream());
        new SchemaGenerator(new ModelBuilder().get(bean.getClass())).generateSchema(r);
        r.getOutputStream().close();
    }

    /**
     * Exposes the bean as JSON.
     */
    public void doJson(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (req.getParameter("jsonp") == null || permit(req)) {
            setHeaders(rsp);
            rsp.serveExposedBean(req,bean, req.getParameter("jsonp") == null ? Flavor.JSON : Flavor.JSONP);
        } else {
            rsp.sendError(HttpURLConnection.HTTP_FORBIDDEN, "jsonp forbidden; implement jenkins.security.SecureRequester");
        }
    }

    /**
     * Exposes the bean as Python literal.
     */
    public void doPython(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        setHeaders(rsp);
        rsp.serveExposedBean(req,bean, Flavor.PYTHON);
    }

    private boolean permit(StaplerRequest req) {
        for (SecureRequester r : ExtensionList.lookup(SecureRequester.class)) {
            if (r.permit(req, bean)) {
                return true;
            }
        }
        return false;
    }

    private void setHeaders(StaplerResponse rsp) {
        rsp.setHeader("X-Jenkins", Jenkins.VERSION);
        rsp.setHeader("X-Jenkins-Session", Jenkins.SESSION_HASH);
    }

    private static final Logger LOGGER = Logger.getLogger(Api.class.getName());
    private static final ModelBuilder MODEL_BUILDER = new ModelBuilder();

}
