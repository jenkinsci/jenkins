/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.slaves.NodeSpecific;
import jenkins.model.Jenkins;
import hudson.model.Node;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;

/**
 * This Jenkins-wide extension points can participate in determining the actual node-specific path
 * of the {@link ToolInstallation} for the given {@link Node}.
 *
 * <p>
 * This extension point is useful when there's a deterministic rule of where tools are installed.
 * One can program such a logic and contribute a {@link ToolLocationTranslator}.
 * Compared to manually specifying {@link ToolLocationNodeProperty}, duplicated configurations
 * can be avoided this way. 
 *
 * <p>
 * Entry point to the translation process is
 *
 * @author Kohsuke Kawaguchi
 * @since 1.299
 * @see ToolInstallation#translateFor(Node, TaskListener)
 */
public abstract class ToolLocationTranslator implements ExtensionPoint {
    /**
     * Called for each {@link ToolInstallation#translateFor(Node, TaskListener)} invocations
     * (which normally means it's invoked for each {@link NodeSpecific#forNode(Node, TaskListener)})
     * to translate the tool location into the node specific location.
     *
     * <p>
     * If this implementation is capable of determining the location, return the path in the absolute file name.
     * (This method doesn't return {@link File} so that it can handle path names of a different OS.
     *
     * <p>
     * Otherwise return null to let other {@link ToolLocationTranslator}s a chance to do translations
     * on their own. 
     */
    public abstract String getToolHome(Node node, ToolInstallation installation, TaskListener log) throws IOException, InterruptedException;

    /**
     * Returns all the registered {@link ToolLocationTranslator}s.
     */
    public static ExtensionList<ToolLocationTranslator> all() {
        return ExtensionList.lookup(ToolLocationTranslator.class);
    }
}
