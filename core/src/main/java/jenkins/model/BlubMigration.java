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
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Inform the admin about the migration. This affects the parts of the rename
 * that cannot be done compatibly: The self-label (although we could probably
 * improvise something), and the node name as injected into build environments.
 *
 * TODO what else could break and needs to be based off blubMigrationNeeded?
 */
@Extension
@Restricted(NoExternalUse.class)
@Symbol("blubMigration")
public class BlubMigration extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        final Boolean v = Jenkins.get().blubMigrationNeeded;
        return v == null || v;
    }

    @RequirePOST
    public void doAct(StaplerRequest req, StaplerResponse rsp, @QueryParameter String yes, @QueryParameter String no) throws IOException, ServletException {
        if (yes != null) {
            final Jenkins j = Jenkins.get();
            j.blubMigrationNeeded = false;
            j.save();
        } else if (no != null) {
            disable(true);
        }
        rsp.forwardToPreviousPage(req);
    }

    @Override
    public String getDisplayName() {
        return "Blub Migration"; // TODO i18n
    }
}
