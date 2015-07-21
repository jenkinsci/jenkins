
// find the nearest ancestor node that has the given tag name
exports.findAncestor = function(e, tagName) {
    do {
        e = e.parentNode;
    } while (e != null && e.tagName != tagName);
    return e;
};

exports.findAncestorClass = function(e, cssClass) {
    do {
        e = e.parentNode;
    } while (e != null && !Element.hasClassName(e,cssClass));
    return e;
};

exports.findFollowingTR = function(input, className) {
    // identify the parent TR
    var tr = input;
    while (tr && tr.tagName !== "TR" && !tr.hasClassName('tr')){
        tr = tr.parentNode;
    }
    if(tr && (tr.tagName === 'TR' ||  tr.hasClassName('tr'))){
      // then next TR that matches the CSS
      do {
          tr = $(tr).next();
      } while (tr != null && ((tr.tagName != "TR" && !tr.hasClassName('tr')) || !Element.hasClassName(tr,className)));
    }
    return tr;
};

exports.findNode = function(src,filter,traversalF) {
    while(src!=null) {
        src = traversalF(src);
        if(src!=null && filter(src))
            return src;
    }
    return null;
};

/**
* Traverses a form in the reverse document order starting from the given element (but excluding it),
* until the given filter matches, or run out of an element.
*/
exports.findPrevious = function(src,filter) {
    return exports.findNode(src,filter,function (e) {
        var p = e.previousSibling;
        if(p==null) return e.parentNode;
        while(p.lastChild!=null)
            p = p.lastChild;
        return p;
    });
};

exports.findNext = function(src,filter) {
    return exports.findNode(src,filter,function (e) {
        var n = e.nextSibling;
        if(n==null) return e.parentNode;
        while(n.firstChild!=null)
            n = n.firstChild;
        return n;
    });
};

exports.findFormItem = function(src,name,directionF) {
    var name2 = "_."+name; // handles <textbox field="..." /> notation silently
    return directionF(src,function(e){ return (e.tagName=="INPUT" || e.tagName=="TEXTAREA" || e.tagName=="SELECT") && (e.name==name || e.name==name2); });
};

/**
* Traverses a form in the reverse document order and finds an INPUT element that matches the given name.
*/
exports.findPreviousFormItem = function(src,name) {
    return exports.findFormItem(src,name,findPrevious);
};

exports.findNextFormItem = function(src,name) {
    return exports.findFormItem(src,name,findNext);
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
exports.findFormParent = function(e,form,isStatic) {
    isStatic = isStatic || false;

    if (form==null) // caller can pass in null to have this method compute the owning form
        form = exports.findAncestor(e,"FORM");

    while(e!=form) {
        // this is used to create a group where no single containing parent node exists,
        // like <optionalBlock>
        var nameRef = e.getAttribute("nameRef");
        if(nameRef!=null)
            e = $(nameRef);
        else
            e = e.parentNode;

        if(!isStatic && e.getAttribute("field-disabled")!=null)
            return null;  // this field shouldn't contribute to the final result

        var name = e.getAttribute("name");
        if(name!=null && name.length>0) {
            if(e.tagName=="INPUT" && !isStatic && !xor(e.checked,Element.hasClassName(e,"negative")))
                return null;  // field is not active

            return e;
        }
    }

    return form;
};


/**
 * Find the sibling (in the sense of the structured form submission) form item of the given name,
 * and returns that DOM node.
 *
 * @param {HTMLElement} e
 * @param {string} name
 *      Name of the control to find. Can include "../../" etc in the prefix.
 *      See @RelativePath.
 *
 *      We assume that the name is normalized and doesn't contain any redundant component.
 *      That is, ".." can only appear as prefix, and "foo/../bar" is not OK (because it can be reduced to "bar")
 */
exports.findNearBy = function(e,name) {
    while (name.startsWith("../")) {
        name = name.substring(3);
        e = exports.findFormParent(e,null,true);
    }

    // name="foo/bar/zot"  -> prefixes=["bar","foo"] & name="zot"
    var prefixes = name.split("/");
    name = prefixes.pop();
    prefixes = prefixes.reverse();

    // does 'e' itself match the criteria?
    // as some plugins use the field name as a parameter value, instead of 'value'
    var p = exports.findFormItem(e,name,function(e,filter) {
        return filter(e) ? e : null;
    });
    if (p!=null && prefixes.length==0)    return p;

    var owner = exports.findFormParent(e,null,true);

    function locate(iterator,e) {// keep finding elements until we find the good match
        while (true) {
            e = iterator(e,name);
            if (e==null)    return null;

            // make sure this candidate element 'e' is in the right point in the hierarchy
            var p = e;
            for (var i=0; i<prefixes.length; i++) {
                p = exports.findFormParent(p,null,true);
                if (p.getAttribute("name")!=prefixes[i])
                    return null;
            }
            if (exports.findFormParent(p,null,true)==owner)
                return e;
        }
    }

    return locate(exports.findPreviousFormItem,e) || locate(exports.findNextFormItem,e);
};

// Hack to offer backward compatibility for callers of the
// global version that used to be defined in hudson-behavior.js
require('../backcompatib')
    .globalize(module, ['findAncestor', 'findAncestorClass', 'findFollowingTR',
            {from: 'findNode', to: 'find'}, 'findPrevious', 'findNext',
            'findFormItem', 'findPreviousFormItem', 'findNextFormItem',
            'findFormParent', 'findNearBy']);
