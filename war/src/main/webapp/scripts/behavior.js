/*
   Behaviour v1.1 by Ben Nolan, June 2005. Based largely on the work
   of Simon Willison (see comments by Simon below).

   Description:

   	Uses css selectors to apply javascript behaviours to enable
   	unobtrusive javascript in html documents.

   Usage:

        Behaviour.specify('b.someclass', 'myrules.alert', 10, function(element) {
	    element.onclick = function() {
                alert(this.innerHTML);
            }
        });
        Behaviour.specify('#someid u', 'myrules.blah', 0, function(element) {
	    element.onmouseover = function() {
                this.innerHTML = "BLAH!";
            }
        });

	// Call Behaviour.apply() to re-apply the rules (if you
	// update the dom, etc).

   License:

   	This file is entirely BSD licensed.

   More information:

   	http://ripcord.co.nz/behaviour/

*/

var storage = {};
var Behaviour = {

    /**
     * @param {String} selector a CSS selector triggering your behavior
     * @param {String} id uniquely identifies this behavior among all behaviors for the selector; prevents duplicate registrations
     * @param {Number} priority relative position of this behavior in case multiple apply; lower numbers applied first; alphabetical by id in case of tie; choose 0 if you do not care
     * @param {Function} behavior callback function taking one parameter, a (DOM) {@link Element}, and returning void
     */
    specify : function(selector, id, priority, behavior) {
        var forSelector = storage[selector];
        if (forSelector == null) {
            storage[selector] = forSelector = [];
        }
        for (var i = 0; i < forSelector.length; i++) {
            if (forSelector[i].id == id) {
                forSelector.splice(i, 1);
                break;
            }
        }
        forSelector.push({id: id, priority: priority, behavior: behavior});
        forSelector.sort(function(a, b) {
            var location = a.priority - b.priority;
            return location != 0 ? location : a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
        });
    },

        /** @deprecated For backward compatibility only; use {@link specify} instead. */
	list : new Array,

        /** @deprecated For backward compatibility only; use {@link specify} instead. */
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
        if (!(startNode instanceof Array)) {
            startNode = [startNode];
        }
        // First, handle deprecated registrations:
        Behaviour.list._each(function(sheet) {
            for (var selector in sheet){
                startNode._each(function (n) {
                    var list = findElementsBySelector(n,selector,includeSelf);
                    if (list.length>0)  // just to simplify setting of a breakpoint.
                        list._each(sheet[selector]);
                });
            }
        });
        // Now those using specify:
        for (var selector in storage) {
            storage[selector]._each(function (registration) {
                startNode._each(function (node) {
                    var list = findElementsBySelector(node, selector, includeSelf);
                    if (list.length > 0) {
                        list._each(registration.behavior);
                    }
                });
            });
        }
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
