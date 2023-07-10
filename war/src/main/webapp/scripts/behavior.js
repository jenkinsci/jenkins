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

var Behaviour = (function () {
  var storage = [{ selector: "", id: "_deprecated", priority: 0 }];
  return {
    /**
     * Specifies something to do when an element matching a CSS selector is encountered.
     * @param {String} selector a CSS selector triggering your behavior
     * @param {String} id combined with selector, uniquely identifies this behavior; prevents duplicate registrations
     * @param {Number} priority relative position of this behavior in case multiple apply to a given element; lower numbers applied first (sorted by id then selector in case of tie); choose 0 if you do not care
     * @param {Function} behavior callback function taking one parameter, a (DOM) {@link Element}, and returning void
     */
    specify: function (selector, id, priority, behavior) {
      for (var i = 0; i < storage.length; i++) {
        if (storage[i].selector == selector && storage[i].id == id) {
          storage.splice(i, 1);
          break;
        }
      }
      storage.push({
        selector: selector,
        id: id,
        priority: priority,
        behavior: behavior,
      });
      storage.sort(function (a, b) {
        var location = a.priority - b.priority;
        return location != 0
          ? location
          : a.id < b.id
          ? -1
          : a.id > b.id
          ? 1
          : a.selector < b.selector
          ? -1
          : a.selector > b.selector
          ? 1
          : 0;
      });
    },

    /** @deprecated For backward compatibility only; use {@link specify} instead. */
    list: [],

    /** @deprecated For backward compatibility only; use {@link specify} instead. */
    register: function (sheet) {
      Behaviour.list.push(sheet);
    },

    start: function () {
      Behaviour.addLoadEvent(function () {
        Behaviour.apply();
      });
    },

    apply: function () {
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
    applySubtree: function (startNode, includeSelf) {
      if (!Array.isArray(startNode)) {
        startNode = [startNode];
      }
      storage.forEach(function (registration) {
        if (registration.id == "_deprecated") {
          Behaviour.list.forEach(function (sheet) {
            for (var selector in sheet) {
              startNode.forEach(function (n) {
                try {
                  var list = findElementsBySelector(n, selector, includeSelf);
                  if (list.length > 0) {
                    // just to simplify setting of a breakpoint.
                    //console.log("deprecated:" + selector + " on " + list.length + " elements");
                    list.forEach(sheet[selector]);
                  }
                } catch (e) {
                  console.error(e);
                }
              });
            }
          });
        } else {
          startNode.forEach(function (node) {
            try {
              var list = findElementsBySelector(
                node,
                registration.selector,
                includeSelf,
              );
              if (list.length > 0) {
                //console.log(registration.id + ":" + registration.selector + " @" + registration.priority + " on " + list.length + " elements");
                list.forEach(registration.behavior);
              }
            } catch (e) {
              console.error(e);
            }
          });
        }
      });
    },

    addLoadEvent: function (func) {
      var oldonload = window.onload;

      if (typeof window.onload != "function") {
        window.onload = func;
      } else {
        window.onload = function (e) {
          oldonload(e);
          func(e);
        };
      }
    },
  };
})();

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

function findElementsBySelector(startNode, selector, includeSelf) {
  if (includeSelf) {
    var isSelfOrChild = function (c) {
      // eslint-disable-next-line no-constant-condition
      while (true) {
        if (startNode == c) {
          return true;
        }
        if (c == null) {
          return false;
        }
        c = c.parentNode;
      }
    };
    return Array.from(startNode.parentNode.querySelectorAll(selector)).filter(
      isSelfOrChild,
    );
  } else {
    return Array.from(startNode.querySelectorAll(selector));
  }
}

document.getElementsBySelector = function (selector) {
  return findElementsBySelector(document, selector);
};
