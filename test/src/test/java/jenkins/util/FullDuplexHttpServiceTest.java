/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package jenkins.util;

import static org.junit.Assert.assertEquals;

import hudson.cli.HttpUploadDownloadStream;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;

public class FullDuplexHttpServiceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(FullDuplexHttpService.class, Level.FINE).record(HttpUploadDownloadStream.class, Level.FINE);

    @Test
    public void smokes() throws Exception {
        logging.record("org.eclipse.jetty", Level.ALL);
        HttpUploadDownloadStream con = new HttpUploadDownloadStream(r.getURL(), "test/", null);
        InputStream is = con.getInputStream();
        OutputStream os = con.getOutputStream();
        os.write(33);
        os.flush();
        Logger.getLogger(FullDuplexHttpServiceTest.class.getName()).info("uploaded initial content");
        assertEquals(0, is.read()); // see FullDuplexHttpStream.getInputStream
        assertEquals(66, is.read());
    }

    @TestExtension("smokes")
    public static class Endpoint extends InvisibleAction implements RootAction {
        private final transient Map<UUID, FullDuplexHttpService> duplexServices = new HashMap<>();

        @Override
        public String getUrlName() {
            return "test";
        }

        public HttpResponse doIndex() {
            return new FullDuplexHttpService.Response(duplexServices) {
                @Override
                protected FullDuplexHttpService createService(StaplerRequest2 req, UUID uuid) throws IOException, InterruptedException {
                    return new FullDuplexHttpService(uuid) {
                        @Override
                        protected void run(InputStream upload, OutputStream download) throws IOException, InterruptedException {
                            int x = upload.read();
                            download.write(x * 2);
                        }
                    };
                }
            };
        }
    }

    @TestExtension("smokes")
    public static class EndpointCrumbExclusion extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            if ("/test/".equals(request.getPathInfo())) {
                chain.doFilter(request, response);
                return true;
            }
            return false;
        }
    }

}
