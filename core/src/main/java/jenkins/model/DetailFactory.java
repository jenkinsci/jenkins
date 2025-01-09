/*
 * The MIT License
 *
 * Copyright 2025 Jan Faracik
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

package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Actionable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Allows you to add multiple details to a Run at once.
 * @param <T> the type of object to add to; typically an {@link Actionable} subtype
 * @since TODO
 */
public abstract class DetailFactory<T extends Actionable> implements ExtensionPoint {

    public abstract Class<T> type();

    public abstract @NonNull Collection<? extends Detail> createFor(@NonNull T target);

    @Restricted(NoExternalUse.class)
    public static <T extends Actionable> Iterable<DetailFactory<T>> factoriesFor(Class<T> type) {
        List<DetailFactory<T>> result = new ArrayList<>();
        for (DetailFactory<T> wf : ExtensionList.lookup(DetailFactory.class)) {
            if (wf.type().isAssignableFrom(type)) {
                result.add(wf);
            }
        }
        return result;
    }
}
