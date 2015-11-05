package hudson.init.impl;

import hudson.init.Initializer;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.compression.CompressionFilter;
import org.kohsuke.stapler.compression.UncaughtExceptionHandler;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.kohsuke.stapler.Stapler;

/**
 * @author Kohsuke Kawaguchi
 */
public class InstallUncaughtExceptionHandler {
    @Initializer
    public static void init(final Jenkins j) throws IOException {
        CompressionFilter.setUncaughtExceptionHandler(j.servletContext, new UncaughtExceptionHandler() {
            @Override
            public void reportException(Throwable e, ServletContext context, HttpServletRequest req, HttpServletResponse rsp) throws ServletException, IOException {
                req.setAttribute("javax.servlet.error.exception",e);
                try {
                    WebApp.get(j.servletContext).getSomeStapler()
                            .invoke(req,rsp, Jenkins.getInstance(), "/oops");
                } catch (ServletException x) {
                    if (!Stapler.isSocketException(x)) {
                        throw x;
                    }
                } catch (IOException x) {
                    if (!Stapler.isSocketException(x)) {
                        throw x;
                    }
                }
            }
        });
    }
}
