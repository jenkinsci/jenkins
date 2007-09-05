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

// Form check code
//========================================================
var FormChecker = {
    // pending requests
    queue : [],

    inProgress : false,

    delayedCheck : function(url, method, target) {
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
        if (this.inProgress)  return;
        if (this.queue.length == 0) return;

        var next = this.queue.shift();
        this.inProgress = true;

        this.sendRequest(next.url, {
            method : next.method,
            onComplete : function(x) {
                next.target.innerHTML = x.responseText;
                FormChecker.inProgress = false;
                FormChecker.schedule();
            }
        });
    }
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

    e.onchange = e.onblur = function() {
        var target = this.targetElement;
        FormChecker.sendRequest(this.targetUrl(), {
            method : method,
            onComplete : function(x) {
                target.innerHTML = x.responseText;
            }
        });
    }
    e = null; // avoid memory leak
}

var hudsonRules = {
    "BODY" : function() {
        tooltip = new YAHOO.widget.Tooltip("tt", {context:[]});
    },

    "TABLE.sortable" : function(e) {// sortable table
        ts_makeSortable(e);
    },

    "INPUT.advancedButton" : function(e) {
        e.onclick = function() {
            var link = this.parentNode;
            link.style.display = "none"; // hide the button

            var container = link.nextSibling.firstChild; // TABLE -> TBODY

            var tr = link;
            while (tr.tagName != "TR")
                tr = tr.parentNode;

            // move the contents of the advanced portion into the main table
            while (container.lastChild != null)
                tr.parentNode.insertBefore(container.lastChild, tr.nextSibling);
        }
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
                new Ajax.Request(
                        this.getAttribute("helpURL"),
                    {
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
                onComplete : function(x) {
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
        e.onclick = function() {
            repetableSupport.onAdd(this);
        };
        e = null; // avoid memory leak
    },

    "INPUT.repeatable-delete" : function(e) {
        e.onclick = function() {
            repetableSupport.onDelete(this);
        };
        e = null; // avoid memory leak
    },

    "DIV.repeated-container" : function(e) {
        // compute the insertion point
        var ip = e.lastChild;
        while (!Element.hasClassName(ip, "repeatable-insertion-point"))
            ip = ip.previousSibling;
        // set up the logic
        object(repetableSupport).init(e, e.firstChild, ip);
    },

    // hook up tooltip
    "[tooltip]" : function(e) {
        // copied from YAHOO.widget.Tooltip.prototype.configContext to efficiently add a new element
        // event registration via YAHOO.util.Event.addListener leaks memory, so do it by ourselves here
        e.onmouseover = function() { return tooltip.onContextMouseOver.call(this,window.event,tooltip); }
        e.onmousemove = function() { return tooltip.onContextMouseMove.call(this,window.event,tooltip); }
        e.onmouseout  = function() { return tooltip.onContextMouseOut .call(this,window.event,tooltip); }
        e.title = e.getAttribute("tooltip");
        e = null; // avoid memory leak
    }
};

Behaviour.register(hudsonRules);


// used by editableDescription.jelly to replace the description field with a form
function replaceDescription() {
    var d = document.getElementById("description");
    d.firstChild.nextSibling.innerHTML = "<div class='spinner-right'>loading...</div>";
    new Ajax.Request(
        "./descriptionForm",
        {
          method : 'get',
          onComplete : function(x) {
            d.innerHTML = x.responseText;
            d.getElementsByTagName("TEXTAREA")[0].focus();
          }
        }
    );
    return false;
}

function applyNameRef(s,e,id) {
    $(id).groupingNode = true;
    // s contains the node itself
    for(var x=s.nextSibling; x!=e; x=x.nextSibling)
        x.setAttribute("nameRef",id);
}

function initOptionalBlock(sid, eid, cid) {
    applyNameRef($(sid),$(eid),cid);
    updateOptionalBlock(sid,eid,cid);
}

// used by optionalBlock.jelly to update the form status
//   @param sid     ID of the start marker
//   @param sid     ID of the end marker
//   @param cid     ID of the check box
function updateOptionalBlock(sid, eid, cid) {
    var tbl = document.getElementById(sid).parentNode;
    var i = false;
    var o = false;

    var checked = document.getElementById(cid).checked;

    for (var j = 0; tbl.rows[j]; j++) {
        var n = tbl.rows[j];

        if (n.id == eid)
            o = true;

        if (i && !o) {
            if (checked)
                n.style.display = "";
            else
                n.style.display = "none";
        }

        if (n.id == sid) {
            if (n.getAttribute('hasHelp') == 'true')
                j++;
            i = true;
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
    window.setTimeout(function() {
        new Ajax.Request(url, {
            method: "post",
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

                refreshPart(id,url);
            }
        });
    }, 5000);
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
    // alert('Yay! '+sel.value+' '+sel.selectedIndex);
    for (var i = 0; i < sel.forms.length; i++) {
        var show = sel.selectedIndex == i;
        var f = sel.forms[i];
        var td = f.start;
        while (true) {
            td.style.display = (show ? "" : "none");
            if (td == f.end) break;
            td = td.nextSibling;
        }
    }
}


// code for supporting repeatable.jelly
var repetableSupport = {
    // set by the inherited instance to the insertion point DIV
    insertionPoint: null,

    // HTML text of the repeated chunk
    blockHTML: null,

    // containing <div>.
    container: null,

    // do the initialization
    init : function(container,master,insertionPoint) {
        this.container = $(container);
        this.container.tag = this;
        master = $(master);
        this.blockHTML = master.innerHTML;
        master.parentNode.removeChild(master);
        this.insertionPoint = $(insertionPoint);
        this.update();
    },

    // insert one more block at the insertion position
    expand : function() {
        // importNode isn't supported in IE.
        // nc = document.importNode(node,true);
        var nc = document.createElement("div");
        nc.className = "repeated-chunk";
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

                if (n == blockStart)
                    i = true;
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


function updateBuildHistory(nBuild) {
    $('buildHistory').headers = ["n",nBuild];

    function updateBuilds() {
        var bh = $('buildHistory');
        new Ajax.Request("./ajaxBuildHistoryUpdate", {
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
        method: "post",
        onSuccess: function(rsp) {
            var l = $(listBox);
            while(l.length>0)   l.options[0] = null;

            var opts = eval('('+rsp.responseText+')').values;
            for( var i=0; i<opts.length; i++ ) {
                l.options[i] = new Option(opts[i].name,opts[i].value);
                if(opts[i].selected)
                    l.selectedIndex = i;
            }
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
    // I initially tried to use an associative array with DOM elemnets as keys
    // but that doesn't seem to work neither on IE nor Firefox.
    // so I switch back to adding a dynamic property on DOM.
    form.formDom = {}; // root object

    var doms = []; // DOMs that we added 'formDom' for.
    doms.push(form);

    function addProperty(parent,name,value) {
        // abc.def.ghi -> ghi
        var idx = name.lastIndexOf('.');
        if(idx>=0)  name = name.substring(idx+1);
        
        if(parent[name]!=null) {
            if(typeof parent[name]!="array")
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
            
            var name = e.getAttribute("name");
            if(name!=null) {
                if(e.tagName=="INPUT" && !e.checked)
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

    for( var i=0; i<form.elements.length; i++ ) {
        var e = form.elements[i];
        var p;
        var type = e.getAttribute("type");
        if(type==null)  type="";
        switch(type.toLowerCase()) {
        case "button":
        case "submit":
            break;
        case "checkbox":
            p = findParent(e);
            if(!e.groupingNode)
                addProperty(p, e.name, e.checked);
            else {
                if(e.checked)
                    addProperty(p, e.name, e.formDom = {});
            }
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

    alert(Object.toJSON(form.formDom));

    // clean up
    for( i=0; i<doms.length; i++ )
        doms[i].formDom = null;
    

    return false;
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
