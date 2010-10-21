/*
   Behaviour v1.1 by Ben Nolan, June 2005. Based largely on the work
   of Simon Willison (see comments by Simon below).

   Description:

   	Uses css selectors to apply javascript behaviours to enable
   	unobtrusive javascript in html documents.

   Usage:

	var myrules = {
		'b.someclass' : function(element){
			element.onclick = function(){
				alert(this.innerHTML);
			}
		},
		'#someid u' : function(element){
			element.onmouseover = function(){
				this.innerHTML = "BLAH!";
			}
		}
	};

	Behaviour.register(myrules);

	// Call Behaviour.apply() to re-apply the rules (if you
	// update the dom, etc).

   License:

   	This file is entirely BSD licensed.

   More information:

   	http://ripcord.co.nz/behaviour/

*/

var Behaviour = {
	list : new Array,

	register : function(sheet){
		Behaviour.list.push(sheet);
	},

	start : function(){
		Behaviour.addLoadEvent(function(){
			Behaviour.apply();
		});
	},

	apply : function(){
        this.applySubtree(document);
    },

    applySubtree : function(startNode) {
        Behaviour.list._each(function(sheet) {
            for (var selector in sheet){
                var list = findElementsBySelector(startNode,selector);
                list._each(sheet[selector]);
            }
        });
    },

    addLoadEvent : function(func){
		var oldonload = window.onload;

		if (typeof window.onload != 'function') {
			window.onload = func;
		} else {
			window.onload = function() {
				oldonload();
				func();
			}
		}
	}
}

Behaviour.start();

/*
   The following code is Copyright (C) Simon Willison 2004.

   document.getElementsBySelector(selector)
   - returns an array of element objects from the current document
     matching the CSS selector. Selectors can contain element names,
     class names and ids and can be nested. For example:

       elements = document.getElementsBySelect('div#main p a.external')

     Will return an array of all 'a' elements with 'external' in their
     class attribute that are contained inside 'p' elements that are
     contained inside the 'div' element which has id="main"

   New in version 0.4: Support for CSS2 and CSS3 attribute selectors:
   See http://www.w3.org/TR/css3-selectors/#attribute-selectors

   Version 0.4 - Simon Willison, March 25th 2003
   -- Works in Phoenix 0.5, Mozilla 1.3, Opera 7, Internet Explorer 6, Internet Explorer 5 on Windows
   -- Opera 7 fails
*/

function getAllChildren(e) {
  // Returns all children of element. Workaround required for IE5/Windows. Ugh.
  return e.all ? e.all : e.getElementsByTagName('*');
}

function isAncestor(p,c) {
  while(true) {
    if(p==c)      return true;
    if(c==null)   return false;
    c = c.parentNode;
  }
}

function findElementsBySelector(startNode,selector) {
  // Split selector in to tokens
  var tokens = selector.replace(/^\s+/,'').replace(/\s+$/,'').split(' ');
  var currentContext = new Array(startNode);
  for (var i = 0; i < tokens.length; i++) {
    var token = tokens[i].replace(/^\s+/,'').replace(/\s+$/,'');
    if (token.indexOf('#') > -1) {
      // Token is an ID selector
      var bits = token.split('#');
      var tagName = bits[0];
      var id = bits[1];
      var element = document.getElementById(id);
      if (tagName && element.nodeName.toLowerCase() != tagName) {
        // tag with that ID not found, return false
        return [];
      }

      // make sure this node is a descendant of the current context
      if(currentContext.find(function(n) {return isAncestor(n,element)})==null)
        return []; // not a descendant

      // Set currentContext to contain just this element
      currentContext = [element];
      continue; // Skip to next token
    }
    if (token.indexOf('.') > -1) {
      // Token contains a class selector
      var bits = token.split('.');
      var tagName = bits[0];
      var className = new RegExp('\\b'+bits[1]+'\\b');
      if (!tagName) {
        tagName = '*';
      }
      // Get elements matching tag, filter them for class selector
      var found = [];
      for (var h = 0; h < currentContext.length; h++) {
        var elements;
        if (tagName == '*') {
            elements = getAllChildren(currentContext[h]);
        } else {
            elements = currentContext[h].getElementsByTagName(tagName);
        }
        for (var j = 0; j < elements.length; j++) {
          found.push(elements[j]);
        }
      }

      currentContext = [];
      for (var k = 0; k < found.length; k++) {
        if (found[k].className && found[k].className.match(className)) {
          currentContext.push(found[k]);
        }
      }
      continue; // Skip to next token
    }
    // Code to deal with attribute selectors
    bits = /^(\w*)\[(\w+)([=~\|\^\$\*]?)=?"?([^\]"]*)"?\]$/.exec(token);
    if (bits!=null) {
      var tagName = bits[1];
      var attrName = bits[2];
      var attrOperator = bits[3];
      var attrValue = bits[4];
      if (!tagName) {
        tagName = '*';
      }
      // Grab all of the tagName elements within current context
      var found = new Array;
      var foundCount = 0;
      for (var h = 0; h < currentContext.length; h++) {
        var elements;
        if (tagName == '*') {
            elements = getAllChildren(currentContext[h]);
        } else {
            elements = currentContext[h].getElementsByTagName(tagName);
        }
        for (var j = 0; j < elements.length; j++) {
          found.push(elements[j]);
        }
      }

      var checkFunction; // This function will be used to filter the elements
      switch (attrOperator) {
        case '=': // Equality
          checkFunction = function(e) { return (e.getAttribute(attrName) == attrValue); };
          break;
        case '~': // Match one of space seperated words
          checkFunction = function(e) { return (e.getAttribute(attrName).match(new RegExp('\\b'+attrValue+'\\b'))); };
          break;
        case '|': // Match start with value followed by optional hyphen
          checkFunction = function(e) { return (e.getAttribute(attrName).match(new RegExp('^'+attrValue+'-?'))); };
          break;
        case '^': // Match starts with value
          checkFunction = function(e) { return (e.getAttribute(attrName).indexOf(attrValue) == 0); };
          break;
        case '$': // Match ends with value - fails with "Warning" in Opera 7
          checkFunction = function(e) { return (e.getAttribute(attrName).lastIndexOf(attrValue) == e.getAttribute(attrName).length - attrValue.length); };
          break;
        case '*': // Match ends with value
          checkFunction = function(e) { return (e.getAttribute(attrName).indexOf(attrValue) > -1); };
          break;
        default :
          // Just test for existence of attribute
          checkFunction = function(e) { return e.getAttribute(attrName); };
      }

      currentContext = found.findAll(checkFunction);
      // alert('Attribute Selector: '+tagName+' '+attrName+' '+attrOperator+' '+attrValue);
      continue; // Skip to next token
    }

    if (!currentContext[0]){
    	return [];
    }

    // If we get here, token is JUST an element (not a class or ID selector)
    tagName = token;
    var found = new Array;
    for (var h = 0; h < currentContext.length; h++) {
      var elements = currentContext[h].getElementsByTagName(tagName);
      for (var j = 0; j < elements.length; j++) {
        found.push(elements[j]);
      }
    }
    currentContext = found;
  }
  return currentContext;
}

document.getElementsBySelector = function(selector) {
    return findElementsBySelector(document,selector);
}

/* That revolting regular expression explained
/^(\w+)\[(\w+)([=~\|\^\$\*]?)=?"?([^\]"]*)"?\]$/
  \---/  \---/\-------------/    \-------/
    |      |         |               |
    |      |         |           The value
    |      |    ~,|,^,$,* or =
    |   Attribute
   Tag
*/
