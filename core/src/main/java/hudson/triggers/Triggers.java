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
package hudson.triggers;

import hudson.model.Descriptor;
import hudson.model.Item;

import java.util.List;
import java.util.ArrayList;

/**
 * List of all installed {@link Trigger}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class Triggers {
    public static final List<TriggerDescriptor> TRIGGERS = Descriptor.toList(
        SCMTrigger.DESCRIPTOR,
        TimerTrigger.DESCRIPTOR
    );

    /**
     * Returns a subset of {@link TriggerDescriptor}s that applys to the given item.
     */
    public static List<TriggerDescriptor> getApplicableTriggers(Item i) {
        List<TriggerDescriptor> r = new ArrayList<TriggerDescriptor>();
        for (TriggerDescriptor t : TRIGGERS) {
            if(t.isApplicable(i))
                r.add(t);
        }
        return r;
    }
}
