/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package jenkins.model.queue;

import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Represents the fact that there was at least one {@link hudson.model.Queue.JobOffer} which rejected a task.
 */
@Restricted(NoExternalUse.class)
public class CompositeCauseOfBlockage extends CauseOfBlockage {

    public final Map<String, CauseOfBlockage> uniqueReasons;

    public CompositeCauseOfBlockage(List<CauseOfBlockage> delegates) {
        uniqueReasons = new TreeMap<>();
        for (CauseOfBlockage delegate : delegates) {
            uniqueReasons.put(delegate.getShortDescription(), delegate);
        }
    }

    private static final int MAX_REASONS_TO_DISPLAY = 5;

    @Override
    public String getShortDescription() {
        int totalReasons = uniqueReasons.size();
        if (totalReasons <= MAX_REASONS_TO_DISPLAY) {
            return String.join("; ", uniqueReasons.keySet());
        }
        // Truncate long lists to avoid extremely verbose tooltips (JENKINS-45927)
        String truncatedReasons = uniqueReasons.keySet().stream()
                .limit(MAX_REASONS_TO_DISPLAY)
                .collect(Collectors.joining("; "));
        int remaining = totalReasons - MAX_REASONS_TO_DISPLAY;
        return truncatedReasons + "; ... and " + remaining + " more";
    }

    @Override
    public void print(TaskListener listener) {
        for (CauseOfBlockage delegate : uniqueReasons.values()) {
            delegate.print(listener);
        }
    }

}
