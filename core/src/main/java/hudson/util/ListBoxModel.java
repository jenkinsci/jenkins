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

import hudson.model.ModelObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

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
 * <pre><xmp>
 * <select id='foo'>
 *   <option>Fetching values...</optoin>
 * </select>
 * </xmp></pre>
 *
 * <p>
 * The SELECT element may have initial option values (in fact in most cases having initial
 * values are desirable to avoid the client from submitting the form before the AJAX call
 * updates the SELECT element.) It should also have an ID (although if you can get
 * to the DOM element by other means, that's fine, too.)
 *
 * <p>
 * Other parts of the HTML can initiate the SELECT element update by using the "updateListBox"
 * function, defined in <tt>hudson-behavior.js</tt>. The following example does it
 * when the value of the textbox changes:
 *
 * <pre><xmp>
 * <input type="textbox" onchange="updateListBox('list','optionValues?value='+encode(this.value))"/>
 * </xmp></pre>
 *
 * <p>
 * The first argument is the SELECT element or the ID of it (see Prototype.js <tt>$(...)</tt> function.)
 * The second argument is the URL that returns the options list.
 *
 * <p>
 * The URL usually maps to the <tt>doXXX</tt> method on the server, which uses {@link ListBoxModel}
 * for producing option values. See the following example:
 *
 * <pre>
 * public ListBoxModel doOptionValues(@QueryParameter("value") String value) throws IOException, ServletException {
 *   ListBoxModel m = new ListBoxModel();
 *   for(int i=0; i<5; i++)
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

    @ExportedBean(defaultVisibility=999)
    public static final class Option {
        /**
         * Text to be displayed to user.
         */
        @Exported
        public String name;
        /**
         * The value that gets sent to the server when the form is submitted.
         */
        @Exported
        public String value;

        /**
         * True to make this item selected.
         */
        @Exported
        public boolean selected;

        public Option(String name, String value) {
            this(name,value,false);
        }

        public Option(String name) {
            this(name,name,false);
        }

        public Option(String name, String value, boolean selected) {
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

    public void add(String displayName, String value) {
        add(new Option(displayName,value));
    }

    public void add(ModelObject usedForDisplayName, String value) {
        add(usedForDisplayName.getDisplayName(), value);
    }

    /**
     * A version of the {@link #add(String, String)} method where the display name and the value are the same. 
     */
    public ListBoxModel add(String nameAndValue) {
        add(nameAndValue,nameAndValue);
        return this;
    }

    public void writeTo(StaplerRequest req,StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveExposedBean(req,this,Flavor.JSON);
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        writeTo(req,rsp);
    }

    /**
     * @deprecated
     *      Exposed for stapler. Not meant for programatic consumption.
     */
    @Exported
    @Deprecated
    public Option[] values() {
        return toArray(new Option[size()]);
    }
}
