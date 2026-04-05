/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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

package hudson.tools;

import hudson.Extension;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

/**
 * Actually runs installations.
 * @since 1.305
 */
@Extension
public class InstallerTranslator extends ToolLocationTranslator {

    private static final Map<Node, Map<ToolInstallation, Semaphore>> mutexByNode = new WeakHashMap<>();

    @Override
    public String getToolHome(Node node, ToolInstallation tool, TaskListener log) throws IOException, InterruptedException {
        if (node.getRootPath() == null) {
            log.error(node.getDisplayName() + " is offline; cannot locate " + tool.getName());
            return null;
        }
        InstallSourceProperty isp = tool.getProperties().get(InstallSourceProperty.class);
        if (isp == null) {
            return null;
        }

        ArrayList<String> inapplicableInstallersMessages = new ArrayList<>();

        for (ToolInstaller installer : isp.installers) {
            if (installer.appliesTo(node)) {
                Semaphore semaphore;
                synchronized (mutexByNode) {
                    Map<ToolInstallation, Semaphore> mutexByTool = mutexByNode.computeIfAbsent(node, unused -> new WeakHashMap<>());
                    semaphore = mutexByTool.computeIfAbsent(tool, unused -> new Semaphore(1));
                }
                semaphore.acquire();
                try {
                    return installer.performInstallation(tool, node, log).getRemote();
                } finally {
                    semaphore.release();
                }
            } else {
                inapplicableInstallersMessages.add(Messages.CannotBeInstalled(
                        installer.getDescriptor().getDisplayName(),
                        tool.getName(),
                        node.getDisplayName()));
            }
        }
        for (String message : inapplicableInstallersMessages) {
            log.getLogger().println(message);
        }
        return null;
    }

}
