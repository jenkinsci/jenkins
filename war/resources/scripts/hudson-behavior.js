// id generator
var iota = 0;

// Form check code
//========================================================
var Form = {
  // pending requests
  queue : [],

  inProgress : false,

  delayedCheck : function(checkUrl,targetElement) {
    this.queue.push({url:checkUrl,target:targetElement});
    this.schedule();
  },

  schedule : function() {
    if(this.inProgress)  return;
    if(this.queue.length==0) return;

    next = this.queue.shift();
    this.inProgress = true;

    new Ajax.Request(next.url, {
        method : 'get',
        onComplete : function(x) {
          next.target.innerHTML = x.responseText;
          Form.inProgress = false;
          Form.schedule();
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

    Form.delayedCheck(e.targetUrl(),e.targetElement);

    e.onchange = function() {
      new Ajax.Request(this.targetUrl(), {
          method : 'get',
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
    field.parentNode.parentNode.parentNode.parentNode.innerHTML =
        "<textarea rows=8 class='setting-input' name='"+field.name+"'>"+value+"</textarea>";
}
