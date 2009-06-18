/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Yahoo! Inc.
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
//
//
// JavaScript for Hudson
//     See http://www.ibm.com/developerworks/web/library/wa-memleak/?ca=dgr-lnxw97JavascriptLeaks
//     for memory leak patterns and how to prevent them.
//

// create a new object whose prototype is the given object
function object(o) {
    function F() {}
    F.prototype = o;
    return new F();
}

// id generator
var iota = 0;

// crumb information
var crumb = {
    fieldName: null,
    value: null,

    init: function(crumbField, crumbValue) {
        this.fieldName = crumbField;
        this.value = crumbValue;
    },

    /**
     * Adds the crumb value into the given hash and returns the hash.
     */
    wrap: function(hash) {
        if(this.fieldName!=null)
            hash[this.fieldName]=this.value;
        return hash;
    },

    /**
     * Puts a hidden input field to the form so that the form submission will have the crumb value
     */
    appendToForm : function(form) {
        if(this.fieldName==null)    return; // noop
        var div = document.createElement("div");
        div.innerHTML = "<input type=hidden name='"+this.fieldName+"' value='"+this.value+"'>";
        form.appendChild(div);
    }
}

// Form check code
//========================================================
var FormChecker = {
    // pending requests
    queue : [],

    // conceptually boolean, but doing so create concurrency problem.
    // that is, during unit tests, the AJAX.send works synchronously, so
    // the onComplete happens before the send method returns. On a real environment,
    // more likely it's the other way around. So setting a boolean flag to true or false
    // won't work.
    inProgress : 0,

    /**
     * Schedules a form field check. Executions are serialized to reduce the bandwidth impact.
     *
     * @param url
     *      Remote doXYZ URL that performs the check. Query string should include the field value.
     * @param method
     *      HTTP method. GET or POST. I haven't confirmed specifics, but some browsers seem to cache GET requests.
     * @param target
     *      HTML element whose innerHTML will be overwritten when the check is completed.
     */
    delayedCheck : function(url, method, target) {
        if(url==null || method==null || target==null)
            return; // don't know whether we should throw an exception or ignore this. some broken plugins have illegal parameters
        this.queue.push({url:url, method:method, target:target});
        this.schedule();
    },

    sendRequest : function(url, params) {
        if (params.method == "post") {
            var idx = url.indexOf('?');
            params.parameters = url.substring(idx + 1);
            url = url.substring(0, idx);
        }
        new Ajax.Request(url, params);
    },

    schedule : function() {
        if (this.inProgress>0)  return;
        if (this.queue.length == 0) return;

        var next = this.queue.shift();
        this.sendRequest(next.url, {
            method : next.method,
            onComplete : function(x) {
                next.target.innerHTML = x.responseText;
                FormChecker.inProgress--;
                FormChecker.schedule();
            }
        });
        this.inProgress++;
    }
}

function toValue(e) {
    // compute the form validation value to be sent to the server
    var type = e.getAttribute("type");
    if(type!=null && type.toLowerCase()=="checkbox")
        return e.checked;
    return encode(e.value);
}

// find the nearest ancestor node that has the given tag name
function findAncestor(e, tagName) {
    do {
        e = e.parentNode;
    } while (e != null && e.tagName != tagName);
    return e;
}

function findFollowingTR(input, className) {
    // identify the parent TR
    var tr = input;
    while (tr.tagName != "TR")
        tr = tr.parentNode;

    // then next TR that matches the CSS
    do {
        tr = tr.nextSibling;
    } while (tr.tagName != "TR" || tr.className != className);

    return tr;
}

/**
 * Traverses a form in the reverse document order starting from the given element (but excluding it),
 * until the given filter matches, or run out of an element.
 */
function findPrevious(src,filter) {
    function prev(e) {
        var p = e.previousSibling;
        if(p==null) return e.parentNode;
        while(p.lastChild!=null)
            p = p.lastChild;
        return p;
    }

    while(src!=null) {
        src = prev(src);
        if(src==null)   break;
        if(filter(src))
            return src;
    }
    return null;
}
/**
 * Traverses a form in the reverse document order and finds an INPUT element that matches the given name.
 */
function findPreviousFormItem(src,name) {
    var name2 = "_."+name; // handles <textbox field="..." /> notation silently
    return findPrevious(src,function(e){ return (e.tagName=="INPUT" || e.tagName=="TEXTAREA") && (e.name==name || e.name==name2); });
}


// shared tooltip object
var tooltip;



// Behavior rules
//========================================================
// using tag names in CSS selector makes the processing faster
function registerValidator(e) {
    e.targetElement = findFollowingTR(e, "validation-error-area").firstChild.nextSibling;
    e.targetUrl = function() {
        return eval(this.getAttribute("checkUrl"));
    };
    var method = e.getAttribute("checkMethod");
    if (!method) method = "get";

    FormChecker.delayedCheck(e.targetUrl(), method, e.targetElement);

    var checker = function() {
        var target = this.targetElement;
        FormChecker.sendRequest(this.targetUrl(), {
            method : method,
            onComplete : function(x) {
                target.innerHTML = x.responseText;
            }
        });
    }
    var oldOnchange = e.onchange;
    if(typeof oldOnchange=="function") {
        e.onchange = function() { checker.call(this); oldOnchange.call(this); }
    } else
        e.onchange = checker;
    e.onblur = checker;

    e = null; // avoid memory leak
}

/**
 * Wraps a <button> into YUI button.
 *
 * @param e
 *      button element
 * @param onclick
 *      onclick handler
 */
function makeButton(e,onclick) {
    var h = e.onclick;
    var clsName = e.className;
    var n = e.name;
    var btn = new YAHOO.widget.Button(e,{});
    if(onclick!=null)
        btn.addListener("click",onclick);
    if(h!=null)
        btn.addListener("click",h);
    var be = btn.get("element");
    Element.addClassName(be,clsName);
    if(n!=null) // copy the name
        be.setAttribute("name",n);
    return btn;
}

/*
    If we are inside 'to-be-removed' class, some HTML altering behaviors interact badly, because
    the behavior re-executes when the removed master copy gets reinserted later.
 */
function isInsideRemovable(e) {
    return Element.ancestors(e).find(function(f){return f.hasClassName("to-be-removed");});
}

var hudsonRules = {
    "BODY" : function() {
        tooltip = new YAHOO.widget.Tooltip("tt", {context:[], zindex:999});
    },

// do the ones that extract innerHTML so that they can get their original HTML before
// other behavior rules change them (like YUI buttons.)

    "DIV.hetero-list-container" : function(e) {
        if(isInsideRemovable(e))    return;

        // components for the add button
        var menu = document.createElement("SELECT");
        var btn = findElementsBySelector(e,"INPUT.hetero-list-add")[0];
        YAHOO.util.Dom.insertAfter(menu,btn);

        var prototypes = e.lastChild;
        while(!Element.hasClassName(prototypes,"prototypes"))
            prototypes = prototypes.previousSibling;
        var insertionPoint = prototypes.previousSibling;    // this is where the new item is inserted.

        // extract templates
        var templates = []; var i=0;
        for(var n=prototypes.firstChild;n!=null;n=n.nextSibling,i++) {
            var name = n.getAttribute("name");
            var tooltip = n.getAttribute("tooltip");
            menu.options[i] = new Option(n.getAttribute("title"),""+i);
            templates.push({html:n.innerHTML, name:name, tooltip:tooltip});
        }
        Element.remove(prototypes);

        // D&D support
        function prepareDD(e) {
            var dd = new DragDrop(e);
            var h = e;
            // locate a handle
            while(!Element.hasClassName(h,"dd-handle"))
                h = h.firstChild;
            dd.setHandleElId(h);
        }
        var withDragDrop = Element.hasClassName(e,"with-drag-drop");
        if(withDragDrop) {
            for(e=e.firstChild; e!=null; e=e.nextSibling) {
                if(Element.hasClassName(e,"repeated-chunk"))
                    prepareDD(e);
            }
        }

        var menuButton = new YAHOO.widget.Button(btn, { type: "menu", menu: menu });
        menuButton.getMenu().clickEvent.subscribe(function(type,args,value) {
            var t = templates[parseInt(args[1].value)]; // where this args[1] comes is a real mystery

            var nc = document.createElement("div");
            nc.className = "repeated-chunk";
            nc.setAttribute("name",t.name);
            nc.innerHTML = t.html;
            insertionPoint.parentNode.insertBefore(nc, insertionPoint);
            if(withDragDrop)    prepareDD(nc);
            
            Behaviour.applySubtree(nc);
        });

        menuButton.getMenu().renderEvent.subscribe(function(type,args,value) {
            // hook up tooltip for menu items
            var items = menuButton.getMenu().getItems();
            for(i=0; i<items.length; i++) {
                var t = templates[i].tooltip;
                if(t!=null)
                    applyTooltip(items[i].element,t);
            }
        });
    },

    "DIV.repeated-container" : function(e) {
        if(isInsideRemovable(e))    return;

        // compute the insertion point
        var ip = e.lastChild;
        while (!Element.hasClassName(ip, "repeatable-insertion-point"))
            ip = ip.previousSibling;
        // set up the logic
        object(repeatableSupport).init(e, e.firstChild, ip);
    },


    "TABLE.sortable" : function(e) {// sortable table
        ts_makeSortable(e);
    },

    "TABLE.progress-bar" : function(e) {// sortable table
        e.onclick = function() {
            var href = this.getAttribute("href");
            if(href!=null)      window.location = href;
        }
        e = null; // avoid memory leak
    },

    "INPUT.advancedButton" : function(e) {
        makeButton(e,function(e) {
            var link = e.target;
            while(!Element.hasClassName(link,"advancedLink"))
                link = link.parentNode;
            link.style.display = "none"; // hide the button

            var container = link.nextSibling.firstChild; // TABLE -> TBODY

            var tr = link;
            while (tr.tagName != "TR")
                tr = tr.parentNode;

            // move the contents of the advanced portion into the main table
            var nameRef = tr.getAttribute("nameref");
            while (container.lastChild != null) {
                var row = container.lastChild;
                if(nameRef!=null)
                    row.setAttribute("nameref",nameRef);
                tr.parentNode.insertBefore(row, tr.nextSibling);
            }
        });
        e = null; // avoid memory leak
    },

    "INPUT.expandButton" : function(e) {
        makeButton(e,function(e) {
            var link = e.target;
            while(!Element.hasClassName(link,"advancedLink"))
                link = link.parentNode;
            link.style.display = "none";
            link.nextSibling.style.display="block";
        });
        e = null; // avoid memory leak
    },

// scripting for having default value in the input field
    "INPUT.has-default-text" : function(e) {
        var defaultValue = e.value;
        Element.addClassName(e, "defaulted");
        e.onfocus = function() {
            if (this.value == defaultValue) {
                this.value = "";
                Element.removeClassName(this, "defaulted");
            }
        }
        e.onblur = function() {
            if (this.value == "") {
                this.value = defaultValue;
                Element.addClassName(this, "defaulted");
            }
        }
        e = null; // avoid memory leak
    },

// <label> that doesn't use ID, so that it can be copied in <repeatable>
    "LABEL.attach-previous" : function(e) {
        e.onclick = function() {
            var e = this;
            while(e.tagName!="INPUT")
                e=e.previousSibling;
            e.click();
        }
        e = null;
    },

// form fields that are validated via AJAX call to the server
// elements with this class should have two attributes 'checkUrl' that evaluates to the server URL.
    "INPUT.validated" : registerValidator,
    "SELECT.validated" : registerValidator,
    "TEXTAREA.validated" : registerValidator,

// validate form values to be a number
    "INPUT.number" : function(e) {
        e.targetElement = findFollowingTR(e, "validation-error-area").firstChild.nextSibling;
        e.onchange = function() {
            if (this.value.match(/^(\d+|)$/)) {
                this.targetElement.innerHTML = "";
            } else {
                this.targetElement.innerHTML = "<div class=error>Not a number</div>";
            }
        }
        e = null; // avoid memory leak
    },

    "A.help-button" : function(e) {
        e.onclick = function() {
            var tr = findFollowingTR(this, "help-area");
            var div = tr.firstChild.nextSibling.firstChild;

            if (div.style.display != "block") {
                div.style.display = "block";
                // make it visible
                new Ajax.Request(this.getAttribute("helpURL"), {
                    method : 'get',
                    onComplete : function(x) {
                        div.innerHTML = x.responseText;
                    }
                });
            } else {
                div.style.display = "none";
            }

            return false;
        }
        e = null; // avoid memory leak
    },

// deferred client-side clickable map.
// this is useful where the generation of <map> element is time consuming
    "IMG[lazymap]" : function(e) {
        new Ajax.Request(
            e.getAttribute("lazymap"),
            {
                method : 'get',
                onSuccess : function(x) {
                    var div = document.createElement("div");
                    document.body.appendChild(div);
                    div.innerHTML = x.responseText;
                    var id = "map" + (iota++);
                    div.firstChild.setAttribute("name", id);
                    e.setAttribute("usemap", "#" + id);
                }
            });
    },

    // button to add a new repeatable block
    "INPUT.repeatable-add" : function(e) {
        makeButton(e,function(e) {
            repeatableSupport.onAdd(e.target);
        });
        e = null; // avoid memory leak
    },

    "INPUT.repeatable-delete" : function(e) {
        makeButton(e,function(e) {
            repeatableSupport.onDelete(e.target);
        });
        e = null; // avoid memory leak
    },

    // resizable text area
    "TEXTAREA" : function(textarea) {
        if(Element.hasClassName(textarea,"rich-editor")) {
            // rich HTML editor
            try {
                var editor = new YAHOO.widget.Editor(textarea, {
                    dompath: true,
                    animate: true,
                    handleSubmit: true
                });
                // probably due to the timing issue, we need to let the editor know
                // that DOM is ready
                editor.DOMReady=true;
                editor.fireQueue();
                editor.render();
            } catch(e) {
                alert(e);
            }
            return;
        }

        var handle = textarea.nextSibling;
        if(handle==null || handle.className!="textarea-handle") return;

        var Event = YAHOO.util.Event;

        handle.onmousedown = function(ev) {
            ev = Event.getEvent(ev);
            var offset = textarea.offsetHeight-Event.getPageY(ev);
            textarea.style.opacity = 0.5;
            document.onmousemove = function(ev) {
                ev = Event.getEvent(ev);
                function max(a,b) { if(a<b) return b; else return a; }
                textarea.style.height = max(32, offset + Event.getPageY(ev)) + 'px';
                return false;
            };
            document.onmouseup = function() {
                document.onmousemove = null;
                document.onmouseup = null;
                textarea.style.opacity = 1;
            }
        };
        handle.ondblclick = function() {
            textarea.style.height = "";
            textarea.rows = textarea.value.split("\n").length;
        }
    },

    // structured form submission
    "FORM" : function(form) {
        crumb.appendToForm(form);
        if(Element.hasClassName("no-json"))
            return;
        // add the hidden 'json' input field, which receives the form structure in JSON
        var div = document.createElement("div");
        div.innerHTML = "<input type=hidden name=json value=init>";
        form.appendChild(div);
        
        form.onsubmit = function() { buildFormTree(this); };
        form = null; // memory leak prevention
    },

    // hook up tooltip.
    //   add nodismiss="" if you'd like to display the tooltip forever as long as the mouse is on the element.
    "[tooltip]" : function(e) {
        applyTooltip(e,e.getAttribute("tooltip"));
    },

    "INPUT.submit-button" : function(e) {
        makeButton(e);
    },

    "INPUT.yui-button" : function(e) {
        makeButton(e);
    },

    "TR.optional-block-start": function(e) { // see optionalBlock.jelly
        // set start.ref to checkbox in preparation of row-set-end processing
        var checkbox = e.firstChild.firstChild;
        e.setAttribute("ref", checkbox.id = "cb"+(iota++));
    },

    "TR.row-set-end": function(e) { // see rowSet.jelly and optionalBlock.jelly
        // figure out the corresponding start block
        var end = e;

        for( var depth=0; ; e=e.previousSibling) {
            if(Element.hasClassName(e,"row-set-end"))        depth++;
            if(Element.hasClassName(e,"row-set-start"))      depth--;
            if(depth==0)    break;
        }
        var start = e;

        var ref = start.getAttribute("ref");
        if(ref==null)
            start.id = ref = "rowSetStart"+(iota++);

        applyNameRef(start,end,ref);
    },

    "BODY TR.optional-block-start": function(e) { // see optionalBlock.jelly
        // this is prefixed by a pointless BODY so that two processing for optional-block-start
        // can sandwitch row-set-end
        // this requires "TR.row-set-end" to mark rows
        var checkbox = e.firstChild.firstChild;
        updateOptionalBlock(checkbox,false);
    },

    // image that shows [+] or [-], with hover effect.
    // oncollapsed and onexpanded will be called when the button is triggered.
    "IMG.fold-control" : function(e) {
        function changeTo(e,img) {
            var src = e.src;
            e.src = src.substring(0,src.lastIndexOf('/'))+"/"+e.getAttribute("state")+img;
        }
        e.onmouseover = function() {
            changeTo(this,"-hover.png");
        };
        e.onmouseout = function() {
            changeTo(this,".png");
        };
        e.parentNode.onclick = function(event) {
            var e = this.firstChild;
            var s = e.getAttribute("state");
            if(s=="plus") {
                e.setAttribute("state","minus");
                if(e.onexpanded)    e.onexpanded();
            } else {
                e.setAttribute("state","plus");
                if(e.oncollapsed)    e.oncollapsed();
            }
            changeTo(e,"-hover.png");
            YAHOO.util.Event.stopEvent(event);
            return false;
        };
        e = null; // memory leak prevention
    }
};

function applyTooltip(e,text) {
        // copied from YAHOO.widget.Tooltip.prototype.configContext to efficiently add a new element
        // event registration via YAHOO.util.Event.addListener leaks memory, so do it by ourselves here
        e.onmouseover = function(ev) {
            var delay = this.getAttribute("nodismiss")!=null ? 99999999 : 5000;
            tooltip.cfg.setProperty("autodismissdelay",delay);
            return tooltip.onContextMouseOver.call(this,YAHOO.util.Event.getEvent(ev),tooltip);
        }
        e.onmousemove = function(ev) { return tooltip.onContextMouseMove.call(this,YAHOO.util.Event.getEvent(ev),tooltip); }
        e.onmouseout  = function(ev) { return tooltip.onContextMouseOut .call(this,YAHOO.util.Event.getEvent(ev),tooltip); }
        e.title = text;
        e = null; // avoid memory leak
    }

Behaviour.register(hudsonRules);



function xor(a,b) {
    // convert both values to boolean by '!' and then do a!=b
    return !a != !b;
}

// used by editableDescription.jelly to replace the description field with a form
function replaceDescription() {
    var d = document.getElementById("description");
    d.firstChild.nextSibling.innerHTML = "<div class='spinner-right'>loading...</div>";
    new Ajax.Request(
        "./descriptionForm",
        {
          onComplete : function(x) {
            d.innerHTML = x.responseText;
            Behaviour.applySubtree(d);
            d.getElementsByTagName("TEXTAREA")[0].focus();
          }
        }
    );
    return false;
}

function applyNameRef(s,e,id) {
    $(id).groupingNode = true;
    // s contains the node itself
    for(var x=s.nextSibling; x!=e; x=x.nextSibling) {
        // to handle nested <f:rowSet> correctly, don't overwrite the existing value
        if(x.getAttribute("nameRef")==null)
            x.setAttribute("nameRef",id);
    }
}

// used by optionalBlock.jelly to update the form status
//   @param c     checkbox element
function updateOptionalBlock(c,scroll) {
    // find the start TR
    var s = c;
    while(!Element.hasClassName(s, "optional-block-start"))
        s = s.parentNode;
    var tbl = s.parentNode;
    var i = false;
    var o = false;

    var checked = xor(c.checked,Element.hasClassName(c,"negative"));
    var lastRow = null;

    for (var j = 0; tbl.rows[j]; j++) {
        var n = tbl.rows[j];

        if (i && Element.hasClassName(n, "optional-block-end"))
            o = true;

        if (i && !o) {
            if (checked) {
                n.style.display = "";
                lastRow = n;
            } else
                n.style.display = "none";
        }

        if (n==s) {
            if (n.getAttribute('hasHelp') == 'true')
                j++;
            i = true;
        }
    }

    if(checked && scroll) {
        var D = YAHOO.util.Dom;

        var r = D.getRegion(s);
        if(lastRow!=null)   r = r.union(D.getRegion(lastRow));
        scrollIntoView(r);
    }

    if (c.name == 'hudson-tools-InstallSourceProperty') {
        // Hack to hide tool home when "Install automatically" is checked.
        var homeField = findPreviousFormItem(c, 'home');
        if (homeField != null && homeField.value == '') {
            var tr = findAncestor(homeField, 'TR');
            if (tr != null) {
                tr.style.display = c.checked ? 'none' : '';
            }
        }
    }
}


//
// Auto-scroll support for progressive log output.
//   See http://radio.javaranch.com/pascarello/2006/08/17/1155837038219.html
//
function AutoScroller(scrollContainer) {
    // get the height of the viewport.
    // See http://www.howtocreate.co.uk/tutorials/javascript/browserwindow
    function getViewportHeight() {
        if (typeof( window.innerWidth ) == 'number') {
            //Non-IE
            return window.innerHeight;
        } else if (document.documentElement && ( document.documentElement.clientWidth || document.documentElement.clientHeight )) {
            //IE 6+ in 'standards compliant mode'
            return document.documentElement.clientHeight;
        } else if (document.body && ( document.body.clientWidth || document.body.clientHeight )) {
            //IE 4 compatible
            return document.body.clientHeight;
        }
        return null;
    }

    return {
        bottomThreshold : 25,
        scrollContainer: scrollContainer,

        getCurrentHeight : function() {
            var scrollDiv = $(this.scrollContainer);

            if (scrollDiv.scrollHeight > 0)
                return scrollDiv.scrollHeight;
            else
                if (objDiv.offsetHeight > 0)
                    return scrollDiv.offsetHeight;

            return null; // huh?
        },

        // return true if we are in the "stick to bottom" mode
        isSticking : function() {
            var scrollDiv = $(this.scrollContainer);
            var currentHeight = this.getCurrentHeight();

            // when used with the BODY tag, the height needs to be the viewport height, instead of
            // the element height.
            //var height = ((scrollDiv.style.pixelHeight) ? scrollDiv.style.pixelHeight : scrollDiv.offsetHeight);
            var height = getViewportHeight();
            var diff = currentHeight - scrollDiv.scrollTop - height;
            // window.alert("currentHeight=" + currentHeight + ",scrollTop=" + scrollDiv.scrollTop + ",height=" + height);

            return diff < this.bottomThreshold;
        },

        scrollToBottom : function() {
            var scrollDiv = $(this.scrollContainer);
            scrollDiv.scrollTop = this.getCurrentHeight();
        }
    };
}

// scroll the current window to display the given element or the region.
function scrollIntoView(e) {
    function calcDelta(ex1,ex2,vx1,vw) {
        var vx2=vx1+vw;
        var a;
        a = Math.min(vx1-ex1,vx2-ex2);
        if(a>0)     return -a;
        a = Math.min(ex1-vx1,ex2-vx2);
        if(a>0)     return a;
        return 0;
    }

    var D = YAHOO.util.Dom;

    var r;
    if(e.tagName!=null) r = D.getRegion(e);
    else                r = e;

    var dx = calcDelta(r.left,r.right, document.body.scrollLeft, D.getViewportWidth());
    var dy = calcDelta(r.top, r.bottom,document.body.scrollTop,  D.getViewportHeight());
    window.scrollBy(dx,dy);
}

// used in expandableTextbox.jelly to change a input field into a text area
function expandTextArea(button,id) {
    button.style.display="none";
    var field = document.getElementById(id);
    var value = field.value.replace(/ +/g,'\n');
    var n = field;
    while(n.tagName!="TABLE")
        n = n.parentNode;
    n.parentNode.innerHTML =
        "<textarea rows=8 class='setting-input' name='"+field.name+"'>"+value+"</textarea>";
}


// refresh a part of the HTML specified by the given ID,
// by using the contents fetched from the given URL.
function refreshPart(id,url) {
    var f = function() {
        new Ajax.Request(url, {
            onSuccess: function(rsp) {
                var hist = $(id);
                var p = hist.parentNode;
                var next = hist.nextSibling;
                p.removeChild(hist);

                var div = document.createElement('div');
                div.innerHTML = rsp.responseText;

                var node = div.firstChild;
                p.insertBefore(node, next);

                Behaviour.applySubtree(node);

                if(isRunAsTest) return;
                refreshPart(id,url);
            }
        });
    };
    // if run as test, just do it once and do it now to make sure it's working,
    // but don't repeat.
    if(isRunAsTest) f();
    else    window.setTimeout(f, 5000);
}


/*
    Perform URL encode.
    Taken from http://www.cresc.co.jp/tech/java/URLencoding/JavaScript_URLEncoding.htm
*/
function encode(str){
    var s, u;
    var s0 = "";                // encoded str

    for (var i = 0; i < str.length; i++){   // scan the source
        s = str.charAt(i);
        u = str.charCodeAt(i);          // get unicode of the char

        if (s == " "){s0 += "+";}       // SP should be converted to "+"
        else {
            if ( u == 0x2a || u == 0x2d || u == 0x2e || u == 0x5f || ((u >= 0x30) && (u <= 0x39)) || ((u >= 0x41) && (u <= 0x5a)) || ((u >= 0x61) && (u <= 0x7a))){     // check for escape
                s0 = s0 + s;           // don't escape
            } else {                      // escape
                if ((u >= 0x0) && (u <= 0x7f)){     // single byte format
                    s = "0"+u.toString(16);
                    s0 += "%"+ s.substr(s.length-2);
                } else
                if (u > 0x1fffff){     // quaternary byte format (extended)
                    s0 += "%" + (0xF0 + ((u & 0x1c0000) >> 18)).toString(16);
                    s0 += "%" + (0x80 + ((u & 0x3f000) >> 12)).toString(16);
                    s0 += "%" + (0x80 + ((u & 0xfc0) >> 6)).toString(16);
                    s0 += "%" + (0x80 + (u & 0x3f)).toString(16);
                } else
                if (u > 0x7ff){        // triple byte format
                    s0 += "%" + (0xe0 + ((u & 0xf000) >> 12)).toString(16);
                    s0 += "%" + (0x80 + ((u & 0xfc0) >> 6)).toString(16);
                    s0 += "%" + (0x80 + (u & 0x3f)).toString(16);
                } else {                      // double byte format
                    s0 += "%" + (0xc0 + ((u & 0x7c0) >> 6)).toString(16);
                    s0 += "%" + (0x80 + (u & 0x3f)).toString(16);
                }
            }
        }
    }
    return s0;
}

// when there are multiple form elements of the same name,
// this method returns the input field of the given name that pairs up
// with the specified 'base' input element.
Form.findMatchingInput = function(base, name) {
    // find the FORM element that owns us
    var f = base;
    while (f.tagName != "FORM")
        f = f.parentNode;

    var bases = Form.getInputs(f, null, base.name);
    var targets = Form.getInputs(f, null, name);

    for (var i=0; i<bases.length; i++) {
        if (bases[i] == base)
            return targets[i];
    }

    return null;        // not found
}

// used witih <dropdownList> and <dropdownListBlock> to control visibility
function updateDropDownList(sel) {
    for (var i = 0; i < sel.subForms.length; i++) {
        var show = sel.selectedIndex == i;
        var f = sel.subForms[i];
        var td = f.start;
        while (true) {
            td.style.display = (show ? "" : "none");
            if(show)
                td.removeAttribute("field-disabled");
            else    // buildFormData uses this attribute and ignores the contents
                td.setAttribute("field-disabled","true");
            if (td == f.end) break;
            td = td.nextSibling;
        }
    }
}


// code for supporting repeatable.jelly
var repeatableSupport = {
    // set by the inherited instance to the insertion point DIV
    insertionPoint: null,

    // HTML text of the repeated chunk
    blockHTML: null,

    // containing <div>.
    container: null,

    // block name for structured HTML
    name : null,

    // do the initialization
    init : function(container,master,insertionPoint) {
        this.container = $(container);
        this.container.tag = this;
        master = $(master);
        this.blockHTML = master.innerHTML;
        master.parentNode.removeChild(master);
        this.insertionPoint = $(insertionPoint);
        this.name = master.getAttribute("name");
        this.update();
    },

    // insert one more block at the insertion position
    expand : function() {
        // importNode isn't supported in IE.
        // nc = document.importNode(node,true);
        var nc = document.createElement("div");
        nc.className = "repeated-chunk";
        nc.setAttribute("name",this.name);
        nc.innerHTML = this.blockHTML;
        this.insertionPoint.parentNode.insertBefore(nc, this.insertionPoint);

        Behaviour.applySubtree(nc);
        this.update();
    },

    // update CSS classes associated with repeated items.
    update : function() {
        var children = [];
        for( var n=this.container.firstChild; n!=null; n=n.nextSibling )
            if(Element.hasClassName(n,"repeated-chunk"))
                children.push(n);

        if(children.length==0) {
            // noop
        } else
        if(children.length==1) {
            children[0].className = "repeated-chunk first last only";
        } else {
            children[0].className = "repeated-chunk first";
            for(var i=1; i<children.length-1; i++)
                children[i].className = "repeated-chunk middle";
            children[children.length-1].className = "repeated-chunk last";
        }
    },

    // these are static methods that don't rely on 'this'

    // called when 'delete' button is clicked
    onDelete : function(n) {
        while (!Element.hasClassName(n,"repeated-chunk"))
            n = n.parentNode;

        var p = n.parentNode;
        p.removeChild(n);
        p.tag.update();
    },

    // called when 'add' button is clicked
    onAdd : function(n) {
        while(n.tag==null)
            n = n.parentNode;
        n.tag.expand();
        // Hack to hide tool home when a new tool has some installers.
        var inputs = n.getElementsByTagName('INPUT');
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            if (input.name == 'hudson-tools-InstallSourceProperty') {
                updateOptionalBlock(input, false);
            }
        }
    }
};

// Used by radioBlock.jelly to wire up expandable radio block
function addRadioBlock(id) {
    // prototype object to be duplicated for each radio button group
    var radioBlockSupport = {
        buttons : null,

        updateButtons : function() {
            for( var i=0; i<this.buttons.length; i++ )
                this.buttons[i]();
        },

        // update one block based on the status of the given radio button
        updateSingleButton : function(radio, blockStart, blockEnd) {
            var tbl = blockStart.parentNode;
            var i = false;
            var o = false;
            var show = radio.checked;

            for (var j = 0; tbl.rows[j]; j++) {
                var n = tbl.rows[j];

                if (n == blockEnd)
                    o = true;

                if (i && !o) {
                    if (show)
                        n.style.display = "";
                    else
                        n.style.display = "none";
                }

                if (n == blockStart) {
                    i = true;
                    if (n.getAttribute('hasHelp') == 'true')
                        j++;
                }
            }
        }
    };

    // when one radio button is clicked, we need to update foldable block for
    // other radio buttons with the same name. To do this, group all the
    // radio buttons with the same name together and hang it under the form object
    var r = document.getElementById('Rb' + id);
    var f = r.form;
    var radios = f.radios;
    if (radios == null)
        f.radios = radios = {};

    var g = radios[r.name];
    if (g == null) {
        radios[r.name] = g = object(radioBlockSupport);
        g.buttons = [];
    }

    var s = document.getElementById("rb_s"+id);
    var e = document.getElementById("rb_e"+id);

    var u = function() {
        g.updateSingleButton(r,s,e);
    };
    applyNameRef(s,e,'Rb'+id);
    g.buttons.push(u);

    // apply the initial visibility
    u();

    // install event handlers to update visibility.
    // needs to use onclick and onchange for Safari compatibility
    r.onclick = r.onchange = function() { g.updateButtons(); };
}


function updateBuildHistory(ajaxUrl,nBuild) {
    if(isRunAsTest) return;
    $('buildHistory').headers = ["n",nBuild];

    function updateBuilds() {
        var bh = $('buildHistory');
        new Ajax.Request(ajaxUrl, {
            requestHeaders: bh.headers,
            onSuccess: function(rsp) {
                var rows = bh.rows;

                //delete rows with transitive data
                while (rows.length > 2 && Element.hasClassName(rows[1], "transitive"))
                    Element.remove(rows[1]);

                // insert new rows
                var div = document.createElement('div');
                div.innerHTML = rsp.responseText;
                Behaviour.applySubtree(div);

                var pivot = rows[0];
                var newRows = div.firstChild.rows;
                for (var i = newRows.length - 1; i >= 0; i--) {
                    pivot.parentNode.insertBefore(newRows[i], pivot.nextSibling);
                }

                // next update
                bh.headers = ["n",rsp.getResponseHeader("n")];
                window.setTimeout(updateBuilds, 5000);
            }
        });
    }
    window.setTimeout(updateBuilds, 5000);
}

// send async request to the given URL (which will send back serialized ListBoxModel object),
// then use the result to fill the list box.
function updateListBox(listBox,url) {
    new Ajax.Request(url, {
        onSuccess: function(rsp) {
            var l = $(listBox);
            while(l.length>0)   l.options[0] = null;

            var opts = eval('('+rsp.responseText+')').values;
            for( var i=0; i<opts.length; i++ ) {
                l.options[i] = new Option(opts[i].name,opts[i].value);
                if(opts[i].selected)
                    l.selectedIndex = i;
            }
        },
        onFailure: function(rsp) {
            var l = $(listBox);
            l.options[0] = null;
        }
    });
}

// get the cascaded computed style value. 'a' is the style name like 'backgroundColor'
function getStyle(e,a){
  if(document.defaultView && document.defaultView.getComputedStyle)
    return document.defaultView.getComputedStyle(e,null).getPropertyValue(a.replace(/([A-Z])/g, "-$1"));
  if(e.currentStyle)
    return e.currentStyle[a];
  return null;
};

// set up logic behind the search box
function createSearchBox(searchURL) {
    var ds = new YAHOO.widget.DS_XHR(searchURL+"suggest",["suggestions","name"]);
    ds.queryMatchCase = false;
    var ac = new YAHOO.widget.AutoComplete("search-box","search-box-completion",ds);
    ac.typeAhead = false;

    var box   = $("search-box");
    var sizer = $("search-box-sizer");
    var comp  = $("search-box-completion");
    var minW  = $("search-box-minWidth");

    Behaviour.addLoadEvent(function(){
        // make sure all three components have the same font settings
        function copyFontStyle(s,d) {
            var ds = d.style;
            ds.fontFamily = getStyle(s,"fontFamily");
            ds.fontSize = getStyle(s,"fontSize");
            ds.fontStyle = getStyle(s,"fontStyle");
            ds.fontWeight = getStyle(s,"fontWeight");
        }

        copyFontStyle(box,sizer);
        copyFontStyle(box,minW);
    });

    // update positions and sizes of the components relevant to search
    function updatePos() {
        function max(a,b) { if(a>b) return a; else return b; }

        sizer.innerHTML = box.value;
        var w = max(sizer.offsetWidth,minW.offsetWidth);
        box.style.width =
        comp.style.width = 
        comp.firstChild.style.width = (w+60)+"px";

        var pos = YAHOO.util.Dom.getXY(box);
        pos[1] += YAHOO.util.Dom.get(box).offsetHeight + 2;
        YAHOO.util.Dom.setXY(comp, pos);
    }

    updatePos();
    box.onkeyup = updatePos;
}


//
// structured form submission handling
//   see http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
function buildFormTree(form) {
    try {
        // I initially tried to use an associative array with DOM elemnets as keys
        // but that doesn't seem to work neither on IE nor Firefox.
        // so I switch back to adding a dynamic property on DOM.
        form.formDom = {}; // root object

        var doms = []; // DOMs that we added 'formDom' for.
        doms.push(form);

        function shortenName(name) {
            // [abc.def.ghi] -> abc.def.ghi
            if(name.startsWith('['))
                return name.substring(1,name.length-1);

            // abc.def.ghi -> ghi
            var idx = name.lastIndexOf('.');
            if(idx>=0)  name = name.substring(idx+1);
            return name;
        }

        function addProperty(parent,name,value) {
            name = shortenName(name);
            if(parent[name]!=null) {
                if(parent[name].push==null) // is this array?
                    parent[name] = [ parent[name] ];
                parent[name].push(value);
            } else {
                parent[name] = value;
            }
        }

        // find the grouping parent node, which will have @name.
        // then return the corresponding object in the map
        function findParent(e) {
            while(e!=form) {
                e = e.parentNode;

                // this is used to create a group where no single containing parent node exists,
                // like <optionalBlock>
                var nameRef = e.getAttribute("nameRef");
                if(nameRef!=null)
                    e = $(nameRef);

                if(e.getAttribute("field-disabled")!=null)
                    return {};  // this field shouldn't contribute to the final result

                var name = e.getAttribute("name");
                if(name!=null) {
                    if(e.tagName=="INPUT" && !xor(e.checked,Element.hasClassName(e,"negative")))
                        return {};  // field is not active

                    var m = e.formDom;
                    if(m==null) {
                        // this is a new grouping node
                        doms.push(e);
                        e.formDom = m = {};
                        addProperty(findParent(e), name, m);
                    }
                    return m;
                }
            }

            return form.formDom; // guaranteed non-null
        }

        var jsonElement = null;

        for( var i=0; i<form.elements.length; i++ ) {
            var e = form.elements[i];
            if(e.name=="json") {
                jsonElement = e;
                continue;
            }
            if(e.tagName=="FIELDSET")
                continue;
            if(e.tagName=="SELECT" && e.multiple) {
                var values = [];
                for( var o=0; o<e.options.length; o++ ) {
                    var opt = e.options.item(o);
                    if(opt.selected)
                        values.push(opt.value);
                }
                addProperty(findParent(e),e.name,values);
                continue;
            }
                
            var p;
            var type = e.getAttribute("type");
            if(type==null)  type="";
            switch(type.toLowerCase()) {
            case "button":
            case "submit":
                break;
            case "checkbox":
                p = findParent(e);
                var checked = xor(e.checked,Element.hasClassName(e,"negative"));
                if(!e.groupingNode)
                    addProperty(p, e.name, checked);
                else {
                    if(checked)
                        addProperty(p, e.name, e.formDom = {});
                }
                break;
            case "file":
                // to support structured form submission with file uploads,
                // rename form field names to unique ones, and leave this name mapping information
                // in JSON. this behavior is backward incompatible, so only do
                // this when
                p = findParent(e);
                if(e.getAttribute("jsonAware")!=null) {
                    var on = e.getAttribute("originalName");
                    if(on!=null) {
                        addProperty(p,on,e.name);
                    } else {
                        var uniqName = "file"+(iota++);
                        addProperty(p,e.name,uniqName);
                        e.setAttribute("originalName",e.name);
                        e.name = uniqName;
                    }
                }
                // switch to multipart/form-data to support file submission
                // @enctype is the standard, but IE needs @encoding.
                form.enctype = form.encoding = "multipart/form-data";
                break;
            case "radio":
                if(!e.checked)  break;
                if(e.groupingNode) {
                    p = findParent(e);
                    addProperty(p, e.name, e.formDom = { value: e.value });
                    break;
                }

                // otherwise fall through
            default:
                p = findParent(e);
                addProperty(p, e.name, e.value);
                break;
            }
        }

        jsonElement.value = Object.toJSON(form.formDom);

        // clean up
        for( i=0; i<doms.length; i++ )
            doms[i].formDom = null;


        return jsonElement.value;
    } catch(e) {
        alert(e);
    }
}

// this used to be in prototype.js but it must have been removed somewhere between 1.4.0 to 1.5.1
String.prototype.trim = function() {
    var temp = this;
    var obj = /^(\s*)([\W\w]*)(\b\s*$)/;
    if (obj.test(temp))
        temp = temp.replace(obj, '$2');
    obj = /  /g;
    while (temp.match(obj))
        temp = temp.replace(obj, " ");
    return temp;
}



var hoverNotification = (function() {
    var msgBox;
    var body;

    // animation effect that automatically hide the message box
    var effect = function(overlay, dur) {
        var o = YAHOO.widget.ContainerEffect.FADE(overlay, dur);
        o.animateInCompleteEvent.subscribe(function() {
            window.setTimeout(function() {
                msgBox.hide()
            }, 1500);
        });
        return o;
    }

    function init() {
        if(msgBox!=null)  return;   // already initialized

        var div = document.createElement("DIV");
        document.body.appendChild(div);
        div.innerHTML = "<div id=hoverNotification><div class=bd></div></div>";
        body = $('hoverNotification');
        
        msgBox = new YAHOO.widget.Overlay(body, {
          visible:false,
          width:"10em",
          zIndex:1000,
          effect:{
            effect:effect,
            duration:0.25
          }
        });
        msgBox.render();
    }

    return function(title,anchor) {
        init();
        body.innerHTML = title;
        var xy = YAHOO.util.Dom.getXY(anchor);
        xy[0] += 48;
        xy[1] += anchor.offsetHeight;
        msgBox.cfg.setProperty("xy",xy);
        msgBox.show();
    };
})();

/*
    D&D implementation for heterogeneous list.
 */
var DragDrop = function(id, sGroup, config) {
    DragDrop.superclass.constructor.apply(this, arguments);
};

(function() {
    var Dom = YAHOO.util.Dom;
    var Event = YAHOO.util.Event;
    var DDM = YAHOO.util.DragDropMgr;

    YAHOO.extend(DragDrop, YAHOO.util.DDProxy, {
        startDrag: function(x, y) {
            var el = this.getEl();

            this.resetConstraints();
            this.setXConstraint(0,0);    // D&D is for Y-axis only

            // set Y constraint to be within the container
            var totalHeight = el.parentNode.offsetHeight;
            var blockHeight = el.offsetHeight;
            this.setYConstraint(el.offsetTop, totalHeight-blockHeight-el.offsetTop);

            el.style.visibility = "hidden";

            this.goingUp = false;
            this.lastY = 0;
        },

        endDrag: function(e) {
            var srcEl = this.getEl();
            var proxy = this.getDragEl();

            // Show the proxy element and animate it to the src element's location
            Dom.setStyle(proxy, "visibility", "");
            var a = new YAHOO.util.Motion(
                proxy, {
                    points: {
                        to: Dom.getXY(srcEl)
                    }
                },
                0.2,
                YAHOO.util.Easing.easeOut
            )
            var proxyid = proxy.id;
            var thisid = this.id;

            // Hide the proxy and show the source element when finished with the animation
            a.onComplete.subscribe(function() {
                    Dom.setStyle(proxyid, "visibility", "hidden");
                    Dom.setStyle(thisid, "visibility", "");
                });
            a.animate();
        },

        onDrag: function(e) {

            // Keep track of the direction of the drag for use during onDragOver
            var y = Event.getPageY(e);

            if (y < this.lastY) {
                this.goingUp = true;
            } else if (y > this.lastY) {
                this.goingUp = false;
            }

            this.lastY = y;
        },

        onDragOver: function(e, id) {
            var srcEl = this.getEl();
            var destEl = Dom.get(id);

            // We are only concerned with list items, we ignore the dragover
            // notifications for the list.
            if (destEl.nodeName == "DIV" && Dom.hasClass(destEl,"repeated-chunk")) {
                var p = destEl.parentNode;

                // if going up, insert above the target element
                p.insertBefore(srcEl, this.goingUp?destEl:destEl.nextSibling);

                DDM.refreshCache();
            }
        }
    });
})();

function loadScript(href) {
    var s = document.createElement("script");
    s.setAttribute("src",href);
    document.getElementsByTagName("HEAD")[0].appendChild(s);
}

var downloadService = {
    continuations: {},

    download : function(id,url,info, postBack,completionHandler) {
        this.continuations[id] = {postBack:postBack,completionHandler:completionHandler};
        loadScript(url+"?"+Hash.toQueryString(info));
    },

    post : function(id,data) {
        var o = this.continuations[id];
        new Ajax.Request(o.postBack, {
            parameters:{json:Object.toJSON(data)},
            onSuccess: function() {
                if(o.completionHandler!=null)
                    o.completionHandler();
            }
        });
    }
};

// update center service. for historical reasons,
// this is separate from downloadSerivce
var updateCenter = {
    postBackURL : null,
    info: {},
    completionHandler: null,
    url: "https://hudson.dev.java.net/",

    checkUpdates : function() {
        loadScript(updateCenter.url+"update-center.json?"+Hash.toQueryString(updateCenter.info));
    },

    post : function(data) {
        new Ajax.Request(updateCenter.postBackURL, {
            parameters:{json:Object.toJSON(data)},
            onSuccess: function() {
                if(updateCenter.completionHandler!=null)
                    updateCenter.completionHandler();
            }
        });
    }
};

/*
redirects to a page once the page is ready.

    @param url
        Specifies the URL to redirect the user.
*/
function applySafeRedirector(url) {
    var i=0;
    new PeriodicalExecuter(function() {
      i = (i+1)%4;
      var s = "";
      for( var j=0; j<i; j++ )
        s+='.';
      $('progress').innerHTML = s;
    },1);

    window.setTimeout(function() {
      var statusChecker = arguments.callee;
        new Ajax.Request(url, {
            method: "get",
            onFailure: function(rsp) {
                if(rsp.status==503) {
                  // redirect as long as we are still loading
                  window.setTimeout(statusChecker,5000);
                } else {
                  window.location.replace(url);
                }
            },
            onSuccess: function(rsp) {
                if(rsp.status!=200) {
                    // if connection fails, somehow Prototype thinks it's a success
                    window.setTimeout(statusChecker,5000);
                } else {
                    window.location.replace(url);
                }
            }
        });
    }, 5000);
}

// logic behind <f:validateButton />
function validateButton(checkUrl,paramList,button) {
  button = button._button;

  var parameters = {};

  paramList.split(',').each(function(name) {
      var p = findPreviousFormItem(button,name);
      if(p!=null)
        parameters[name] = p.value;
  });

  var spinner = Element.up(button,"DIV").nextSibling;
  var target = spinner.nextSibling;
  spinner.style.display="block";

  new Ajax.Request(checkUrl, {
      parameters: parameters,
      onComplete: function(rsp) {
          spinner.style.display="none";
          target.innerHTML = rsp.responseText;
          var s = rsp.getResponseHeader("script");
          if(s!=null)
            try {
              eval(s);
            } catch(e) {
              window.alert("failed to evaluate "+s+"\n"+e.message);
            }
      }
  });
}

// create a combobox.
// @param id
//      ID of the <input type=text> element that becomes a combobox.
// @param valueFunction
//      Function that returns all the candidates as an array
function createComboBox(id,valueFunction) {
    var candidates = valueFunction();

    Behaviour.addLoadEvent(function() {
        var callback = function(value /*, comboBox*/) {
          var items = new Array();
          if (value.length > 0) { // if no value, we'll not provide anything
            value = value.toLowerCase();
            for (var i = 0; i<candidates.length; i++) {
              if (candidates[i].toLowerCase().indexOf(value) >= 0) {
                items.push(candidates[i]);
                if(items.length>20)
                  break; // 20 items in the list should be enough
              }
            }
          }
          return items; // equiv to: comboBox.setItems(items);
        };

        if (document.getElementById(id) != null) {
          new ComboBox(id,callback);
        }
    });
}
