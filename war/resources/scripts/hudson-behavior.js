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
          }
        }
    );
    return false;
}