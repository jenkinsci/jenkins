/*
 * The MIT License
 *
 * Copyright (c) 2021 Daniel Beck
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

package jenkins.model;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Inform the admin about the migration. This affects the parts of the rename
 * that cannot be done compatibly: The self-label (although we could probably
 * improvise something), and the node name as injected into build environments.
 */
@Extension
@Restricted(NoExternalUse.class)
@Symbol("builtinNodeMigration")
public class BuiltInNodeMigration extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        return !Jenkins.get().getRenameMigrationDone();
    }

    @RequirePOST
    public void doAct(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String yes, @QueryParameter String no) throws IOException, ServletException {
        if (yes != null) {
            Jenkins.get().performRenameMigration();
        } else if (no != null) {
            disable(true);
        }
        rsp.forwardToPreviousPage(req);
    }

    @Override
    public String getDisplayName() {
        return Messages.BuiltInNodeMigration_DisplayName();
    }
}
