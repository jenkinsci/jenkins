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
package hudson;

import hudson.model.PermalinkProjectAction.Permalink;
import hudson.util.EditDistance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link List} of {@link Permalink}s with some convenience methods.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.255
 */
public final class PermalinkList extends ArrayList<Permalink> {
    public PermalinkList(Collection<? extends Permalink> c) {
        super(c);
    }

    public PermalinkList() {
    }

    /**
     * Gets the {@link Permalink} by its {@link Permalink#getId() id}.
     *
     * @return null if not found
     */
    public Permalink get(String id) {
        for (Permalink p : this)
            if(p.getId().equals(id))
                return p;
        return null;
    }

    /**
     * Finds the closest name match by its ID.
     */
    public Permalink findNearest(String id) {
        List<String> ids = new ArrayList<String>();
        for (Permalink p : this)
            ids.add(p.getId());
        String nearest = EditDistance.findNearest(id, ids);
        if(nearest==null)   return null;
        return get(nearest);
    }
}
