/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Michael B. Donohue
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

import hudson.model.Queue.Task;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class CauseAction implements FoldableAction {
	@Deprecated
	// there can be multiple causes, so this is deprecated
	private transient Cause cause;
	
	private List<Cause> causes = new ArrayList<Cause>();

	@Exported(visibility=2)
	public List<Cause> getCauses() {
		return causes;
	}
		
	public CauseAction(Cause c) {
		this.causes.add(c);
	}
	
	public CauseAction(CauseAction ca) {
		this.causes.addAll(ca.causes);
	}
	
	public String getDisplayName() {
		return "Cause";
	}

	public String getIconFileName() {
		// no icon
		return null;
	}

	public String getUrlName() {
		return "cause";
	}

    /**
     * @deprecated as of 1.288
     *      but left here for backward compatibility.
     */
    public String getShortDescription() {
        if(causes.isEmpty())    return "N/A";
        return causes.get(0).getShortDescription();
    }

	public void foldIntoExisting(Task t, List<Action> actions) {
		for(Action action : actions) {
			if(action instanceof CauseAction) {
				this.causes.addAll(((CauseAction)action).causes);
				return;
			}
		}
		// no CauseAction found, so add a copy of this one
		actions.add(new CauseAction(this));
	}
	
	private Object readResolve() {
		// if we are being read in from an older version
		if(cause != null) {
			if(causes == null) causes=new ArrayList<Cause>();
			causes.add(cause);
		}
		return this;
	} 
}
