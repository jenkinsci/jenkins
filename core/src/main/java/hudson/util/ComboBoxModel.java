/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, CollabNet
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

package hudson.util;

import static java.util.Arrays.asList;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import net.sf.json.JSONArray;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Flavor;

/**
 * Model object for dynamically filed combo box, which is really just {@code ArrayList<String>}
 *
 * @author Kohsuke Kawaguchi
 */
public class ComboBoxModel extends ArrayList<String> implements HttpResponse {
    public ComboBoxModel(int initialCapacity) {
        super(initialCapacity);
    }

    public ComboBoxModel() {
    }

    public ComboBoxModel(Collection<? extends String> c) {
        super(c);
    }

    public ComboBoxModel(String... values) {
        this(asList(values));
    }

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
        rsp.setContentType(Flavor.JSON.contentType);
        PrintWriter w = rsp.getWriter();
        JSONArray.fromObject(this).write(w);
    }
}
