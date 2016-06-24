/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Stephen Connolly.
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
package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.TransientActionFactory;

/**
 * Extension point for inserting transient {@link hudson.model.Action}s to {@link hudson.model.Computer}s.
 * <p>
 * To register your implementation, put {@link hudson.Extension} on your subtype.
 *
 * @author Stephen Connolly
 * @since 1.405
 * @see hudson.model.Action
 * @see TransientActionFactory
 */
public abstract class TransientComputerActionFactory implements ExtensionPoint {
    /**
     * Creates actions for the given computer.
     *
     * @param target
     *      The computer for which the action objects are requested. Never null.
     * @return
     *      Can be empty but must not be null.
     */
    public abstract Collection<? extends Action> createFor(Computer target);

    /**
     * Returns all the registered {@link TransientComputerActionFactory}s.
     */
    public static ExtensionList<TransientComputerActionFactory> all() {
        return ExtensionList.lookup(TransientComputerActionFactory.class);
    }


    /**
     * Creates {@link Action}s for a node, using all registered {@link TransientComputerActionFactory}s.
     */
	public static List<Action> createAllFor(Computer target) {
		List<Action> result = new ArrayList<Action>();
		for (TransientComputerActionFactory f: all()) {
			result.addAll(f.createFor(target));
		}
		return result;
	}


}
