/*
 * The MIT License
 * 
 * Copyright (c) 2004-2015, Sun Microsystems, Inc., Kohsuke Kawaguchi, Johnathon Jamison, Yahoo! Inc.
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

/**
 * &lt;textarea> tag on steroids.
 * The textarea will be rendered to fit the content. It also gets the resize handle.
 * 
 * @param field Used for databinding. TBD.
 * @param name This becomes @name of the &lt;textarea> tag.
 *   If @field is specified, this value is inferred from it.
 * @param value The initial value of the field. This becomes the value of the &lt;textarea> tag.
 *   If @field is specified, the current property from the "instance" object
 *   will be set as the initial value automatically,
 *   which is the recommended approach.
 * @param default The default value of the text box, in case both @value is and 'instance[field]' is null.
 * @param checkUrl If specified, the value entered in this input field will be checked (via AJAX)
 *   against this URL, and errors will be rendered under the text field.
 *   
 *   If @field is specified, this will be inferred automatically,
 *   which is the recommended approach.
 * @param checkMethod If specified, the HTTP method to use for input field will be checked (via AJAX)
 * @param codemirror-mode Turns this text area into CodeMirror-assisted code editing text area.
 *   This attribute specifies the mode of CodeMirror, such as "text/x-java".
 *   See http://codemirror.net/ for more details.
 * @codemirror-config Specifies additional key/value pairs in the JSON format (except the start and end bracket)
 *   to be passed as CodeMirror option object.
 * @previewEndpoint If specified, this text area has preview feature.
 *   The previewEndpoint is used to obtain formatted html.
 */

import org.apache.commons.lang.StringUtils;

def st = namespace("jelly:stapler");
def f  = namespace(lib.FormTagLib);

f.prepareDatabinding();

def defaultValue = attrs.default?:"";
def value = attrs.value ?: instance && attrs.field && instance[attrs.field] ? instance[attrs.field] : defaultValue;

if (attrs["previewEndpoint"] != null || attrs["codemirror-mode"] != null) {
  st.adjunct(includes: "lib.form.textarea.textarea");
}

if (attrs["codemirror-mode"] != null) {
  st.adjunct(includes: "org.kohsuke.stapler.codemirror.mode.${attrs["codemirror-mode"]}.${attrs["codemirror-mode"]},org.kohsuke.stapler.codemirror.theme.default");
}

def name = attrs.name ?: "_." + attrs.field;

textarea (id: attrs.id, style: attrs.style,
    name: name,
    class: "setting-input ${attrs.checkUrl!=null?'validated':''} ${attrs['codemirror-mode']!=null?'codemirror':''} ${attrs.class}",
    checkUrl: attrs.checkUrl, checkDependsOn: attrs.checkDependsOn, checkMethod: attrs.checkMethod,
    rows: h.determineRows(value),
    readonly: attrs.readonly,
    "codemirror-mode": attrs["codemirror-mode"],
    "codemirror-config": attrs["codemirror-config"]
) {
  if (StringUtils.startsWith(value, "\r") || StringUtils.startsWith(value, "\n")) {
    text("\n");
  }
  st.out(value: value);
}

if (binding.getVariables().get("customizedFields") != null && attrs.field != null && value != defaultValue) {
  customizedFields.add(name);
}

div (class: "textarea-handle"); // resize handle

if (attrs.previewEndpoint != null) {
  div (class: "textarea-preview-container") {
    if (attrs.previewEndpoint == '/markupFormatter/previewDescription') {
      text("[${app.markupFormatter.descriptor.displayName}]");
      st.nbsp();
    }
    a (href: "#", class: "textarea-show-preview", previewEndpoint: attrs.previewEndpoint) {
      text(_("Preview"));
    }
    st.nbsp();
    a (href: "#", class: "textarea-hide-preview") {
      text(_("Hide preview"));
    }
    div (class: "textarea-preview"); // div for preview
  }
}
