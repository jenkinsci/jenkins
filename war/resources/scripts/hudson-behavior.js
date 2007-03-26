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

  delayedCheck : function(url,method,target) {
    this.queue.push({url:url, method:method, target:target});
    this.schedule();
  },

  sendRequest : function(url,params) {
    if(params.method=="post") {
      var idx = url.indexOf('?');
      params.parameters = url.substring(idx+1);
      url = url.substring(0,idx);
    }
    new Ajax.Request(url,params);
  },

  schedule : function() {
    if(this.inProgress)  return;
    if(this.queue.length==0) return;

    var next = this.queue.shift();
    this.inProgress = true;

    this.sendRequest(next.url, {
        method : next.method,
        onComplete : function(x) {
          next.target.innerHTML = x.responseText;
          FormChecker.inProgress = false;
          FormChecker.schedule();
        }
      }
    );
  }
}

function findFollowingTR(input,className) {
  // identify the parent TR
  var tr = input;
  while(tr.tagName!="TR")
    tr = tr.parentNode;

  // then next TR that matches the CSS
  do {
    tr = tr.nextSibling;
  } while(tr.tagName!="TR" || tr.className!=className);

  return tr;
}




// Behavior rules
//========================================================

var hudsonRules = {
  ".advancedButton" : function(e) {
    e.onclick = function() {
      var link = this.parentNode;
      link.style.display = "none"; // hide the button

      var container = link.nextSibling.firstChild; // TABLE -> TBODY

      var tr = link;
      while(tr.tagName!="TR")
        tr = tr.parentNode;

      // move the contents of the advanced portion into the main table
      while(container.lastChild!=null) {
        tr.parentNode.insertBefore(container.lastChild,tr.nextSibling);
      }
    }
  },

  ".pseudoLink" : function(e) {
    e.onmouseover = function() {
      this.style.textDecoration="underline";
    }
    e.onmouseout = function() {
      this.style.textDecoration="none";
    }
  },

  // form fields that are validated via AJAX call to the server
  // elements with this class should have two attributes 'checkUrl' that evaluates to the server URL.
  ".validated" : function(e) {
    e.targetElement = findFollowingTR(e,"validation-error-area").firstChild.nextSibling;
    e.targetUrl = function() {return eval(this.getAttribute("checkUrl"));};
    var method = e.getAttribute("checkMethod");
    if(!method) method="get";

    FormChecker.delayedCheck(e.targetUrl(), method, e.targetElement);

    e.onchange = function() {
      FormChecker.sendRequest(this.targetUrl(), {
          method : method,
          onComplete : function(x) {e.targetElement.innerHTML = x.responseText;}
        }
      );
    }
  },

  // validate form values to be a number
  "input.number" : function(e) {
    e.targetElement = findFollowingTR(e,"validation-error-area").firstChild.nextSibling;
    e.onchange = function() {
      if(this.value.match(/^\d+$/)) {
        this.targetElement.innerHTML="";
      } else {
        this.targetElement.innerHTML="<div class=error>Not a number</div>";
      }
    }
  },

  ".help-button" : function(e) {
    e.onclick = function() {
      tr = findFollowingTR(this,"help-area");
      div = tr.firstChild.nextSibling.firstChild;

      if(div.style.display!="block") {
        div.style.display="block";
        // make it visible
        new Ajax.Request(
            this.getAttribute("helpURL"),
            {
              method : 'get',
              onComplete : function(x) {
                div.innerHTML = x.responseText;
              }
            }
        );
      } else {
        div.style.display = "none";
      }

      return false;
    }
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
              var id = "map"+(iota++);
              div.firstChild.setAttribute("name",id);
              e.setAttribute("usemap","#"+id);
            }
          }
      );
  },


    // button to add a new repeatable block
    "INPUT.repeatable-add" : function(e) {
        e.onclick = function() {
            repetableSupport.onAdd(this);
        };
    },

    "INPUT.repeatable-delete" : function(e) {
        e.onclick = function() {
            repetableSupport.onDelete(this);
        };
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

                p.insertBefore(div.firstChild, next);
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

// create a tooltip that applies to the element of the specified ID.
// @param text
//    HTML text of the tooltip
function makeTooltip(id,text) {
    new YAHOO.widget.Tooltip("tooltip-"+id, {
      context:id,
      text:text,
      showDelay:500 } );
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