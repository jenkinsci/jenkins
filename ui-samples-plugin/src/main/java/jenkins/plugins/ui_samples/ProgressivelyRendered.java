/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package jenkins.plugins.ui_samples;

import hudson.Extension;
import java.util.LinkedList;
import java.util.List;
import jenkins.util.ProgressiveRendering;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Extension
public class ProgressivelyRendered extends UISample {

    @Override public String getDescription() {
        return "Shows how to use progressively rendered content to avoid overloading the server with a slow HTTP request.";
    }

    public ProgressiveRendering factor(final String numberS) {
        return new ProgressiveRendering() {
            final List<Integer> newFactors = new LinkedList<Integer>();
            @Override protected void compute() throws Exception {
                int number = Integer.parseInt(numberS); // try entering a nonnumeric value!
                // Deliberately inefficient:
                for (int i = 1; i <= number; i++) {
                    if (canceled()) {
                        return;
                    }
                    if (i % 1000000 == 0) {
                        Thread.sleep(10); // take a breather
                    }
                    if (number % i == 0) {
                        synchronized (this) {
                            newFactors.add(i);
                        }
                    }
                    progress(((double) i) / number);
                }
            }
            @Override protected synchronized JSON data() {
                JSONArray r = new JSONArray();
                for (int i : newFactors) {
                    r.add(i);
                }
                newFactors.clear();
                return new JSONObject().accumulate("newfactors", r);
            }
        };
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {}

}
