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

import hudson.model.Item;
import hudson.util.DescriptorList;
import hudson.Extension;

import java.util.List;

/**
 * List of all installed {@link Trigger}s.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated as of 1.286
 *      See each member for how to migrate your code.
 */
@Deprecated
public class Triggers {
    /**
     * All registered {@link TriggerDescriptor} implementations.
     * @deprecated as of 1.286
     *      Use {@link Trigger#all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final List<TriggerDescriptor> TRIGGERS = (List)new DescriptorList<Trigger<?>>((Class)Trigger.class);
//    Descriptor.toList(
//        SCMTrigger.DESCRIPTOR,
//        TimerTrigger.DESCRIPTOR
//    );

    /**
     * Returns a subset of {@link TriggerDescriptor}s that applys to the given item.
     *
     * @deprecated as of 1.286
     *      Use {@link Trigger#for_(Item)}.
     */
    @Deprecated
    public static List<TriggerDescriptor> getApplicableTriggers(Item i) {
        return Trigger.for_(i);
    }
}
