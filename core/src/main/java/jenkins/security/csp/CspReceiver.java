/*
 * The MIT License
 *
 * Copyright (c) 2021-2025 Daniel Beck, CloudBees, Inc.
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

package jenkins.security.csp;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.User;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Extension point for receivers of Content Security Policy reports.
 */
@Restricted(Beta.class)
public interface CspReceiver extends ExtensionPoint {

    void report(@NonNull ViewContext viewContext, @CheckForNull User user, @NonNull JSONObject report);

    record ViewContext(String className, String viewName) {
    }

    @Restricted(NoExternalUse.class)
    @Extension
    class LoggingReceiver implements CspReceiver {
        private static final Logger LOGGER = Logger.getLogger(LoggingReceiver.class.getName());

        @Override
        public void report(@NonNull ViewContext viewContext, User user, @NonNull JSONObject report) {
            if (user == null) {
                LOGGER.log(Level.FINEST, "Received anonymous report for context: " + viewContext);
            }
            LOGGER.log(Level.FINER, "Received report: {0}", report);
        }
    }
}
