/*
 * The MIT License
 *
 * Copyright (c) 2025 CloudBees, Inc.
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

package jenkins.security.csp.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.csp.CspReceiver;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Basic {@link jenkins.security.csp.CspReceiver} that just logs received reports.
 */
@Restricted(NoExternalUse.class)
@Extension
public class LoggingReceiver implements CspReceiver {
    private static final Logger LOGGER = Logger.getLogger(jenkins.security.csp.impl.LoggingReceiver.class.getName());

    @Override
    public void report(@NonNull ViewContext viewContext, String userId, @NonNull JSONObject report) {
        if (userId == null) {
            LOGGER.log(Level.FINEST, "Received anonymous report for context {0}: {1}", new Object[]{viewContext, report});
        } else {
            LOGGER.log(Level.FINE, "Received report from {0} for context {1}: {2}", new Object[]{userId, viewContext, report});
        }
    }
}
