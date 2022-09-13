/*
 * The MIT License
 *
 * Copyright (c) 2022 Jenkins contributors
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

package jenkins.util;

import static jenkins.util.MemoryReductionUtil.EMPTY_STRING_ARRAY;

import hudson.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import jenkins.plugins.DetachedPluginsUtil;
import net.sf.json.JSONArray;

public class PluginLabelUtil {
    private static HashMap<String, String> renamedLabels;

    private static String canonicalLabel(String label) {
        if (renamedLabels == null) {
            renamedLabels = new HashMap<>();
            try (InputStream is = PluginLabelUtil.class.getResourceAsStream("/jenkins/canonical-labels.txt")) {
                DetachedPluginsUtil.configLines(is).forEach(line -> {
                    String[] pieces = line.split(" ");
                    renamedLabels.put(pieces[0], pieces[1]);
                });
            } catch (IOException x) {
                throw new ExceptionInInitializerError(x);
            }
        }
        return renamedLabels.getOrDefault(label, label);
    }

    /**
     * Replaces labels with their canonical form and removes duplicates
     * @param labels labels array
     * @return unique canonical labels
     */
    public static String[] canonicalLabels(JSONArray labels) {
        HashSet<String> uniqueLabels = new HashSet<>();
        for (Object label : labels) {
            uniqueLabels.add(Util.intern(canonicalLabel(label.toString())));
        }
        return uniqueLabels.toArray(EMPTY_STRING_ARRAY);
    }

}
