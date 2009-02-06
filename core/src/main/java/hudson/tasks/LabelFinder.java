/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.tasks;

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Descriptor;
import hudson.remoting.VirtualChannel;
import hudson.tasks.labelers.OSLabeler;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Support for autoconfiguration of nodes.
 *
 * @author Stephen Connolly
 */
public interface LabelFinder {

    public static final List<DynamicLabeler> LABELERS = new ArrayList<DynamicLabeler>()/*{
        // Taking adding default DynamicLabelers out of main trunk
        {
            add(OSLabeler.INSTANCE);
        }
    }*/;

    /**
     * Find the labels that the node supports.
     * @param node The Node
     * @return a set of labels.
     */
    Set<String> findLabels(VirtualChannel channel);
}
