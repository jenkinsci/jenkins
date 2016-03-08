/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package hudson.diagnosis;

import hudson.Extension;
import hudson.PluginWrapper;
import hudson.init.Initializer;
import hudson.model.AdministrativeMonitor;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;

/**
 * Some old descriptors apparently has the getId() method that's used in different ways
 * and that's causing errors like JENKINS-8866, so detect and report that.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.402
 */
@Extension
public class NullIdDescriptorMonitor extends AdministrativeMonitor {

    private final List<Descriptor> problems = new ArrayList<Descriptor>();

    @Override
    public boolean isActivated() {
        return !problems.isEmpty();
    }

    public List<Descriptor> getProblems() {
        return Collections.unmodifiableList(problems);
    }

    private void verify() {
        Jenkins h = Jenkins.getInstance();
        if (h == null) {
            return;
        }
        for (Descriptor d : h.getExtensionList(Descriptor.class)) {
            PluginWrapper p = h.getPluginManager().whichPlugin(d.getClass());
            String id;
            try {
                id = d.getId();
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE,MessageFormat.format("Descriptor {0} from plugin {1} with display name {2} reported an exception for ID",
                        d, p == null ? "???" : p.getLongName(), d.getDisplayName()),t);
                problems.add(d);
                continue;
            }
            if (id==null) {
                LOGGER.severe(MessageFormat.format("Descriptor {0} from plugin {1} with display name {2} has null ID",
                        d, p==null?"???":p.getLongName(), d.getDisplayName()));
                problems.add(d);
            }
        }
    }

    @Initializer(after=EXTENSIONS_AUGMENTED)
    public static void verifyId() {
        AdministrativeMonitor.all().get(NullIdDescriptorMonitor.class).verify();
    }

    private static final Logger LOGGER = Logger.getLogger(NullIdDescriptorMonitor.class.getName());
}
