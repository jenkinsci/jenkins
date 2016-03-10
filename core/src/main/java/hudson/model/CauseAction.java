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
import java.util.Collections;
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

    /** @deprecated JENKINS-33467 inefficient */
    @Deprecated
    private transient List<Cause> causes;

    private Map<Cause,Integer> causeBag = new LinkedHashMap<>();

    public CauseAction(Cause c) {
   		this.causeBag.put(c, 1);
   	}

    private void addCause(Cause c) {
        synchronized (causeBag) {
            Integer cnt = causeBag.get(c);
            causeBag.put(c, cnt == null ? 1 : cnt + 1);
        }
    }
    private void addCauses(Collection<? extends Cause> causes) {
        for (Cause cause : causes) {
            addCause(cause);
        }
    }

    public CauseAction(Cause... c) {
   		this(Arrays.asList(c));
   	}

    public CauseAction(Collection<? extends Cause> causes) {
   		addCauses(causes);
   	}

   	public CauseAction(CauseAction ca) {
   		addCauses(ca.getCauses());
   	}
   	
	@Exported(visibility=2)
	public List<Cause> getCauses() {
		List<Cause> r = new ArrayList<>();
        for (Map.Entry<Cause,Integer> entry : causeBag.entrySet()) {
            r.addAll(Collections.nCopies(entry.getValue(), entry.getKey()));
        }
        return r;
	}

    /**
     * Finds the cause of the specific type.
     */
    public <T extends Cause> T findCause(Class<T> type) {
        for (Cause c : causeBag.keySet())
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
        return Collections.unmodifiableMap(causeBag);
    }

    /**
     * @deprecated as of 1.288
     *      but left here for backward compatibility.
     */
    @Deprecated
    public String getShortDescription() {
        if (causeBag.isEmpty()) {
            return "N/A";
        }
        return causeBag.keySet().iterator().next().getShortDescription();
    }

    @Override public void onLoad(Run<?,?> owner) {
        for (Cause c : causeBag.keySet()) {
            if (c != null) {
                c.onLoad(owner);
            }
        }
    }

    /**
     * When hooked up to build, notify {@link Cause}s.
     */
    @Override public void onAttached(Run<?,?> owner) {
        for (Cause c : causeBag.keySet()) {
            if (c != null) {
                c.onAddedTo(owner);
            }
        }
    }

    public void foldIntoExisting(hudson.model.Queue.Item item, Task owner, List<Action> otherActions) {
        CauseAction existing = item.getAction(CauseAction.class);
        if (existing!=null) {
            existing.addCauses(getCauses());
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
                if (ca.causeBag == null) {
                    ca.causeBag = new LinkedHashMap<>();
                }
                ca.addCause(ca.cause);
                OldDataMonitor.report(context, "1.288");
                ca.cause = null;
            } else if (ca.causes != null) {
                if (ca.causeBag == null) {
                    ca.causeBag = new LinkedHashMap<>();
                }
                ca.addCauses(ca.causes);
                OldDataMonitor.report(context, "1.653");
                ca.causes = null;
            }
        }
    }
}
