/**
 * Adds a 'path' attribute to form elements in the DOM.
 * This is useful for providing stable selectors for UI testing.
 *
 * Instead of selecting by xpath with something like div/span/input[text() = 'Name']
 * You can use the path attribute: /org-jenkinsci-plugins-workflow-libs-FolderLibraries/libraries/name
 */
document.addEventListener("DOMContentLoaded", function () {
  // most of this is copied from hudson-behaviour.js
  function buildFormTree(form) {
    form.formDom = {}; // root object

    var doms = []; // DOMs that we added 'formDom' for.
    doms.push(form);

    function addProperty(parent, name, value) {
      name = shortenName(name);
      if (parent[name] != null) {
        // is this array?
        if (parent[name].push == null) {
          parent[name] = [parent[name]];
        }
        parent[name].push(value);
      } else {
        parent[name] = value;
      }
    }

    // find the grouping parent node, which will have @name.
    // then return the corresponding object in the map
    function findParent(e) {
      var p = findFormParent(e, form);
      if (p == null) {
        return {};
      }

      var m = p.formDom;
      if (m == null) {
        // this is a new grouping node
        doms.push(p);
        p.formDom = m = {};
        addProperty(findParent(p), p.getAttribute("name"), p);
      }
      return m;
    }

    for (let i = 0; i < form.elements.length; i++) {
      var e = form.elements[i];
      if (e.name == "json") {
        continue;
      }
      if (e.tagName == "FIELDSET") {
        continue;
      }
      if (e.tagName == "SELECT" && e.multiple) {
        addProperty(findParent(e), e.name, e);
        continue;
      }

      var p;
      var type = e.getAttribute("type");
      if (type == null) {
        type = "";
      }
      switch (type.toLowerCase()) {
        case "button":
          var element;
          // modern buttons aren't wrapped in spans
          if (
            e.classList.contains("jenkins-button") ||
            e.classList.contains("repeatable-delete")
          ) {
            p = findParent(e);
            element = e;
          } else {
            p = findParent(e);
            element = e.parentNode.parentNode; // YUI's surrounding <SPAN> that has interesting classes
          }
          var name = null;
          [
            "repeatable-add",
            "repeatable-delete",
            "hetero-list-add",
            "advanced-button",
            "apply-button",
            "validate-button",
          ].forEach(function (clazz) {
            if (element.classList.contains(clazz)) {
              name = clazz;
            }
          });
          if (name == null) {
            if (name == null) {
              element = element.parentNode.previousSibling;
              if (
                element != null &&
                element.classList &&
                element.classList.contains("repeatable-insertion-point")
              ) {
                name = "hetero-list-add";
              }
            }
          }
          if (name != null) {
            addProperty(p, name, e);
          }
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

      function child(v, i, key) {
        var suffix = null;
        var newKey = key;
        if (
          v.parentNode.className &&
          v.parentNode.className.indexOf("one-each") > -1 &&
          v.parentNode.className.indexOf("honor-order") > -1
        ) {
          suffix = v.getAttribute("descriptorId").split(".").pop();
        } else if (v.getAttribute("type") == "radio") {
          suffix = v.value;
          while (newKey.substring(0, 8) == "removeme") {
            newKey = newKey.substring(newKey.indexOf("_", 8) + 1);
          }
        } else if (v.getAttribute("suffix") != null) {
          suffix = v.getAttribute("suffix");
        } else {
          if (i > 0) {
            suffix = i;
          }
        }
        if (suffix == null) {
          suffix = "";
        } else {
          suffix = "[" + suffix + "]";
        }

        annotate(v, path + "/" + newKey + suffix);
      }

      for (let key in o) {
        var v = o[key];

        if (v instanceof Array) {
          var i = 0;
          v.forEach(function (v) {
            child(v, i++, key);
          });
        } else {
          child(v, 0, key);
        }
      }
    }

    annotate(form, "");

    // clean up
    for (let i = 0; i < doms.length; i++) {
      doms[i].formDom = null;
    }

    return true;
  }

  function applyAll() {
    document.querySelectorAll("FORM").forEach(function (e) {
      buildFormTree(e);
    });
  }

  /* JavaScript sometimes re-arranges the DOM and doesn't call layout callback
   * known cases: YUI buttons, CodeMirror.
   * We run apply twice to work around this, once immediately so that most cases work and the tests don't need to wait,
   * and once to catch the edge cases.
   */
  function hardenedApplyAll() {
    applyAll();

    setTimeout(function () {
      applyAll();
    }, 1000);
  }

  hardenedApplyAll();

  layoutUpdateCallback.add(hardenedApplyAll);

  // expose this globally so that Selenium can call it
  window.recomputeFormElementPath = hardenedApplyAll;
});
