/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Michael B. Donohue
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

import hudson.diagnosis.OldDataMonitor;
import hudson.model.Queue.Task;
import hudson.model.queue.FoldableAction;
import hudson.util.XStream2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import com.thoughtworks.xstream.converters.UnmarshallingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.RunAction2;

@ExportedBean
public class CauseAction implements FoldableAction, RunAction2 {
    /**
     * @deprecated since 2009-02-28
     */
    @Deprecated
    // there can be multiple causes, so this is deprecated
    private transient Cause cause;
	
    private List<Cause> causes = new ArrayList<Cause>();

    public CauseAction(Cause c) {
   		this.causes.add(c);
   	}

    public CauseAction(Cause... c) {
   		this(Arrays.asList(c));
   	}

    public CauseAction(Collection<? extends Cause> causes) {
   		this.causes.addAll(causes);
   	}

   	public CauseAction(CauseAction ca) {
   		this.causes.addAll(ca.causes);
   	}
   	
	@Exported(visibility=2)
	public List<Cause> getCauses() {
		return causes;
	}

    /**
     * Finds the cause of the specific type.
     */
    public <T extends Cause> T findCause(Class<T> type) {
        for (Cause c : causes)
            if (type.isInstance(c))
                return type.cast(c);
        return null;
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
     * Get list of causes with duplicates combined into counters.
     * @return Map of Cause to number of occurrences of that Cause
     */
    public Map<Cause,Integer> getCauseCounts() {
        Map<Cause,Integer> result = new LinkedHashMap<Cause,Integer>();
        for (Cause c : causes) {
            if (c != null) {
                Integer i = result.get(c);
                result.put(c, i == null ? 1 : i.intValue() + 1);
            }
        }
        return result;
    }

    /**
     * @deprecated as of 1.288
     *      but left here for backward compatibility.
     */
    @Deprecated
    public String getShortDescription() {
        if(causes.isEmpty())    return "N/A";
        return causes.get(0).getShortDescription();
    }

    @Override public void onLoad(Run<?,?> owner) {
        for (Cause c : causes) {
            if (c != null) {
                c.onLoad(owner);
            }
        }
    }

    /**
     * When hooked up to build, notify {@link Cause}s.
     */
    @Override public void onAttached(Run<?,?> owner) {
        for (Cause c : causes) {
            if (c != null) {
                c.onAddedTo(owner);
            }
        }
    }

    public void foldIntoExisting(hudson.model.Queue.Item item, Task owner, List<Action> otherActions) {
        CauseAction existing = item.getAction(CauseAction.class);
        if (existing!=null) {
            existing.causes.addAll(this.causes);
            return;
        }
        // no CauseAction found, so add a copy of this one
        item.addAction(new CauseAction(this));
    }

    public static class ConverterImpl extends XStream2.PassthruConverter<CauseAction> {
        public ConverterImpl(XStream2 xstream) { super(xstream); }
        @Override protected void callback(CauseAction ca, UnmarshallingContext context) {
            // if we are being read in from an older version
            if (ca.cause != null) {
                if (ca.causes == null) ca.causes = new ArrayList<Cause>();
                ca.causes.add(ca.cause);
                OldDataMonitor.report(context, "1.288");
            }
        }
    }
}
