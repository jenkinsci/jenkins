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

package hudson.util;

import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Model object used to display "Hudson is restarting".
 * <p>
 * Set this object to {@link ServletContext#setAttribute(String, Object)} "app" while
 * the loading activity is taking place.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonIsRestarting {
    private boolean safeRestart;

    /**
     * @since 2.414
     */
    public HudsonIsRestarting(boolean safeRestart) {
        this.safeRestart = safeRestart;
    }

    @Deprecated
    public HudsonIsRestarting() {
        this.safeRestart = false;
    }

    public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, InterruptedException {
        rsp.setStatus(SC_SERVICE_UNAVAILABLE);
        req.getView(this, "index.jelly").forward(req, rsp);
    }

    /**
     * @since 2.414
     */
    public boolean isSafeRestart() {
        return safeRestart;
    }
}
