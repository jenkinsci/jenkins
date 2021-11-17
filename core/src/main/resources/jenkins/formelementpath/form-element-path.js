(function () {
    function pack(n, v) {
        var o = {};
        o[n] = v;
        return o;
    }

    function pre(selector, behavior) {
        if (Behaviour.specify) {
            Behaviour.specify(selector, "form-element-path", -200, behavior);
        } else {
            Behaviour.list.unshift(pack(selector, behavior));
        }
    }

    function post(selector, behavior) {
        if (Behaviour.specify) {
            Behaviour.specify(selector, "form-element-path", 200, behavior);
        } else {
            Behaviour.list.shift(pack(selector, behavior));
        }
    }


    // patch hetero-list-container handling to capture name before hudson-behavior.js kicks in
    // in Jenkins 1.473 and newer the suffix is already set in hetero-list.js
    pre(".hetero-list-container", function (e) {
        var proto = $(e).down("DIV.prototypes");
        var d = proto.down();
        if (!proto.next().hasAttribute('suffix')) {
            proto.next().setAttribute('suffix', d.getAttribute("name"));
        }
    });

    // then post process to add @suffix to the button
    post(".hetero-list-container", function (e) {
        var b = $(e.lastChild);
        var button = b.getElementsByTagName("button")[0]
        if (!button.hasAttribute('suffix')) {
            button.setAttribute("suffix", b.getAttribute("suffix"));
        }
    });
})();

Behaviour.addLoadEvent(function () {
    function buildFormTree(form) {
        // I initially tried to use an associative array with DOM elements as keys
        // but that doesn't seem to work neither on IE nor Firefox.
        // so I switch back to adding a dynamic property on DOM.
        form.formDom = {}; // root object

        var doms = []; // DOMs that we added 'formDom' for.
        doms.push(form);

        function addProperty(parent, name, value) {
            name = shortenName(name);
            if (parent[name] != null) {
                if (parent[name].push == null) // is this array?
                    parent[name] = [parent[name]];
                parent[name].push(value);
            } else {
                parent[name] = value;
            }
        }

        // find the grouping parent node, which will have @name.
        // then return the corresponding object in the map
        function findParent(e) {
            var p = findFormParent(e, form);
            if (p == null) return {};

            var m = p.formDom;
            if (m == null) {
                // this is a new grouping node
                doms.push(p);
                p.formDom = m = {};
                addProperty(findParent(p), p.getAttribute("name"), p);
            }
            return m;
        }

        var jsonElement = null;

        for (var i = 0; i < form.elements.length; i++) {
            var e = form.elements[i];
            if (e.name == "json") {
                jsonElement = e;
                continue;
            }
            if (e.tagName == "FIELDSET")
                continue;
            if (e.tagName == "SELECT" && e.multiple) {
                addProperty(findParent(e), e.name, e);
                continue;
            }

            var p;
            var type = e.getAttribute("type");
            if (type == null) type = "";
            switch (type.toLowerCase()) {
                case "button":
                    var element
                    // modern buttons aren't wrapped in spans
                    if (e.classList.contains('jenkins-button')) {
                        element = e
                    } else {
                        p = findParent(e);
                        element = $(e.parentNode.parentNode); // YUI's surrounding <SPAN> that has interesting classes
                    }
                    var name = null;
                    ["repeatable-add", "repeatable-delete", "hetero-list-add", "expand-button", "advanced-button", "apply-button", "validate-button"]
                        .forEach(function (clazz) {
                            if (element.hasClassName(clazz)) {
                                name = clazz;
                            }
                        });
                    if (name == null) {
                        if (name == null) {
                            element = element.parentNode.previousSibling;
                            if (element != null && (typeof ($(element).hasClassName) == "function") && $(element).hasClassName('repeatable-insertion-point'))
                                name = "hetero-list-add";
                        }
                    }
                    if (name != null)
                        addProperty(p, name, e);
                    break;
                case "submit":
                    break;
                case "checkbox":
                case "radio":
                    p = findParent(e);
                    if (e.groupingNode) {
                        e.formDom = {};
                    }
                    addProperty(p, e.name, e);
                    break;
                case "file":
                    // to support structured form submission with file uploads,
                    // rename form field names to unique ones, and leave this name mapping information
                    // in JSON. this behavior is backward incompatible, so only do
                    // this when
                    p = findParent(e);
                    if (e.getAttribute("jsonAware") != null) {
                        var on = e.getAttribute("originalName");
                        if (on != null) {
                            addProperty(p, on, e);
                        } else {
                            addProperty(p, e.name, e);
                        }
                    }
                    // switch to multipart/form-data to support file submission
                    // @enctype is the standard, but IE needs @encoding.
                    form.enctype = form.encoding = "multipart/form-data";
                    break;
                // otherwise fall through
                default:
                    p = findParent(e);
                    addProperty(p, e.name, e);
                    break;
            }
        }

        function annotate(e, path) {
            e.setAttribute("path", path);
            var o = e.formDom || {};
            for (var key in o) {
                var v = o[key];

                function child(v, i) {
                    var suffix = null;
                    var newKey = key;
                    if (v.parentNode.className && v.parentNode.className.indexOf("one-each") > -1 && v.parentNode.className.indexOf("honor-order") > -1) {
                        suffix = v.getAttribute("descriptorId").split(".").pop()
                    } else if (v.getAttribute("type") == "radio") {
                        suffix = v.value
                        while (newKey.substring(0, 8) == 'removeme')
                            newKey = newKey.substring(newKey.indexOf('_', 8) + 1);
                    } else if (v.getAttribute("suffix") != null) {
                        suffix = v.getAttribute("suffix")
                    } else {
                        if (i > 0)
                            suffix = i;
                    }
                    if (suffix == null) suffix = "";
                    else suffix = '[' + suffix + ']';

                    annotate(v, path + "/" + newKey + suffix);
                }

                if (v instanceof Array) {
                    var i = 0;
                    $A(v)._each(function (v) {
                        child(v, i++)
                    })
                } else {
                    child(v, 0)
                }
            }

        }

        annotate(form, "");

        // clean up
        for (i = 0; i < doms.length; i++)
            doms[i].formDom = null;

        return true;
    }

    function applyAll() {
        $$("FORM")._each(function (e) {
            buildFormTree(e);
        })
    }

    applyAll();

    // run this periodically to cope with DOM changes
    window.setInterval(applyAll, 1000);

    // in Jenkins 1.452 and onward, there's a callback available when DOM changes
    if (typeof (layoutUpdateCallback) != "undefined") {
        layoutUpdateCallback.add(applyAll)
    }

    // expose this globally so that Selenium can call it
    window.recomputeFormElementPath = applyAll;
});
