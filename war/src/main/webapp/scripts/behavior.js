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

    /**
     * Applies behaviour rules to a subtree/subforest.
     *
     * @param {HTMLElement|HTMLElement[]} startNode
     *      Subtree/forest to apply rules.
     *
     *      Within a single subtree, rules are the outer loop and the nodes in the tree are the inner loop,
     *      and sometimes the behaviour rules rely on this ordering to work correctly. When you pass a forest,
     *      this semantics is preserved.
     */
    applySubtree : function(startNode,includeSelf) {
        var behaviorsBySelector = {};
        Behaviour.list._each(function(sheet) {
            for (var selector in sheet){
                var behavior = sheet[selector];
                var behaviors = behaviorsBySelector[selector];
                if (behaviors == null) {
                    behaviors = [];
                    behaviorsBySelector[selector] = behaviors;
                }
                if (behaviors.indexOf(behavior.toString()) == -1) {
                    behaviors.push(behavior.toString());
                    function apply(n) {
                        var list = findElementsBySelector(n,selector,includeSelf);
                        if (list.length>0)  // just to simplify setting of a breakpoint.
                            list._each(sheet[selector]);
                    }
                    if (startNode instanceof Array) {
                        startNode._each(apply)
                    } else {
                        apply(startNode);
                    }
                }
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

function findElementsBySelector(startNode,selector,includeSelf) {
    if(includeSelf) {
        function isSelfOrChild(c) {
          while(true) {
              if(startNode == c) return true;
              if(c == null) return false;
              c = c.parentNode;
          }
        }
        return Prototype.Selector.select(selector, startNode.parentNode).filter(isSelfOrChild);
    } else {
        return Prototype.Selector.select(selector, startNode);
    }
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
