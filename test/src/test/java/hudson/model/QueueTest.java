/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.util.XStream2;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class QueueTest extends HudsonTestCase {
    /**
     * Checks the persistence of queue.
     */
    public void testPersistence() throws Exception {
        Queue q = hudson.getQueue();

        // prevent execution to push stuff into the queue
        hudson.setNumExecutors(0);
        hudson.setSlaves(hudson.getSlaves());

        FreeStyleProject testProject = createFreeStyleProject("test");
        testProject.scheduleBuild();
        q.save();

        System.out.println(FileUtils.readFileToString(new File(hudson.getRootDir(), "queue.xml")));

        assertEquals(1,q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // load the contents back
        q.load();
        assertEquals(1,q.getItems().length);

        // did it bind back to the same object?
        assertSame(q.getItems()[0].task,testProject);        
    }

    /**
     * Can {@link Queue} successfully recover removal?
     */
    public void testPersistence2() throws Exception {
        Queue q = hudson.getQueue();

        // prevent execution to push stuff into the queue
        hudson.setNumExecutors(0);
        hudson.setSlaves(hudson.getSlaves());

        FreeStyleProject testProject = createFreeStyleProject("test");
        testProject.scheduleBuild();
        q.save();

        System.out.println(FileUtils.readFileToString(new File(hudson.getRootDir(), "queue.xml")));

        assertEquals(1,q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // delete the project before loading the queue back
        testProject.delete();
        q.load();
        assertEquals(0,q.getItems().length);
    }

    public static final class FileItemPersistenceTestServlet extends HttpServlet {
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/html");
            resp.getWriter().println(
                    "<html><body><form action='/' method=post name=main enctype='multipart/form-data'>" +
                    "<input type=file name=test><input type=submit>"+
                    "</form></body></html>"
            );
        }

        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            try {
                ServletFileUpload f = new ServletFileUpload(new DiskFileItemFactory());
                List v = f.parseRequest(req);
                assertEquals(1,v.size());
                XStream2 xs = new XStream2();
                System.out.println(xs.toXML(v.get(0)));
            } catch (FileUploadException e) {
                throw new ServletException(e);
            }
        }
    }

    public void testFileItemPersistence() throws Exception {
        // TODO: write a synchronous connector?
        byte[] testData = new byte[1024];
        for( int i=0; i<testData.length; i++ )  testData[i] = (byte)i;


        Server server = new Server();
        SocketConnector connector = new SocketConnector();
        server.addConnector(connector);

        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(new ServletHolder(new FileItemPersistenceTestServlet()),"/");
        server.addHandler(handler);

        server.start();

        localPort = connector.getLocalPort();

        try {
            WebClient wc = new WebClient();
            HtmlPage p = (HtmlPage) wc.getPage("http://localhost:" + localPort + '/');
            HtmlForm f = p.getFormByName("main");
            HtmlFileInput input = (HtmlFileInput) f.getInputByName("test");
            input.setData(testData);
            f.submit();
        } finally {
            server.stop();
        }
    }

}
