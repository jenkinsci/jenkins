/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Yahoo! Inc., Alan Harder, InfraDNA, Inc.
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
        if (crumbField=="") return; // layout.jelly passes in "" whereas it means null.
        this.fieldName = crumbField;
        this.value = crumbValue;
    },

    /**
     * Adds the crumb value into the given hash or array and returns it.
     */
    wrap: function(headers) {
        if (this.fieldName!=null) {
            if (headers instanceof Array)
                headers.push(this.fieldName, this.value);
            else
                headers[this.fieldName]=this.value;
        }
        return headers;
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
                var i;
                next.target.innerHTML = x.status==200 ? x.responseText
                    : '<a href="" onclick="document.getElementById(\'valerr' + (i=iota++)
                    + '\').style.display=\'block\';return false">ERROR</a><div id="valerr'
                    + i + '" style="display:none">' + x.responseText + '</div>';
                Behaviour.applySubtree(next.target);
                FormChecker.inProgress--;
                FormChecker.schedule();
            }
        });
        this.inProgress++;
    }
}

/**
 * Find the sibling (in the sense of the structured form submission) form item of the given name,
 * and returns that DOM node.
 *
 * @param {HTMLElement} e
 * @param {string} name
 *      Name of the control to find. Can include "../../" etc in the prefix.
 *      See @RelativePath.
 */
function findNearBy(e,name) {
    while (name.startsWith("../")) {
        name = name.substring(3);
        e = findFormParent(e,null,true);
    }

    // does 'e' itself match the criteria?
    // as some plugins use the field name as a parameter value, instead of 'value'
    var p = findFormItem(e,name,function(e,filter) {
        if (filter(e))    return e;
        return null;
    });
    if (p!=null)    return p;

    var owner = findFormParent(e,null,true);

    p = findPreviousFormItem(e,name);
    if (p!=null && findFormParent(p,null,true)==owner)
        return p;

    var n = findNextFormItem(e,name);
    if (n!=null && findFormParent(n,null,true)==owner)
        return n;

    return null; // not found
}

function controlValue(e) {
    if (e==null)    return null;
    // compute the form validation value to be sent to the server
    var type = e.getAttribute("type");
    if(type!=null && type.toLowerCase()=="checkbox")
        return e.checked;
    return e.value;
}

function toValue(e) {
    return encodeURIComponent(controlValue(e));
}

/**
 * Builds a query string in a fluent API pattern.
 * @param {HTMLElement} owner
 *      The 'this' control.
 */
function qs(owner) {
    return {
        params : "",

        append : function(s) {
            if (this.params.length==0)  this.params+='?';
            else                        this.params+='&';
            this.params += s;
            return this;
        },

        nearBy : function(name) {
            var e = findNearBy(owner,name);
            if (e==null)    return this;    // skip
            return this.append(Path.tail(name)+'='+toValue(e));
        },

        addThis : function() {
            return this.append("value="+toValue(owner));
        },

        toString : function() {
            return this.params;
        }
    };
}

// find the nearest ancestor node that has the given tag name
function findAncestor(e, tagName) {
    do {
        e = e.parentNode;
    } while (e != null && e.tagName != tagName);
    return e;
}

function findAncestorClass(e, cssClass) {
    do {
        e = e.parentNode;
    } while (e != null && !Element.hasClassName(e,cssClass));
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
    } while (tr != null && (tr.tagName != "TR" || tr.className != className));

    return tr;
}

function find(src,filter,traversalF) {
    while(src!=null) {
        src = traversalF(src);
        if(src!=null && filter(src))
            return src;
    }
    return null;
}

/**
 * Traverses a form in the reverse document order starting from the given element (but excluding it),
 * until the given filter matches, or run out of an element.
 */
function findPrevious(src,filter) {
    return find(src,filter,function (e) {
        var p = e.previousSibling;
        if(p==null) return e.parentNode;
        while(p.lastChild!=null)
            p = p.lastChild;
        return p;
    });
}

function findNext(src,filter) {
    return find(src,filter,function (e) {
        var n = e.nextSibling;
        if(n==null) return e.parentNode;
        while(n.firstChild!=null)
            n = n.firstChild;
        return n;
    });
}

function findFormItem(src,name,directionF) {
    var name2 = "_."+name; // handles <textbox field="..." /> notation silently
    return directionF(src,function(e){ return (e.tagName=="INPUT" || e.tagName=="TEXTAREA" || e.tagName=="SELECT") && (e.name==name || e.name==name2); });
}

/**
 * Traverses a form in the reverse document order and finds an INPUT element that matches the given name.
 */
function findPreviousFormItem(src,name) {
    return findFormItem(src,name,findPrevious);
}

function findNextFormItem(src,name) {
    return findFormItem(src,name,findNext);
}

/**
 * Parse HTML into DOM.
 */
function parseHtml(html) {
    var c = document.createElement("div");
    c.innerHTML = html;
    return c.firstChild;
}

/**
 * Emulate the firing of an event.
 *
 * @param {HTMLElement} element
 *      The element that will fire the event
 * @param {String} event
 *      like 'change', 'blur', etc.
 */
function fireEvent(element,event){
    if (document.createEvent) {
        // dispatch for firefox + others
        var evt = document.createEvent("HTMLEvents");
        evt.initEvent(event, true, true ); // event type,bubbling,cancelable
        return !element.dispatchEvent(evt);
    } else {
        // dispatch for IE
        var evt = document.createEventObject();
        return element.fireEvent('on'+event,evt)
    }
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

    var url = e.targetUrl();
    try {
      FormChecker.delayedCheck(url, method, e.targetElement);
    } catch (x) {
        // this happens if the checkUrl refers to a non-existing element.
        // don't let this kill off the entire JavaScript
        YAHOO.log("Failed to register validation method: "+e.getAttribute("checkUrl")+" : "+e);
        return;
    }

    var checker = function() {
        var target = this.targetElement;
        FormChecker.sendRequest(this.targetUrl(), {
            method : method,
            onComplete : function(x) {
                target.innerHTML = x.responseText;
                Behaviour.applySubtree(target);
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

function registerRegexpValidator(e,regexp,message) {
    e.targetElement = findFollowingTR(e, "validation-error-area").firstChild.nextSibling;
    var checkMessage = e.getAttribute('checkMessage');
    if (checkMessage) message = checkMessage;
    var oldOnchange = e.onchange;
    e.onchange = function() {
        var set = oldOnchange != null ? oldOnchange.call(this) : false;
        if (this.value.match(regexp)) {
            if (!set) this.targetElement.innerHTML = "";
        } else {
            this.targetElement.innerHTML = "<div class=error>" + message + "</div>";
            set = true;
        }
        return set;
    }
    e.onchange.call(e);
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
        var btns = findElementsBySelector(e,"INPUT.hetero-list-add"),
            btn = btns[btns.length-1]; // In case nested content also uses hetero-list
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

        var withDragDrop = initContainerDD(e);

        var menuButton = new YAHOO.widget.Button(btn, { type: "menu", menu: menu });
        menuButton.getMenu().clickEvent.subscribe(function(type,args,value) {
            var t = templates[parseInt(args[1].value)]; // where this args[1] comes is a real mystery

            var nc = document.createElement("div");
            nc.className = "repeated-chunk";
            nc.setAttribute("name",t.name);
            nc.innerHTML = t.html;
            insertionPoint.parentNode.insertBefore(nc, insertionPoint);
            if(withDragDrop)    prepareDD(nc);

            hudsonRules['DIV.repeated-chunk'](nc);  // applySubtree doesn't get nc itself
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
                if(nameRef!=null && row.getAttribute("nameref")==null)
                    row.setAttribute("nameref",nameRef); // to handle inner rowSets, don't override existing values
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
            var e = this.previousSibling;
            while (e!=null) {
                if (e.tagName=="INPUT") {
                    e.click();
                    break;
                }
                e = e.previousSibling;
            }
        }
        e = null;
    },

// form fields that are validated via AJAX call to the server
// elements with this class should have two attributes 'checkUrl' that evaluates to the server URL.
    "INPUT.validated" : registerValidator,
    "SELECT.validated" : registerValidator,
    "TEXTAREA.validated" : registerValidator,

// validate required form values
    "INPUT.required" : function(e) { registerRegexpValidator(e,/./,"Field is required"); },

// validate form values to be a number
    "INPUT.number" : function(e) { registerRegexpValidator(e,/^(\d+|)$/,"Not a number"); },
    "INPUT.positive-number" : function(e) {
        registerRegexpValidator(e,/^(\d*[1-9]\d*|)$/,"Not a positive number");
    },

    "INPUT.auto-complete": function(e) {// form field with auto-completion support 
        // insert the auto-completion container
        var div = document.createElement("DIV");
        e.parentNode.insertBefore(div,e.nextSibling);
        e.style.position = "relative"; // or else by default it's absolutely positioned, making "width:100%" break

        var ds = new YAHOO.widget.DS_XHR(e.getAttribute("autoCompleteUrl"),["suggestions","name"]);
        ds.scriptQueryParam = "value";
        
        // Instantiate the AutoComplete
        var ac = new YAHOO.widget.AutoComplete(e, div, ds);
        ac.prehighlightClassName = "yui-ac-prehighlight";
        ac.animSpeed = 0;
        ac.useShadow = true;
        ac.autoSnapContainer = true;
        ac.delimChar = e.getAttribute("autoCompleteDelimiChar");
        ac.doBeforeExpandContainer = function(textbox,container) {// adjust the width every time we show it
            container.style.width=textbox.clientWidth+"px";
            var Dom = YAHOO.util.Dom;
            Dom.setXY(container, [Dom.getX(textbox), Dom.getY(textbox) + textbox.offsetHeight] );
            return true;
        }
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
                    onSuccess : function(x) {
                        div.innerHTML = x.responseText;
                    },
                    onFailure : function(x) {
                        div.innerHTML = "<b>ERROR</b>: Failed to load help file: " + x.statusText;
                    }
                });
            } else {
                div.style.display = "none";
            }

            return false;
        };
        e.tabIndex = 9999; // make help link unnavigable from keyboard
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
        if(Element.hasClassName(form, "no-json"))
            return;
        // add the hidden 'json' input field, which receives the form structure in JSON
        var div = document.createElement("div");
        div.innerHTML = "<input type=hidden name=json value=init>";
        form.appendChild(div);

        var oldOnsubmit = form.onsubmit;
        if (typeof oldOnsubmit == "function") {
            form.onsubmit = function() { return buildFormTree(this) && oldOnsubmit.call(this); }
        } else {
            form.onsubmit = function() { return buildFormTree(this); };
        }

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

    "TR.optional-block-start ": function(e) { // see optionalBlock.jelly
        // this is suffixed by a pointless string so that two processing for optional-block-start
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
    },

    // radio buttons in repeatable content
    "DIV.repeated-chunk" : function(d) {
        var inputs = d.getElementsByTagName('INPUT');
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].type == 'radio') {
                // Need to uniquify each set of radio buttons in repeatable content.
                // buildFormTree will remove the prefix before form submission.
                var prefix = d.getAttribute('radioPrefix');
                if (!prefix) {
                    prefix = 'removeme' + (iota++) + '_';
                    d.setAttribute('radioPrefix', prefix);
                }
                inputs[i].name = prefix + inputs[i].name;
                // Reselect anything unselected by browser before names uniquified:
                if (inputs[i].defaultChecked) inputs[i].checked = true;
            }
        }
    },

    // radioBlock.jelly
    "INPUT.radio-block-control" : function(r) {
        r.id = "radio-block-"+(iota++);

        // when one radio button is clicked, we need to update foldable block for
        // other radio buttons with the same name. To do this, group all the
        // radio buttons with the same name together and hang it under the form object
        var f = r.form;
        var radios = f.radios;
        if (radios == null)
            f.radios = radios = {};

        var g = radios[r.name];
        if (g == null) {
            radios[r.name] = g = object(radioBlockSupport);
            g.buttons = [];
        }

        var s = findAncestorClass(r,"radio-block-start");

        // find the end node
        var e = (function() {
            var e = s;
            var cnt=1;
            while(cnt>0) {
                e = e.nextSibling;
                if (Element.hasClassName(e,"radio-block-start"))
                    cnt++;
                if (Element.hasClassName(e,"radio-block-end"))
                    cnt--;
            }
            return e;
        })();

        var u = function() {
            g.updateSingleButton(r,s,e);
        };
        applyNameRef(s,e,r.id);
        g.buttons.push(u);

        // apply the initial visibility
        u();

        // install event handlers to update visibility.
        // needs to use onclick and onchange for Safari compatibility
        r.onclick = r.onchange = function() { g.updateButtons(); };
    },

    // editableComboBox.jelly
    "INPUT.combobox" : function(c) {
        // Next element after <input class="combobox"/> should be <div class="combobox-values">
        var vdiv = c.nextSibling;
        if (Element.hasClassName(vdiv, "combobox-values")) {
            createComboBox(c, function() {
                var values = [];
                for (var value = vdiv.firstChild; value; value = value.nextSibling)
                    values.push(value.getAttribute('value'));
                return values;
            });
        }
    },

    // dropdownList.jelly
    "SELECT.dropdownList" : function(e) {
        if(isInsideRemovable(e))    return;

        e.subForms = [];
        var start = findFollowingTR(e, 'dropdownList-container').firstChild.nextSibling, end;
        do { start = start.firstChild; } while (start && start.tagName != 'TR');
        if (start && start.className != 'dropdownList-start')
            start = findFollowingTR(start, 'dropdownList-start');
        while (start != null) {
            end = findFollowingTR(start, 'dropdownList-end');
            e.subForms.push({ 'start': start, 'end': end });
            start = findFollowingTR(end, 'dropdownList-start');
        }

        updateDropDownList(e);
    },

    // select.jelly
    "SELECT.select" : function(e) {
        // controls that this SELECT box depends on
        refillOnChange(e,function(params) {
            var value = e.value;
            updateListBox(e,e.getAttribute("fillUrl"),{
                parameters: params,
                onSuccess: function() {
                    if (value=="") {
                        // reflect the initial value. if the control depends on several other SELECT.select,
                        // it may take several updates before we get the right items, which is why all these precautions.
                        var v = e.getAttribute("value");
                        if (v) {
                            e.value = v;
                            if (e.value==v) e.removeAttribute("value"); // we were able to apply our initial value
                        }
                    }

                    // if the update changed the current selection, others listening to this control needs to be notified.
                    if (e.value!=value) fireEvent(e,"change");
                }
            });
        });
    },

    // combobox.jelly
    "INPUT.combobox2" : function(e) {
        var items = [];

        var c = new ComboBox(e,function(value) {
            var candidates = [];
            for (var i=0; i<items.length; i++) {
                if (items[i].indexOf(value)==0) {
                    candidates.push(items[i]);
                    if (candidates.length>20)   break;
                }
            } 
            return candidates;
        }, {});

        refillOnChange(e,function(params) {
            new Ajax.Request(e.getAttribute("fillUrl"),{
                parameters: params,
                onSuccess : function(rsp) {
                    items = eval('('+rsp.responseText+')');
                }
            });
        });
    },

    "A.showDetails" : function(e) {
        e.onclick = function() {
            this.style.display = 'none';
            this.nextSibling.style.display = 'block';
            return false;
        };
        e = null; // avoid memory leak
    },

    "DIV.behavior-loading" : function(e) {
        e.style.display = 'none';
    },

    ".button-with-dropdown" : function (e) {
        new YAHOO.widget.Button(e, { type: "menu", menu: e.nextSibling });
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

var Path = {
  tail : function(p) {
      var idx = p.lastIndexOf("/");
      if (idx<0)    return p;
      return p.substring(idx+1);
  }
};

/**
 * Install change handlers based on the 'fillDependsOn' attribute.
 */
function refillOnChange(e,onChange) {
    var deps = [];

    function h() {
        var params = {};
        deps.each(function (d) {
            params[d.name] = controlValue(d.control);
        });
        onChange(params);
    }
    var v = e.getAttribute("fillDependsOn");
    if (v!=null) {
        v.split(" ").each(function (name) {
            var c = findNearBy(e,name);
            if (c==null) {
                if (window.console!=null)  console.warn("Unable to find nearby "+name);
                if (window.YUI!=null)      YUI.log("Unable to find a nearby control of the name "+name,"warn")
                return;
            }
            try { c.addEventListener("change",h,false); } catch (ex) { c.attachEvent("change",h); }
            deps.push({name:Path.tail(name),control:c});
        });
    }
    h();   // initial fill
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
    @deprecated Use standard javascript method "encodeURIComponent" instead
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
        var tr = f.start;
        while (true) {
            tr.style.display = (show ? "" : "none");
            if(show)
                tr.removeAttribute("field-disabled");
            else    // buildFormData uses this attribute and ignores the contents
                tr.setAttribute("field-disabled","true");
            if (tr == f.end) break;
            tr = tr.nextSibling;
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

    withDragDrop: false,

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
        this.withDragDrop = initContainerDD(container);
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
        if (this.withDragDrop) prepareDD(nc);

        hudsonRules['DIV.repeated-chunk'](nc);  // applySubtree doesn't get nc itself
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
function updateListBox(listBox,url,config) {
    config = config || {};
    config = object(config);
    var originalOnSuccess = config.onSuccess;
    config.onSuccess = function(rsp) {
        var l = $(listBox);
        var currentSelection = l.value;

        // clear the contents
        while(l.length>0)   l.options[0] = null;

        var selectionSet = false; // is the selection forced by the server?
        var possibleIndex = null; // if there's a new option that matches the current value, remember its index
        var opts = eval('('+rsp.responseText+')').values;
        for( var i=0; i<opts.length; i++ ) {
            l.options[i] = new Option(opts[i].name,opts[i].value);
            if(opts[i].selected) {
                l.selectedIndex = i;
                selectionSet = true;
            }
            if (opts[i].value==currentSelection)
                possibleIndex = i;
        }

        // if no value is explicitly selected by the server, try to select the same value
        if (!selectionSet && possibleIndex!=null)
            l.selectedIndex = possibleIndex;

        if (originalOnSuccess!=undefined)
            originalOnSuccess(rsp);
    },
    config.onFailure = function(rsp) {
        var l = $(listBox);
        l.options[0] = null;
    }

    new Ajax.Request(url, config);
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


/**
 * Finds the DOM node of the given DOM node that acts as a parent in the form submission.
 *
 * @param {HTMLElement} e
 *      The node whose parent we are looking for.
 * @param {HTMLFormElement} form
 *      The form element that owns 'e'. Passed in as a performance improvement. Can be null.
 * @return null
 *      if the given element shouldn't be a part of the final submission.
 */
function findFormParent(e,form,static) {
    static = static || false;

    if (form==null) // caller can pass in null to have this method compute the owning form
        form = findAncestor(e,"FORM");

    while(e!=form) {
        // this is used to create a group where no single containing parent node exists,
        // like <optionalBlock>
        var nameRef = e.getAttribute("nameRef");
        if(nameRef!=null)
            e = $(nameRef);
        else
            e = e.parentNode;

        if(!static && e.getAttribute("field-disabled")!=null)
            return null;  // this field shouldn't contribute to the final result

        var name = e.getAttribute("name");
        if(name!=null) {
            if(e.tagName=="INPUT" && !static && !xor(e.checked,Element.hasClassName(e,"negative")))
                return null;  // field is not active

            return e;
        }
    }

    return form;
}

// compute the form field name from the control name
function shortenName(name) {
    // [abc.def.ghi] -> abc.def.ghi
    if(name.startsWith('['))
        return name.substring(1,name.length-1);

    // abc.def.ghi -> ghi
    var idx = name.lastIndexOf('.');
    if(idx>=0)  name = name.substring(idx+1);
    return name;
}



//
// structured form submission handling
//   see http://wiki.hudson-ci.org/display/HUDSON/Structured+Form+Submission
function buildFormTree(form) {
    try {
        // I initially tried to use an associative array with DOM elemnets as keys
        // but that doesn't seem to work neither on IE nor Firefox.
        // so I switch back to adding a dynamic property on DOM.
        form.formDom = {}; // root object

        var doms = []; // DOMs that we added 'formDom' for.
        doms.push(form);

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
            var p = findFormParent(e,form);
            if (p==null)    return {};

            var m = p.formDom;
            if(m==null) {
                // this is a new grouping node
                doms.push(p);
                p.formDom = m = {};
                addProperty(findParent(p), p.getAttribute("name"), m);
            }
            return m;
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
                if(!e.groupingNode) {
                    v = e.getAttribute("json");
                    if (v) {
                        // if the special attribute is present, we'll either set the value or not. useful for an array of checkboxes
                        // we can't use @value because IE6 sets the value to be "on" if it's left unspecified.
                        if (checked)
                            addProperty(p, e.name, v);
                    } else {// otherwise it'll bind to boolean
                        addProperty(p, e.name, checked);
                    }
                } else {
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
                while (e.name.substring(0,8)=='removeme')
                    e.name = e.name.substring(e.name.indexOf('_',8)+1);
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

        return true;
    } catch(e) {
        alert(e+'\n(form not submitted)');
        return false;
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
    Drag&Drop implementation for heterogeneous/repeatable lists.
 */
function initContainerDD(e) {
    if (!Element.hasClassName(e,"with-drag-drop")) return false;

    for (e=e.firstChild; e!=null; e=e.nextSibling) {
        if (Element.hasClassName(e,"repeated-chunk"))
            prepareDD(e);
    }
    return true;
}
function prepareDD(e) {
    var h = e;
    // locate a handle
    while (h!=null && !Element.hasClassName(h,"dd-handle"))
        h = h.firstChild ? h.firstChild : h.nextSibling;
    if (h!=null) {
        var dd = new DragDrop(e);
        dd.setHandleElId(h);
    }
}

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
            if (destEl.nodeName == "DIV" && Dom.hasClass(destEl,"repeated-chunk")
                    // Nested lists.. ensure we don't drag out of this list or into a nested one:
                    && destEl.parentNode==srcEl.parentNode) {
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
        if (data==undefined) {
            // default to id in data
            data = id;
            id = data.id;
        }
        var o = this.continuations[id];
        // send the payload back in the body. We used to send this in as a form submission, but that hits the form size check in Jetty.
        new Ajax.Request(o.postBack, {
            contentType:"application/json",
            encoding:"UTF-8",
            postBody:Object.toJSON(data),
            onSuccess: function() {
                if(o.completionHandler!=null)
                    o.completionHandler();
                else if(downloadService.completionHandler!=null)
                    downloadService.completionHandler();
            }
        });
    }
};

// update center service. to remain compatible with earlier version of Hudson, aliased.
var updateCenter = downloadService;

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
      var j=0;
      for( j=0; j<i; j++ )
        s+='.';
      // put the rest of dots as hidden so that the layout doesn't change
      // depending on the # of dots.
      s+="<span style='visibility:hidden'>";
      for( ; j<4; j++ )
        s+='.';
      s+="</span>";
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
      if(p!=null) {
        if(p.type=="checkbox")  parameters[name] = p.checked;
        else                    parameters[name] = p.value;
      }
  });

  var spinner = Element.up(button,"DIV").nextSibling;
  var target = spinner.nextSibling;
  spinner.style.display="block";

  new Ajax.Request(checkUrl, {
      parameters: parameters,
      onComplete: function(rsp) {
          spinner.style.display="none";
          var i;
          target.innerHTML = rsp.status==200 ? rsp.responseText
                : '<a href="" onclick="document.getElementById(\'valerr' + (i=iota++)
                + '\').style.display=\'block\';return false">ERROR</a><div id="valerr'
                + i + '" style="display:none">' + rsp.responseText + '</div>';
          Behaviour.applySubtree(target);
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
// @param idOrField
//      ID of the <input type=text> element that becomes a combobox, or the field itself.
//      Passing an ID is @deprecated since 1.350; use <input class="combobox"/> instead.
// @param valueFunction
//      Function that returns all the candidates as an array
function createComboBox(idOrField,valueFunction) {
    var candidates = valueFunction();
    var creator = function() {
        if (typeof idOrField == "string")
          idOrField = document.getElementById(idOrField);
        if (!idOrField) return;
        new ComboBox(idOrField, function(value /*, comboBox*/) {
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
        });
    };
    // If an ID given, create when page has loaded (backward compatibility); otherwise now.
    if (typeof idOrField == "string") Behaviour.addLoadEvent(creator); else creator();
}


if (isRunAsTest) {
    // during the unit test, make Ajax errors fatal
    Ajax.Request.prototype.dispatchException = function(e) {
        throw e;
    }
}
