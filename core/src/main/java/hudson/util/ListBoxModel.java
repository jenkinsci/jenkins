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

package hudson.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.ModelObject;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

/**
 * Model object of dynamically filled list box.
 *
 * <h2>Usage</h2>
 * <p>
 * The dynamic list box support allows the SELECT element to change its options dynamically
 * by using the values given by the server.
 *
 * <p>
 * To use this, HTML needs to declare the SELECT element:
 *
 * <pre>{@code <xmp>
 * <select id='foo'>
 *   <option>Fetching values...</option>
 * </select>
 * </xmp>}</pre>
 *
 * <p>
 * The SELECT element may have initial option values (in fact in most cases having initial
 * values are desirable to avoid the client from submitting the form before the AJAX call
 * updates the SELECT element.) It should also have an ID (although if you can get
 * to the DOM element by other means, that's fine, too.)
 *
 * <p>
 * Other parts of the HTML can initiate the SELECT element update by using the "updateListBox"
 * function, defined in {@code hudson-behavior.js}. The following example does it
 * when the value of the textbox changes:
 *
 * <pre>{@code <xmp>
 * <input type="textbox" onchange="updateListBox('list','optionValues?value='+encode(this.value))"/>
 * </xmp>}</pre>
 *
 * <p>
 * The first argument is the SELECT element or the ID of it (see Prototype.js {@code $(...)} function.)
 * The second argument is the URL that returns the options list.
 *
 * <p>
 * The URL usually maps to the {@code doXXX} method on the server, which uses {@link ListBoxModel}
 * for producing option values. See the following example:
 *
 * <pre>
 * public ListBoxModel doOptionValues(&#64;QueryParameter("value") String value) throws IOException, ServletException {
 *   ListBoxModel m = new ListBoxModel();
 *   for (int i=0; i&lt;5; i++)
 *     m.add(value+i,value+i);
 *   // make the third option selected initially
 *   m.get(3).selected = true;
 *   return m;
 * }
 * </pre>
 * @since 1.123
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class ListBoxModel extends ArrayList<ListBoxModel.Option> implements HttpResponse {

    @ExportedBean(defaultVisibility = 999)
    public static final class Option {
        /**
         * Text to be displayed to user.
         */
        @Exported
        @NonNull
        public String name;
        /**
         * The value that gets sent to the server when the form is submitted.
         */
        @Exported
        @NonNull
        public String value;

        /**
         * True to make this item selected.
         */
        @Exported
        public boolean selected;

        public Option(@NonNull String name, @NonNull String value) {
            this(name, value, false);
        }

        public Option(@NonNull String name) {
            this(name, name, false);
        }

        public Option(@NonNull String name, @NonNull String value, boolean selected) {
            this.name = name;
            this.value = value;
            this.selected = selected;
        }

        @Override public String toString() {
            return name + "=" + value + (selected ? "[selected]" : "");
        }

    }

    public ListBoxModel(int initialCapacity) {
        super(initialCapacity);
    }

    public ListBoxModel() {
    }

    public ListBoxModel(Collection<Option> c) {
        super(c);
    }

    public ListBoxModel(Option... data) {
        super(Arrays.asList(data));
    }

    public void add(@NonNull String displayName, @NonNull String value) {
        add(new Option(displayName, value));
    }

    public void add(ModelObject usedForDisplayName, @NonNull String value) {
        add(usedForDisplayName.getDisplayName(), value);
    }

    /**
     * A version of the {@link #add(String, String)} method where the display name and the value are the same.
     */
    public ListBoxModel add(@NonNull String nameAndValue) {
        add(nameAndValue, nameAndValue);
        return this;
    }

    /**
     * @since 2.475
     */
    public void writeTo(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(ListBoxModel.class, getClass(), "writeTo", StaplerRequest.class, StaplerResponse.class)) {
            try {
                writeTo(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            writeToImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #writeTo(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    public void writeTo(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            writeToImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void writeToImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        rsp.serveExposedBean(req, this, Flavor.JSON);
    }

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
        writeTo(req, rsp);
    }

    /**
     * @deprecated
     *      Exposed for stapler. Not meant for programmatic consumption.
     */
    @Exported
    @Deprecated
    public Option[] values() {
        return toArray(new Option[size()]);
    }
}
