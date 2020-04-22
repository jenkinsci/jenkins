/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.widgets;

import hudson.model.Run;
import hudson.util.RunList;
import java.util.ArrayList;
import java.util.List;
import jenkins.util.ProgressiveRendering;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Makes it possible to incrementally render some information from a {@link RunList}.
 */
@Restricted(NoExternalUse.class)
public abstract class RunListProgressiveRendering extends ProgressiveRendering {

    private static final double MAX_RUNS = 100;
    private final List<JSONObject> results = new ArrayList<>();
    private Iterable<? extends Run<?,?>> builds;

    /** Jelly cannot call a constructor with arguments. */
    public void setBuilds(Iterable<? extends Run<?,?>> builds) {
        this.builds = builds;
    }

    @Override protected void compute() throws Exception {
        double runs = 0;
        for (Run<?,?> build : builds) {
            if (canceled()) {
                return;
            }
            JSONObject element = new JSONObject();
            calculate(build, element);
            synchronized (this) {
                results.add(element);
            }
            progress(runs / MAX_RUNS);
            runs++;
            if (runs >= MAX_RUNS) {
                break;
            }
        }
    }

    @Override protected synchronized JSON data() {
        JSONArray d = JSONArray.fromObject(results);
        results.clear();
        return d;
    }

    protected abstract void calculate(Run<?,?> build, JSONObject element);
}
