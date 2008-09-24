package hudson.model;

import org.jvnet.hudson.test.HudsonTestCase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.bio.SocketConnector;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.List;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import hudson.util.XStream2;

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
