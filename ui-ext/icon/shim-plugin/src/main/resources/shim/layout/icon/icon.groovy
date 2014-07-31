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
package shim.layout.icon

import org.jenkins.ui.icon.IconSet
import org.jenkins.ui.icon.shim.CoreIconTagDetector
import org.xml.sax.helpers.AttributesImpl

def attrs = context.getVariable('attrs')
def iconSpec = attrs['class']
def iconSize = attrs.iconSize

def mapAttribute(def name, def attributes) {
    def attributeValue = attrs[name];
    if (attributeValue != null) {
        attributes.addAttribute("", name, name, "CDATA", attributeValue)
    }
}

if (iconSize != null) {
    iconSpec += ' ' + IconSet.toNormalizedIconSizeClass(iconSize)
}

if (iconSpec != null) {
    if (CoreIconTagDetector.TAG_AVAILABLE) {
        def l = namespace(lib.LayoutTagLib)

        l.icon(class: iconSpec, onclick: attrs.onclick,
                title: attrs.title, style: attrs.style,
                tooltip: attrs.tooltip, alt: attrs.alt)
    } else {
        def iconDef = IconSet.icons.getIconByClassSpec(iconSpec);
        if (iconDef != null) {
            def AttributesImpl attributes = new AttributesImpl()
            def style = attrs.style

            attributes.addAttribute("", "src", "src", "CDATA", iconDef.getQualifiedUrl(context))
            if (style == null) {
                style = iconDef.getStyle()
            }
            attributes.addAttribute("", "style", "style", "CDATA", style)
            mapAttribute("onclick", attributes)
            mapAttribute("title", attributes)
            mapAttribute("tooltip", attributes)
            mapAttribute("alt", attributes)

            output.startElement("img", attributes)
            output.endElement("img")
        }
    }
}
