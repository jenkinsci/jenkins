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
Behaviour.specify("IMG.treeview-fold-control", 'projectViewNested', 0, function(e) {
    e.onexpanded = function() {
        var img = this;
        var tr = findAncestor(img, "TR");
        var tail = tr.nextSibling;

        img.oncollapsed = function() {
            while (tr.nextSibling != tail)
                tr.nextSibling.remove();
        };

        // fetch the nested view and load it when it's ready
        new Ajax.Request(img.getAttribute("url"), {
            method : 'post',
            onComplete : function(x) {
                var cont = document.createElement("div");
                cont.innerHTML = x.responseText;
                var rows = $A(cont.firstChild.rows);
                var anim = { opacity: { from:0, to:1 } };
                rows.reverse().each(function(r) {
                    YAHOO.util.Dom.setStyle(r, 'opacity', 0); // hide
                    YAHOO.util.Dom.insertAfter(r, tr);
                    Behaviour.applySubtree(r);
                    new YAHOO.util.Anim(r, anim, 0.3).animate();
                });
            }
        });
    };
    e = null;
});
