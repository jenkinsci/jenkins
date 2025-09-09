/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Yahoo! Inc., Alan Harder, InfraDNA, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
//
//
// JavaScript for Jenkins
//     See http://www.ibm.com/developerworks/web/library/wa-memleak/?ca=dgr-lnxw97JavascriptLeaks
//     for memory leak patterns and how to prevent them.
//

if (window.isRunAsTest) {
  // Disable postMessage when running in test mode (HtmlUnit).
  window.postMessage = false;
}

// create a new object whose prototype is the given object
// eslint-disable-next-line no-unused-vars
function object(o) {
  function F() {}
  F.prototype = o;
  return new F();
}

function TryEach(fn) {
  return function (name) {
    try {
      fn(name);
    } catch (e) {
      console.error(e);
    }
  };
}

/**
 * A function that returns false if the page is known to be invisible.
 */
var isPageVisible = (function () {
  // @see https://developer.mozilla.org/en/DOM/Using_the_Page_Visibility_API
  // Set the name of the hidden property and the change event for visibility
  var hidden, visibilityChange;
  if (typeof document.hidden !== "undefined") {
    hidden = "hidden";
    visibilityChange = "visibilitychange";
  } else if (typeof document.mozHidden !== "undefined") {
    hidden = "mozHidden";
    visibilityChange = "mozvisibilitychange";
  } else if (typeof document.msHidden !== "undefined") {
    hidden = "msHidden";
    visibilityChange = "msvisibilitychange";
  } else if (typeof document.webkitHidden !== "undefined") {
    hidden = "webkitHidden";
    visibilityChange = "webkitvisibilitychange";
  }

  // By default, visibility set to true
  var pageIsVisible = true;

  // If the page is hidden, prevent any polling
  // if the page is shown, restore pollings
  function onVisibilityChange() {
    pageIsVisible = !document[hidden];
  }

  // Warn if the browser doesn't support addEventListener or the Page Visibility API
  if (
    typeof document.addEventListener !== "undefined" &&
    typeof hidden !== "undefined"
  ) {
    // Init the value to the real state of the page
    pageIsVisible = !document[hidden];

    // Handle page visibility change
    document.addEventListener(visibilityChange, onVisibilityChange, false);
  }

  return function () {
    return pageIsVisible;
  };
})();

// id generator
var iota = 0;

// crumb information
var crumb = {
  fieldName: null,
  value: null,

  init: function (crumbField, crumbValue) {
    if (crumbField == "") {
      // layout.jelly passes in "" whereas it means null.
      return;
    }
    this.fieldName = crumbField;
    this.value = crumbValue;
  },

  /**
   * Adds the crumb value into the given hash or array and returns it.
   */
  wrap: function (headers) {
    if (this.fieldName != null) {
      if (headers instanceof Array) {
        // TODO prototype.js only seems to interpret object
        headers.push(this.fieldName, this.value);
      } else {
        headers[this.fieldName] = this.value;
      }
    }
    // TODO return value unused
    return headers;
  },

  /**
   * Puts a hidden input field to the form so that the form submission will have the crumb value
   */
  appendToForm: function (form) {
    if (this.fieldName == null) {
      // noop
      return;
    }
    var div = document.createElement("div");
    div.classList.add("jenkins-!-display-contents");
    div.innerHTML =
      "<input type=hidden name='" +
      this.fieldName +
      "' value='" +
      this.value +
      "'>";
    form.appendChild(div);
    if (form.enctype == "multipart/form-data") {
      if (form.action.indexOf("?") != -1) {
        form.action = form.action + "&" + this.fieldName + "=" + this.value;
      } else {
        form.action = form.action + "?" + this.fieldName + "=" + this.value;
      }
    }
  },
};

(function initializeCrumb() {
  var extensionsAvailable = document.head.getAttribute(
    "data-extensions-available",
  );
  if (extensionsAvailable === "true") {
    var crumbHeaderName = document.head.getAttribute("data-crumb-header");
    var crumbValue = document.head.getAttribute("data-crumb-value");
    if (crumbHeaderName && crumbValue) {
      crumb.init(crumbHeaderName, crumbValue);
    }
  }
  // else, the instance is starting, restarting, etc.
})();

var isRunAsTest = undefined;
// Be careful, this variable does not include the absolute root URL as in Java part of Jenkins,
// but the contextPath only, like /jenkins
var rootURL = "not-defined-yet"; // eslint-disable-line no-unused-vars
var resURL = "not-defined-yet"; // eslint-disable-line no-unused-vars

(function initializeUnitTestAndURLs() {
  var dataUnitTest = document.head.getAttribute("data-unit-test");
  if (dataUnitTest !== null) {
    isRunAsTest = dataUnitTest === "true";
  }
  var dataRootURL = document.head.getAttribute("data-rooturl");
  if (dataRootURL !== null) {
    rootURL = dataRootURL;
  }
  var dataResURL = document.head.getAttribute("data-resurl");
  if (dataResURL !== null) {
    resURL = dataResURL;
  }
})();

// Form check code
//========================================================
var FormChecker = {
  // pending requests
  queue: [],

  // conceptually boolean, but doing so create concurrency problem.
  // that is, during unit tests, the AJAX.send works synchronously, so
  // the onComplete happens before the send method returns. On a real environment,
  // more likely it's the other way around. So setting a boolean flag to true or false
  // won't work.
  inProgress: 0,

  // defines the maximum number of parallel checks to be run
  // should be '1' when http1.1 is used as browsers will usually throttle the number of connections
  // and having a higher value can even have a negative impact. But with http2 enabled, this can
  // be a great performance improvement
  maxParallel: 1,

  /**
   * Schedules a form field check. Executions are serialized to reduce the bandwidth impact.
   *
   * @param url
   *      Remote doXYZ URL that performs the check. Query string should include the field value.
   * @param method
   *      HTTP method. GET or POST. I haven't confirmed specifics, but some browsers seem to cache GET requests.
   * @param target
   *      HTML element whose innerHTML will be overwritten when the check is completed.
   */
  delayedCheck: function (url, method, target) {
    if (url == null || method == null || target == null) {
      // don't know whether we should throw an exception or ignore this. some broken plugins have illegal parameters
      return;
    }
    this.queue.push({ url: url, method: method, target: target });
    this.schedule();
  },

  sendRequest: function (url, params) {
    const method = params.method.toLowerCase();
    if (method !== "get") {
      var idx = url.indexOf("?");
      params.parameters = url.substring(idx + 1);
      url = url.substring(0, idx);
    }

    fetch(url, {
      method: params.method,
      headers: crumb.wrap({
        "Content-Type": "application/x-www-form-urlencoded",
      }),
      body: method !== "get" ? params.parameters : null,
    }).then((response) => {
      params.onComplete(response);
    });
  },

  schedule: function () {
    if (this.inProgress >= this.maxParallel) {
      return;
    }
    if (this.queue.length === 0) {
      return;
    }

    var next = this.queue.shift();
    this.sendRequest(next.url, {
      method: next.method,
      onComplete: function (x) {
        x.text().then((responseText) => {
          updateValidationArea(next.target, responseText);
          FormChecker.inProgress--;
          FormChecker.schedule();
          layoutUpdateCallback.call();
        });
      },
    });
    this.inProgress++;
  },
};

/**
 * Converts a JavaScript object to a URL form encoded string.
 */
function objectToUrlFormEncoded(parameters) {
  // https://stackoverflow.com/a/37562814/4951015
  // Code could be simplified if support for HTMLUnit is dropped
  // body: new URLSearchParams(parameters) is enough then, but it doesn't work in HTMLUnit currently
  let formBody = [];
  for (const property in parameters) {
    const encodedKey = encodeURIComponent(property);
    const encodedValue = encodeURIComponent(parameters[property]);
    formBody.push(encodedKey + "=" + encodedValue);
  }
  return formBody.join("&");
}

/**
 * Detects if http2 protocol is enabled.
 */
function isHttp2Enabled() {
  try {
    const p = performance.getEntriesByType("resource");
    if (p.length > 0) {
      if ("nextHopProtocol" in p[0] && p[0].nextHopProtocol === "h2") {
        return true;
      }
    }
  } catch (e) {
    console.error(e.stack || e);
  }
  return false;
}

// detect if we're using http2 and if yes increase the maxParallel connections
// of the FormChecker
if (isHttp2Enabled()) {
  FormChecker.maxParallel = 30;
}

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
function findNearBy(e, name) {
  while (name.startsWith("../")) {
    name = name.substring(3);
    e = findFormParent(e, null, true);
  }

  // name="foo/bar/zot"  -> prefixes=["bar","foo"] & name="zot"
  var prefixes = name.split("/");
  name = prefixes.pop();
  prefixes = prefixes.reverse();

  // does 'e' itself match the criteria?
  // as some plugins use the field name as a parameter value, instead of 'value'
  var p = findFormItem(e, name, function (e, filter) {
    return filter(e) ? e : null;
  });
  if (p != null && prefixes.length == 0) {
    return p;
  }

  var owner = findFormParent(e, null, true);

  function locate(iterator, e) {
    // keep finding elements until we find the good match
    while (true) {
      e = iterator(e, name);
      if (e == null) {
        return null;
      }

      // make sure this candidate element 'e' is in the right point in the hierarchy
      var p = e;
      for (var i = 0; i < prefixes.length; i++) {
        p = findFormParent(p, null, true);
        if (p.getAttribute("name") != prefixes[i]) {
          return null;
        }
      }
      if (findFormParent(p, null, true) == owner) {
        return e;
      }
    }
  }

  return locate(findPreviousFormItem, e) || locate(findNextFormItem, e);
}

function controlValue(e) {
  if (e == null) {
    return null;
  }
  // compute the form validation value to be sent to the server
  var type = e.getAttribute("type");
  if (type != null && type.toLowerCase() == "checkbox") {
    return e.checked;
  }
  return e.value;
}

function toValue(e) {
  return encodeURIComponent(controlValue(e));
}

/**
 * Builds a query string in a fluent API pattern.
 * @param {HTMLElement} owner
 *      The 'this' control.
 */
function qs(owner) {
  return {
    params: "",

    append: function (s) {
      if (this.params.length == 0) {
        this.params += "?";
      } else {
        this.params += "&";
      }
      this.params += s;
      return this;
    },

    nearBy: function (name) {
      var e = findNearBy(owner, name);
      if (e == null) {
        // skip
        return this;
      }
      return this.append(Path.tail(name) + "=" + toValue(e));
    },

    addThis: function () {
      return this.append("value=" + toValue(owner));
    },

    toString: function () {
      return this.params;
    },
  };
}

// @deprecated Use standard javascript method `e.closest(tagName)` instead
// eslint-disable-next-line no-unused-vars
function findAncestor(e, tagName) {
  console.warn(
    "Deprecated call to findAncestor - use standard javascript method `e.closest(tagName)` instead",
  );
  return e.closest(tagName);
}

// @deprecated Use standard javascript method `e.closest(className)` instead
// eslint-disable-next-line no-unused-vars
function findAncestorClass(e, cssClass) {
  console.warn(
    "Deprecated call to findAncestorClass - use standard javascript method `e.closest(className)` instead",
  );
  return e.closest("." + cssClass);
}

function isTR(tr, nodeClass) {
  return (
    tr.tagName == "TR" ||
    tr.classList.contains(nodeClass || "tr") ||
    tr.classList.contains("jenkins-form-item")
  );
}

function findFollowingTR(node, className, nodeClass) {
  // identify the parent TR
  var tr = node;
  while (!isTR(tr, nodeClass)) {
    tr = tr.parentNode;
    if (!(tr instanceof Element)) {
      return null;
    }
  }

  // then next TR that matches the CSS
  do {
    // Supports plugins with custom variants of <f:entry> that call
    // findFollowingTR(element, 'validation-error-area') and haven't migrated
    // to use querySelector
    if (className === "validation-error-area" || className === "help-area") {
      var queryChildren = tr.getElementsByClassName(className);
      if (
        queryChildren.length > 0 &&
        (isTR(queryChildren[0]) ||
          queryChildren[0].classList.contains(className))
      ) {
        return queryChildren[0];
      }
    }

    tr = tr.nextElementSibling;
  } while (tr != null && (!isTR(tr) || !tr.classList.contains(className)));

  return tr;
}

function findInFollowingTR(input, className) {
  var node = findFollowingTR(input, className);
  if (node.tagName == "TR") {
    node = node.firstElementChild.nextSibling;
  } else {
    node = node.firstElementChild;
  }
  return node;
}

function find(src, filter, traversalF) {
  while (src != null) {
    src = traversalF(src);
    if (src != null && filter(src)) {
      return src;
    }
  }
  return null;
}

/**
 * Traverses a form in the reverse document order starting from the given element (but excluding it),
 * until the given filter matches, or run out of an element.
 */
function findPrevious(src, filter) {
  return find(src, filter, function (e) {
    var p = e.previousSibling;
    if (p == null) {
      return e.parentNode;
    }
    while (p.lastElementChild != null) {
      p = p.lastElementChild;
    }
    return p;
  });
}

function findNext(src, filter) {
  return find(src, filter, function (e) {
    var n = e.nextSibling;
    if (n == null) {
      return e.parentNode;
    }
    while (n.firstElementChild != null) {
      n = n.firstElementChild;
    }
    return n;
  });
}

function findFormItem(src, name, directionF) {
  const name2 = "_." + name; // handles <textbox field="..." /> notation silently
  return directionF(src, function (e) {
    if (e.tagName === "INPUT" && e.type === "radio") {
      if (e.checked === true) {
        let r = 0;
        while (e.name.substring(r, r + 8) === "removeme") {
          //radio buttons have must be unique in repeatable blocks so name is prefixed
          r = e.name.indexOf("_", r + 8) + 1;
        }
        return name === e.name.substring(r);
      }
      return false;
    }
    return (
      (e.tagName === "INPUT" ||
        e.tagName === "TEXTAREA" ||
        e.tagName === "SELECT") &&
      (e.name === name || e.name === name2)
    );
  });
}

/**
 * Traverses a form in the reverse document order and finds an INPUT element that matches the given name.
 */
function findPreviousFormItem(src, name) {
  return findFormItem(src, name, findPrevious);
}

function findNextFormItem(src, name) {
  return findFormItem(src, name, findNext);
}

// This method seems unused in the ecosystem, only grails-plugin was using it but it's blacklisted now
/**
 * Parse HTML into DOM.
 */
// eslint-disable-next-line no-unused-vars
function parseHtml(html) {
  var c = document.createElement("div");
  c.innerHTML = html;
  return c.firstElementChild;
}

/**
 * Evaluates the script in global context.
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/eval#direct_and_indirect_eval
 */
function geval(script) {
  (0, eval)(script);
}

/**
 * Emulate the firing of an event.
 *
 * @param {HTMLElement} element
 *      The element that will fire the event
 * @param {String} event
 *      like 'change', 'blur', etc.
 */
// eslint-disable-next-line no-unused-vars
function fireEvent(element, event) {
  return !element.dispatchEvent(
    new CustomEvent(event, {
      bubbles: true,
      cancelable: true,
      detail: element,
    }),
  );
}

// Behavior rules
//========================================================
// using tag names in CSS selector makes the processing faster

/**
 * Updates the validation area for a form element
 * @param {HTMLElement} validationArea The validation area for a given form element
 * @param {string} content The content to update the validation area with
 */
function updateValidationArea(validationArea, content) {
  validationArea.classList.add("validation-error-area--visible");

  if (content === "<div/>") {
    validationArea.classList.remove("validation-error-area--visible");
    validationArea.style.height = "0px";
    validationArea.innerHTML = content;
  } else {
    // Only change content if different, causes an unnecessary animation otherwise
    if (validationArea.innerHTML !== content) {
      validationArea.innerHTML = content;
      validationArea.style.height = "auto";

      Behaviour.applySubtree(validationArea);
      // For errors with additional details, apply the subtree to the expandable details pane
      if (validationArea.nextElementSibling) {
        Behaviour.applySubtree(validationArea.nextElementSibling);
      }
    }
  }
}

function registerValidator(e) {
  // Retrieve the validation error area
  var tr = e
    .closest(".jenkins-form-item")
    .querySelector(".validation-error-area");
  if (!tr) {
    console.warn(
      "Couldn't find the expected validation element (.validation-error-area) for element",
      e.closest(".jenkins-form-item"),
    );
    return;
  }
  // find the validation-error-area
  e.targetElement = tr;

  e.targetUrl = function () {
    var url = this.getAttribute("checkUrl");
    var depends = this.getAttribute("checkDependsOn");

    if (depends == null) {
      // legacy behaviour where checkUrl is a JavaScript
      try {
        return eval(url); // need access to 'this', so no 'geval'
      } catch (e) {
        console.warn(
          "Legacy checkUrl '" + url + "' is not valid JavaScript: " + e,
        );
        return url; // return plain url as fallback
      }
    } else {
      var q = qs(this).addThis();
      if (depends.length > 0) {
        depends.split(" ").forEach(
          TryEach(function (n) {
            q.nearBy(n);
          }),
        );
      }
      return url + q.toString();
    }
  };

  var method = e.getAttribute("checkMethod") || "post";

  var url = e.targetUrl();
  try {
    FormChecker.delayedCheck(url, method, e.targetElement);
    // eslint-disable-next-line no-unused-vars
  } catch (x) {
    // this happens if the checkUrl refers to a non-existing element.
    // don't let this kill off the entire JavaScript
    console.warn(
      "Failed to register validation method: " +
        e.getAttribute("checkUrl") +
        " : " +
        e,
    );
    return;
  }

  var checker = function () {
    const validationArea = this.targetElement;
    FormChecker.sendRequest(this.targetUrl(), {
      method: method,
      onComplete: function (response) {
        // TODO Add i18n support
        response.text().then((responseText) => {
          const errorMessage = `<div class="error">An internal error occurred during form field validation (HTTP ${response.status}). Please reload the page and if the problem persists, ask the administrator for help.</div>`;
          updateValidationArea(
            validationArea,
            response.status === 200 ? responseText : errorMessage,
          );
        });
      },
    });
  };
  var oldOnchange = e.onchange;
  if (typeof oldOnchange == "function") {
    e.onchange = function () {
      checker.call(this);
      oldOnchange.call(this);
    };
  } else {
    e.onchange = checker;
  }

  var v = e.getAttribute("checkDependsOn");
  if (v) {
    v.split(" ").forEach(
      TryEach(function (name) {
        var c = findNearBy(e, name);
        if (c == null) {
          console.warn("Unable to find nearby " + name);
          return;
        }

        if (c.tagName === "INPUT" && c.type === "radio") {
          document
            .querySelectorAll(`input[name='${c.name}'][type='radio']`)
            .forEach((element) => {
              element.addEventListener("change", checker.bind(e));
            });
        } else {
          c.addEventListener("change", checker.bind(e));
        }
      }),
    );
  }

  e = null; // avoid memory leak
}

function registerRegexpValidator(e, regexp, message) {
  var tr = e
    .closest(".jenkins-form-item")
    .querySelector(".validation-error-area");
  if (!tr) {
    console.warn(
      "Couldn't find the expected parent element (.setting-main) for element",
      e.closest(".jenkins-form-item"),
    );
    return;
  }
  // find the validation-error-area
  e.targetElement = tr;
  var checkMessage = e.getAttribute("checkMessage");
  if (checkMessage) {
    message = checkMessage;
  }
  var oldOnchange = e.onchange;
  e.onchange = function () {
    var set = oldOnchange != null ? oldOnchange.call(this) : false;
    if (this.value.match(regexp)) {
      if (!set) {
        updateValidationArea(this.targetElement, `<div/>`);
      }
    } else {
      updateValidationArea(
        this.targetElement,
        `<div class="error">${message}</div>`,
      );
      set = true;
    }
    return set;
  };
  e.onchange.call(e);
  e = null; // avoid memory leak
}

/**
 * Add a validator for number fields which contains 'min', 'max' attribute
 * @param e Input element
 */
function registerMinMaxValidator(e) {
  var tr = e
    .closest(".jenkins-form-item")
    .querySelector(".validation-error-area");
  if (!tr) {
    console.warn(
      "Couldn't find the expected parent element (.setting-main) for element",
      e.closest(".jenkins-form-item"),
    );
    return;
  }
  // find the validation-error-area
  e.targetElement = tr;
  var checkMessage = e.getAttribute("checkMessage");
  if (checkMessage) {
    // eslint-disable-next-line no-undef
    message = checkMessage;
  }
  var oldOnchange = e.onchange;
  e.onchange = function () {
    var set = oldOnchange != null ? oldOnchange.call(this) : false;

    const min = this.getAttribute("min");
    const max = this.getAttribute("max");

    function isInteger(str) {
      return str.match(/^-?\d*$/) !== null;
    }

    if (isInteger(this.value)) {
      // Ensure the value is an integer
      if (min !== null && isInteger(min) && max !== null && isInteger(max)) {
        // Both min and max attributes are available

        if (min <= max) {
          // Add the validator if min <= max
          if (
            parseInt(min) > parseInt(this.value) ||
            parseInt(this.value) > parseInt(max)
          ) {
            // The value is out of range
            updateValidationArea(
              this.targetElement,
              `<div class="error">This value should be between ${min} and ${max}</div>`,
            );
            set = true;
          } else {
            if (!set) {
              updateValidationArea(this.targetElement, `<div/>`);
            }
          }
        }
      } else if (
        min !== null &&
        isInteger(min) &&
        (max === null || !isInteger(max))
      ) {
        // There is only 'min' available

        if (parseInt(min) > parseInt(this.value)) {
          updateValidationArea(
            this.targetElement,
            `<div class="error">This value should be larger than ${min}</div>`,
          );
          set = true;
        } else {
          if (!set) {
            updateValidationArea(this.targetElement, `<div/>`);
          }
        }
      } else if (
        (min === null || !isInteger(min)) &&
        max !== null &&
        isInteger(max)
      ) {
        // There is only 'max' available

        if (parseInt(max) < parseInt(this.value)) {
          updateValidationArea(
            this.targetElement,
            `<div class="error">This value should be less than ${max}</div>`,
          );
          set = true;
        } else {
          if (!set) {
            updateValidationArea(this.targetElement, `<div/>`);
          }
        }
      }
    }
    return set;
  };
  e.onchange.call(e);
  e = null; // avoid memory leak
}

/**
 * Prevent user input 'e' or 'E' in <f:number>
 * @param event Input event
 */
function preventInputEe(event) {
  if (event.which === 69 || event.which === 101) {
    event.preventDefault();
  }
}

// eslint-disable-next-line no-unused-vars
function escapeHTML(html) {
  return html
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

/**
 * Replaces a <input> with a <button class="jenkins-button">
 *
 * @param e
 *      button element
 * @param onclick
 *      onclick handler
 * @return
 *      wrapper with some functions (formerly a YUI widget).
 * @deprecated use <button class="jenkins-button"> and attach event listeners with standard javascript
 */
function makeButton(e, onclick) {
  console.warn(
    "Deprecated call to makeButton - use <button class='jenkins-button'> instead and standard javascript to attach listeners.",
  );
  const h = e.onclick;
  const n = e.name;

  const button = document.createElement("button");
  if (e.id) {
    button.id = e.id;
  }
  if (onclick != null) {
    button.addEventListener("click", onclick);
  }
  if (h != null) {
    button.addEventListener("click", h);
  }
  if (n) {
    // copy the name
    button.setAttribute("name", n);
  }
  if (e.type === "submit" || e.type === "button") {
    button.type = e.type;
  }
  const length = e.attributes.length;
  for (let i = 0; i < length; i++) {
    const attribute = e.attributes[i];
    const attributeName = attribute.name;
    if (attributeName.startsWith("data-")) {
      button.setAttribute(attributeName, attribute.value);
    }
  }
  button.innerText = e.value;
  button.classList.add("jenkins-button");
  const classNames = e.classList;
  if (classNames.contains("primary") || classNames.contains("submit-button")) {
    button.classList.add("jenkins-button--primary");
  }
  classNames.remove("primary");
  classNames.remove("submit-button");
  classNames.remove("yui-button");
  for (let i = 0; i < classNames.length; i++) {
    button.classList.add(classNames.item(i));
  }

  function Button(button) {
    this.button = button;
  }
  Button.prototype.set = function (attributeName, value) {
    if (attributeName === "disabled") {
      if (value) {
        this.button.disabled = "disabled";
      } else {
        this.button.removeAttribute("disabled");
      }
    }
  };
  Button.prototype.getForm = function () {
    return this.button.closest("form");
  };
  e.parentNode.insertBefore(button, e);
  e.remove();
  return new Button(button);
}

/*
    If we are inside 'to-be-removed' class, some HTML altering behaviors interact badly, because
    the behavior re-executes when the removed master copy gets reinserted later.
 */
function isInsideRemovable(e) {
  return !!e.closest(".to-be-removed");
}

/**
 * Render the template captured by &lt;l:renderOnDemand> at the element 'e' and replace 'e' by the content.
 *
 * @param {HTMLElement} e
 *      The place holder element to be lazy-rendered.
 * @param {boolean} noBehaviour
 *      if specified, skip the application of behaviour rule.
 */
function renderOnDemand(e, callback, noBehaviour) {
  if (!e || !e.classList.contains("render-on-demand")) {
    return;
  }

  let proxyMethod = e.getAttribute("data-proxy-method");
  let proxyUrl = e.getAttribute("data-proxy-url");
  let proxyCrumb = e.getAttribute("data-proxy-crumb");
  let proxyUrlNames = e.getAttribute("data-proxy-url-names").split(",");

  var proxy = window[proxyMethod](proxyUrl, proxyCrumb, proxyUrlNames);
  proxy.render(function (t) {
    var contextTagName = e.parentNode.tagName;
    var c;
    if (contextTagName == "TBODY") {
      c = document.createElement("DIV");
      c.innerHTML = "<TABLE><TBODY>" + t.responseText + "</TBODY></TABLE>";
      c = c./*JENKINS-15494*/ lastElementChild.firstElementChild;
    } else {
      c = document.createElement(contextTagName);
      c.innerHTML = t.responseText;
    }

    var elements = [];
    while (c.firstElementChild != null) {
      var n = c.firstElementChild;
      e.parentNode.insertBefore(n, e);
      if (n.nodeType == 1 && !noBehaviour) {
        elements.push(n);
      }
    }
    e.remove();

    evalInnerHtmlScripts(t.responseText, function () {
      Behaviour.applySubtree(elements, true);
      if (callback) {
        callback(t);
      }
    });
  });
}

/**
 * Finds all the script tags
 */
function evalInnerHtmlScripts(text, callback) {
  var q = [];
  var matchAll = new RegExp("<script([^>]*)>([\\S\\s]*?)</script>", "img");
  var matchOne = new RegExp("<script([^>]*)>([\\S\\s]*?)</script>", "im");
  var srcAttr = new RegExp("src=['\"]([^'\"]+)['\"]", "i");
  (text.match(matchAll) || []).map(function (s) {
    var m = s.match(srcAttr);
    if (m) {
      q.push(function (cont) {
        loadScript(m[1], cont);
      });
    } else {
      q.push(function (cont) {
        geval(s.match(matchOne)[2]);
        cont();
      });
    }
  });
  q.push(callback);
  sequencer(q);
}

/**
 * Take an array of (typically async) functions and run them in a sequence.
 * Each of the function in the array takes one 'continuation' parameter, and upon the completion
 * of the function it needs to invoke "continuation()" to signal the execution of the next function.
 */
function sequencer(fs) {
  var nullFunction = function () {};
  function next() {
    if (fs.length > 0) {
      (fs.shift() || nullFunction)(next);
    }
  }
  return next();
}

function progressBarOnClick() {
  var href = this.getAttribute("href");
  if (href != null) {
    window.location = href;
  }
}

function labelAttachPreviousOnClick() {
  var e = this.previousElementSibling;
  while (e != null) {
    if (e.classList.contains("jenkins-radio")) {
      e = e.querySelector("input");
    }
    if (e.tagName == "INPUT") {
      e.click();
      break;
    }
    e = e.previousElementSibling;
  }
}

function helpButtonOnClick() {
  let helpArea;

  // Custom condition for repeatable chunks
  if (this.parentNode.classList.contains("repeated-chunk__header")) {
    helpArea = this.closest(".repeated-chunk").querySelector(".help-area");
  } else {
    helpArea =
      findFollowingTR(this, "help-area", "help-sibling") ||
      findFollowingTR(this, "help-area", "setting-help") ||
      findFollowingTR(this, "help-area");
  }

  var div = helpArea.firstElementChild;
  if (!div.classList.contains("help")) {
    div = div.nextElementSibling.firstElementChild;
  }

  if (helpArea.style.display !== "block") {
    helpArea.style.display = "block";
    // make it visible

    fetch(this.getAttribute("helpURL")).then((rsp) => {
      rsp.text().then((responseText) => {
        if (rsp.ok) {
          var from = rsp.headers.get("X-Plugin-From");
          // Which plugin is this from?
          div.innerHTML =
            responseText +
            (from ? "<div class='from-plugin'>" + from + "</div>" : "");

          // Ensure links open in new window unless explicitly specified otherwise
          var links = div.getElementsByTagName("a");
          for (var i = 0; i < links.length; i++) {
            var link = links[i];
            if (link.hasAttribute("href")) {
              // ignore document anchors
              if (!link.hasAttribute("target")) {
                link.setAttribute("target", "_blank");
              }
              if (!link.hasAttribute("rel")) {
                link.setAttribute("rel", "noopener noreferrer");
              }
            }
          }
        } else {
          div.innerHTML =
            "<b>ERROR</b>: Failed to load help file: " + rsp.statusText;
        }
        layoutUpdateCallback.call();
      });
    });
  } else {
    helpArea.style.display = "none";
    layoutUpdateCallback.call();
  }

  return false;
}

function isCommandKey(event) {
  return event.key === "Meta";
}
function isReturnKeyDown() {
  return event.type == "keydown" && event.key === "Enter";
}
function getParentForm(element) {
  if (element == null) {
    throw "not found a parent form";
  }
  if (element instanceof HTMLFormElement) {
    return element;
  }

  return getParentForm(element.parentNode);
}

// figure out the corresponding end marker
function findEnd(e) {
  for (var depth = 0; ; e = e.nextElementSibling) {
    if (e.classList.contains("rowvg-start")) {
      depth++;
    }
    if (e.classList.contains("rowvg-end")) {
      depth--;
    }
    if (depth == 0) {
      return e;
    }
  }
}

function makeOuterVisible(b) {
  this.outerVisible = b;
  this.updateVisibility();
}

function makeInnerVisible(b) {
  this.innerVisible = b;
  this.updateVisibility();
}

function updateVisibility() {
  var display = this.outerVisible && this.innerVisible;
  for (var e = this.start; e != this.end; e = e.nextElementSibling) {
    if (e.rowVisibilityGroup && e != this.start) {
      e.rowVisibilityGroup.makeOuterVisible(this.innerVisible);
      e = e.rowVisibilityGroup.end; // the above call updates visibility up to e.rowVisibilityGroup.end inclusive
    } else {
      if (display) {
        e.style.display = "";
        e.classList.remove("form-container--hidden");
      } else {
        // TODO remove display once tab bar (ConfigTableMetaData) is able to handle hidden tabs via class and not just display
        e.style.display = "none";
        e.classList.add("form-container--hidden");
      }
    }
  }
  layoutUpdateCallback.call();
}

function rowvgStartEachRow(recursive, f) {
  if (recursive) {
    for (var e = this.start; e != this.end; e = e.nextElementSibling) {
      f(e);
    }
  } else {
    throw "not implemented yet";
  }
}

(function () {
  // This moves all link elements to the head
  // fixes JENKINS-72196 when a link is inside a div of a repeatable and the
  // div is deleted then the styling is lost for divs afterwards.
  Behaviour.specify("body link", "move-css-to-head", -9999, function (link) {
    document.head.appendChild(link);
  });

  var p = 20;
  Behaviour.specify("TABLE.sortable", "table-sortable", ++p, function (e) {
    // sortable table
    e.sortable = new Sortable.Sortable(e);
  });

  Behaviour.specify(
    "TABLE.progress-bar, div.app-progress-bar",
    "table-progress-bar",
    ++p,
    function (e) {
      // progressBar.jelly
      e.onclick = progressBarOnClick;
    },
  );

  // <label> that doesn't use ID, so that it can be copied in <repeatable>
  Behaviour.specify(
    "LABEL.attach-previous",
    "label-attach-previous",
    ++p,
    function (e) {
      e.onclick = labelAttachPreviousOnClick;
    },
  );

  // form fields that are validated via AJAX call to the server
  // elements with this class should have two attributes 'checkUrl' that evaluates to the server URL.
  Behaviour.specify(
    "INPUT.validated",
    "input-validated",
    ++p,
    registerValidator,
  );
  Behaviour.specify(
    "SELECT.validated",
    "select-validated",
    ++p,
    registerValidator,
  );
  Behaviour.specify(
    "TEXTAREA.validated",
    "textarea-validated",
    ++p,
    registerValidator,
  );

  // validate required form values
  Behaviour.specify("INPUT.required", "input-required", ++p, function (e) {
    registerRegexpValidator(e, /./, "Field is required");
  });

  // validate form values to be an integer
  Behaviour.specify("INPUT.number", "input-number", ++p, function (e) {
    e.addEventListener("keypress", preventInputEe);
    registerMinMaxValidator(e);
    registerRegexpValidator(e, /^((-?\d+)|)$/, "Not an integer");
  });

  Behaviour.specify(
    "INPUT.number-required",
    "input-number-required",
    ++p,
    function (e) {
      e.addEventListener("keypress", preventInputEe);
      registerMinMaxValidator(e);
      registerRegexpValidator(e, /^-?(\d+)$/, "Not an integer");
    },
  );

  Behaviour.specify(
    "INPUT.non-negative-number-required",
    "input-non-negative-number-required",
    ++p,
    function (e) {
      e.addEventListener("keypress", preventInputEe);
      registerMinMaxValidator(e);
      registerRegexpValidator(e, /^\d+$/, "Not a non-negative integer");
    },
  );

  Behaviour.specify(
    "INPUT.positive-number",
    "input-positive-number",
    ++p,
    function (e) {
      e.addEventListener("keypress", preventInputEe);
      registerMinMaxValidator(e);
      registerRegexpValidator(e, /^(\d*[1-9]\d*|)$/, "Not a positive integer");
    },
  );

  Behaviour.specify(
    "INPUT.positive-number-required",
    "input-positive-number-required",
    ++p,
    function (e) {
      e.addEventListener("keypress", preventInputEe);
      registerMinMaxValidator(e);
      registerRegexpValidator(e, /^[1-9]\d*$/, "Not a positive integer");
    },
  );

  Behaviour.specify(
    "A.jenkins-help-button",
    "a-jenkins-help-button",
    ++p,
    function (e) {
      e.onclick = helpButtonOnClick;
      e.tabIndex = 9999; // make help link unnavigable from keyboard
    },
  );

  // legacy class name
  Behaviour.specify("A.help-button", "a-help-button", ++p, function (e) {
    e.onclick = helpButtonOnClick;
    e.tabIndex = 9999; // make help link unnavigable from keyboard
  });

  // Script Console : settings and shortcut key
  Behaviour.specify("TEXTAREA.script", "textarea-script", ++p, function (e) {
    (function () {
      var cmdKeyDown = false;
      var mode = e.getAttribute("script-mode") || "text/x-groovy";

      // eslint-disable-next-line no-unused-vars
      var w = CodeMirror.fromTextArea(e, {
        mode: mode,
        lineNumbers: true,
        matchBrackets: true,
        onKeyEvent: function (editor, event) {
          function saveAndSubmit() {
            editor.save();
            getParentForm(e).submit();
            event.stop();
          }

          // Mac (Command + Enter)
          if (navigator.userAgent.indexOf("Mac") > -1) {
            if (event.type == "keydown" && isCommandKey(event)) {
              cmdKeyDown = true;
            }
            if (event.type == "keyup" && isCommandKey(event)) {
              cmdKeyDown = false;
            }
            if (cmdKeyDown && isReturnKeyDown()) {
              saveAndSubmit();
              return true;
            }

            // Windows, Linux (Ctrl + Enter)
          } else {
            if (event.ctrlKey && isReturnKeyDown()) {
              saveAndSubmit();
              return true;
            }
          }
        },
      }).getWrapperElement();
    })();
  });

  // deferred client-side clickable map.
  // this is useful where the generation of <map> element is time consuming
  Behaviour.specify("IMG[lazymap]", "img-lazymap-", ++p, function (e) {
    fetch(e.getAttribute("lazymap")).then((rsp) => {
      if (rsp.ok) {
        rsp.text().then((responseText) => {
          var div = document.createElement("div");
          document.body.appendChild(div);
          div.innerHTML = responseText;
          var id = "map" + iota++;
          div.firstElementChild.setAttribute("name", id);
          e.setAttribute("usemap", "#" + id);
        });
      }
    });
  });

  // Native browser resizing doesn't work for CodeMirror textboxes so let's create our own
  Behaviour.specify(".CodeMirror", "codemirror", ++p, function (codemirror) {
    const MIN_HEIGHT = Math.min(200, codemirror.clientHeight);

    const resizer = document.createElement("div");
    resizer.className = "jenkins-codemirror-resizer";

    let start_x; // eslint-disable-line no-unused-vars
    let start_y;
    let start_h;

    function height_of($el) {
      return parseInt(window.getComputedStyle($el).height.replace(/px$/, ""));
    }

    function on_drag(e) {
      codemirror.CodeMirror.setSize(
        null,
        Math.max(MIN_HEIGHT, start_h + e.y - start_y) + "px",
      );
    }

    function on_release() {
      document.body.removeEventListener("mousemove", on_drag);
      window.removeEventListener("mouseup", on_release);
    }

    resizer.addEventListener("mousedown", function (e) {
      start_x = e.x;
      start_y = e.y;
      start_h = height_of(codemirror);

      document.body.addEventListener("mousemove", on_drag);
      window.addEventListener("mouseup", on_release);
    });

    codemirror.parentNode.insertBefore(resizer, codemirror.nextSibling);
  });

  // structured form submission
  Behaviour.specify("FORM", "form", ++p, function (form) {
    crumb.appendToForm(form);
    if (form.classList.contains("no-json")) {
      return;
    }
    // add the hidden 'json' input field, which receives the form structure in JSON
    var div = document.createElement("div");
    div.classList.add("jenkins-!-display-contents");
    div.innerHTML = "<input type=hidden name=json value=init>";
    form.appendChild(div);

    var oldOnsubmit = form.onsubmit;
    if (typeof oldOnsubmit == "function") {
      form.onsubmit = function () {
        return buildFormTree(this) && oldOnsubmit.call(this);
      };
    } else {
      form.onsubmit = function () {
        return buildFormTree(this);
      };
    }

    form = null; // memory leak prevention
  });

  Behaviour.specify(
    "INPUT.submit-button",
    "input-submit-button",
    ++p,
    function (e) {
      makeButton(e);
    },
  );

  Behaviour.specify("INPUT.yui-button", "input-yui-button", ++p, function (e) {
    makeButton(e);
  });

  Behaviour.specify(
    "TR.optional-block-start,DIV.tr.optional-block-start",
    "tr-optional-block-start-div-tr-optional-block-start",
    ++p,
    function (e) {
      // see optionalBlock.jelly
      // Get the `input` from the checkbox container
      var checkbox = e.querySelector("input[type='checkbox']");

      // Set start.ref to checkbox in preparation of row-set-end processing
      e.setAttribute("ref", (checkbox.id = "cb" + iota++));
    },
  );

  // see RowVisibilityGroupTest
  Behaviour.specify(
    "TR.rowvg-start,DIV.tr.rowvg-start",
    "tr-rowvg-start-div-tr-rowvg-start",
    ++p,
    function (e) {
      e.rowVisibilityGroup = {
        outerVisible: true,
        innerVisible: true,
        /**
         * TR that marks the beginning of this visibility group.
         */
        start: e,
        /**
         * TR that marks the end of this visibility group.
         */
        end: findEnd(e),

        /**
         * Considers the visibility of the row group from the point of view of outside.
         * If you think of a row group like a logical DOM node, this is akin to its .style.display.
         */
        makeOuterVisible: makeOuterVisible,

        /**
         * Considers the visibility of the rows in this row group. Since all the rows in a rowvg
         * shares the single visibility, this just needs to be one boolean, as opposed to many.
         *
         * If you think of a row group like a logical DOM node, this is akin to its children's .style.display.
         */
        makeInnerVisible: makeInnerVisible,

        /**
         * Based on innerVisible and outerVisible, update the relevant rows' actual CSS display attribute.
         */
        updateVisibility: updateVisibility,

        /**
         * Enumerate each row and pass that to the given function.
         *
         * @param {boolean} recursive
         *      If true, this visits all the rows from nested visibility groups.
         */
        eachRow: rowvgStartEachRow,
      };
    },
  );

  Behaviour.specify(
    "INPUT.optional-block-event-item",
    "input-optional-block-event-item",
    ++p,
    function (e) {
      e.addEventListener("click", function () {
        updateOptionalBlock(e);
      });
    },
  );

  Behaviour.specify(
    "TR.row-set-end,DIV.tr.row-set-end",
    "tr-row-set-end-div-tr-row-set-end",
    ++p,
    function (e) {
      // see rowSet.jelly and optionalBlock.jelly
      // figure out the corresponding start block
      var end = e;

      for (var depth = 0; ; e = e.previousElementSibling) {
        if (e.classList.contains("row-set-end")) {
          depth++;
        }
        if (e.classList.contains("row-set-start")) {
          depth--;
        }
        if (depth == 0) {
          break;
        }
      }
      var start = e;

      // @ref on start refers to the ID of the element that controls the JSON object created from these rows
      // if we don't find it, turn the start node into the governing node (thus the end result is that you
      // created an intermediate JSON object that's always on.)
      var ref = start.getAttribute("ref");
      if (ref == null) {
        start.id = ref = "rowSetStart" + iota++;
      }

      applyNameRef(start, end, ref);
    },
  );

  Behaviour.specify(
    "TR.optional-block-start,DIV.tr.optional-block-start",
    "tr-optional-block-start-div-tr-optional-block-start-2",
    ++p,
    function (e) {
      // see optionalBlock.jelly
      // this is suffixed by a pointless string so that two processing for optional-block-start
      // can sandwich row-set-end
      // this requires "TR.row-set-end" to mark rows
      // Get the `input` from the checkbox container
      var checkbox = e.querySelector("input[type='checkbox']");
      updateOptionalBlock(checkbox);
    },
  );

  // image that shows [+] or [-], with hover effect.
  // oncollapsed and onexpanded will be called when the button is triggered.
  Behaviour.specify("IMG.fold-control", "img-fold-control", ++p, function (e) {
    function changeTo(e, img) {
      var src = e.src;
      e.src =
        src.substring(0, src.lastIndexOf("/")) +
        "/" +
        e.getAttribute("state") +
        img;
    }
    e.onmouseover = function () {
      changeTo(this, "-hover.png");
    };
    e.onmouseout = function () {
      changeTo(this, ".png");
    };
    e.parentNode.onclick = function (event) {
      var e = this.firstElementChild;
      var s = e.getAttribute("state");
      if (s == "plus") {
        e.setAttribute("state", "minus");
        if (e.onexpanded) {
          e.onexpanded();
        }
      } else {
        e.setAttribute("state", "plus");
        if (e.oncollapsed) {
          e.oncollapsed();
        }
      }
      changeTo(e, "-hover.png");
      event.stopPropagation();
      event.preventDefault();
      return false;
    };
    e = null; // memory leak prevention
  });

  // editableComboBox.jelly
  Behaviour.specify("INPUT.combobox", "input-combobox", ++p, function (c) {
    // Next element after <input class="combobox"/> should be <div class="combobox-values">
    var vdiv = c.nextElementSibling;
    if (vdiv.classList.contains("combobox-values")) {
      createComboBox(c, function () {
        return Array.from(vdiv.children).map(function (value) {
          return value.getAttribute("value");
        });
      });
    }
  });

  // dropdownList.jelly
  Behaviour.specify(
    "SELECT.dropdownList",
    "select-dropdownlist",
    ++p,
    function (e) {
      if (isInsideRemovable(e)) {
        return;
      }

      var subForms = [];
      var start = findInFollowingTR(e, "dropdownList-container");

      do {
        start = start.firstElementChild;
      } while (start && !isTR(start));

      if (start && !start.classList.contains("dropdownList-start")) {
        start = findFollowingTR(start, "dropdownList-start");
      }
      while (start != null) {
        subForms.push(start);
        start = findFollowingTR(start, "dropdownList-start");
      }

      // control visibility
      function updateDropDownList() {
        for (var i = 0; i < subForms.length; i++) {
          var show = e.selectedIndex == i;
          var f = subForms[i];

          if (show) {
            renderOnDemand(f.nextElementSibling);
          }
          f.rowVisibilityGroup.makeInnerVisible(show);

          // TODO: this is actually incorrect in the general case if nested vg uses field-disabled
          // so far dropdownList doesn't create such a situation.
          f.rowVisibilityGroup.eachRow(
            true,
            show
              ? function (e) {
                  e.removeAttribute("field-disabled");
                }
              : function (e) {
                  e.setAttribute("field-disabled", "true");
                },
          );
        }
      }

      e.onchange = updateDropDownList;

      updateDropDownList();
    },
  );

  Behaviour.specify("A.showDetails", "a-showdetails", ++p, function (e) {
    e.onclick = function () {
      this.style.display = "none";
      this.nextElementSibling.style.display = "block";
      layoutUpdateCallback.call();
      return false;
    };
    e = null; // avoid memory leak
  });

  Behaviour.specify(
    "DIV.jenkins-form-skeleton, DIV.jenkins-side-panel-skeleton",
    "div-jenkins-form-skeleton",
    ++p,
    function (e) {
      e.remove();
    },
  );

  Behaviour.specify(
    "DIV.behavior-loading",
    "div-behavior-loading",
    ++p,
    function (e) {
      console.warn(
        ".behavior-loading is deprecated, use <l:skeleton /> instead - since TODO",
        e,
      );
      e.classList.add("behavior-loading--hidden");
    },
  );

  window.addEventListener("load", function () {
    // Add a class to the bottom bar when it's stuck to the bottom of the screen
    const el = document.querySelector(".jenkins-bottom-app-bar__shadow");
    if (el) {
      const observer = new IntersectionObserver(
        ([e]) =>
          e.target.classList.toggle(
            "jenkins-bottom-app-bar__shadow--stuck",
            e.intersectionRatio < 1,
          ),
        { threshold: [1] },
      );

      observer.observe(el);
    }
  });

  /**
   * Function that provides compatibility to the checkboxes without title on an f:entry
   *
   * When a checkbox is generated by setting the title on the f:entry like
   *     <f:entry field="rebaseBeforePush" title="${%Rebase Before Push}">
   *         <f:checkbox />
   *     </f:entry>
   * This function will copy the title from the .setting-name field to the checkbox label.
   * It will also move the help button.
   *
   * @param {HTMLLabelElement} label
   */
  Behaviour.specify(
    "label.js-checkbox-label-empty",
    "form-fallbacks",
    1000,
    function (label) {
      var labelParent = label.parentElement.parentElement;

      if (!labelParent.classList.contains("setting-main")) {
        return;
      }

      function findSettingName(formGroup) {
        for (var i = 0; i < formGroup.childNodes.length; i++) {
          var child = formGroup.childNodes[i];
          if (
            child.classList.contains("jenkins-form-label") ||
            child.classList.contains("setting-name")
          ) {
            return child;
          }
        }
      }

      var settingName = findSettingName(labelParent.parentNode);
      if (settingName == undefined) {
        return;
      }
      var jenkinsHelpButton = settingName.querySelector(".jenkins-help-button");
      var helpLink =
        jenkinsHelpButton !== null
          ? jenkinsHelpButton
          : settingName.querySelector(".setting-help");

      if (helpLink) {
        labelParent.classList.add("help-sibling");
        labelParent.classList.add("jenkins-checkbox-help-wrapper");
        labelParent.appendChild(helpLink);
      }

      labelParent.parentNode.removeChild(settingName);

      // Copy setting-name text and append it to the checkbox label
      var labelText = settingName.innerText;

      var spanTag = document.createElement("span");
      spanTag.innerHTML = labelText;
      label.appendChild(spanTag);
    },
  );
})();

var hudsonRules = {}; // legacy name
// now empty, but plugins can stuff things in here later:
Behaviour.register(hudsonRules);

var Path = {
  tail: function (p) {
    var idx = p.lastIndexOf("/");
    if (idx < 0) {
      return p;
    }
    return p.substring(idx + 1);
  },
};

/**
 * Install change handlers based on the 'fillDependsOn' attribute.
 */
// eslint-disable-next-line no-unused-vars
function refillOnChange(e, onChange) {
  var deps = [];

  function h() {
    var params = {};
    deps.forEach(
      TryEach(function (d) {
        params[d.name] = controlValue(d.control);
      }),
    );
    onChange(params);
  }
  var v = e.getAttribute("fillDependsOn");
  if (v != null) {
    v.split(" ").forEach(
      TryEach(function (name) {
        var c = findNearBy(e, name);
        if (c == null) {
          console.warn("Unable to find nearby " + name);
          return;
        }
        c.addEventListener("change", h);
        deps.push({ name: Path.tail(name), control: c });
      }),
    );
  }
  h(); // initial fill
}

function xor(a, b) {
  // convert both values to boolean by '!' and then do a!=b
  return !a != !b;
}

// used by editableDescription.jelly to replace the description field with a form
// eslint-disable-next-line no-unused-vars
function replaceDescription(initialDescription, submissionUrl) {
  const descriptionContent = document.getElementById("description-content");
  const descriptionEditForm = document.getElementById("description-edit-form");
  descriptionEditForm.innerHTML = "<div class='jenkins-spinner'></div>";
  descriptionContent.classList.add("jenkins-hidden");
  let parameters = {};
  if (initialDescription !== null && initialDescription !== "") {
    parameters["description"] = initialDescription;
  }
  if (submissionUrl !== null && submissionUrl !== "") {
    parameters["submissionUrl"] = submissionUrl;
  }
  fetch("./descriptionForm", {
    method: "post",
    headers: crumb.wrap({
      "Content-Type": "application/x-www-form-urlencoded",
    }),
    body: objectToUrlFormEncoded(parameters),
  }).then((rsp) => {
    rsp.text().then((responseText) => {
      descriptionEditForm.innerHTML = responseText;
      descriptionEditForm.classList.remove("jenkins-hidden");
      evalInnerHtmlScripts(responseText, function () {
        Behaviour.applySubtree(descriptionEditForm);
        descriptionEditForm.getElementsByTagName("TEXTAREA")[0].focus();
      });
      layoutUpdateCallback.call();
      return false;
    });
  });
}

/**
 * Indicates that form fields from rows [s,e) should be grouped into a JSON object,
 * and attached under the element identified by the specified id.
 */
function applyNameRef(s, e, id) {
  document.getElementById(id).groupingNode = true;
  // s contains the node itself
  applyNameRefHelper(s, e, id);
}

function applyNameRefHelper(s, e, id) {
  if (s === null) {
    return;
  }
  for (var x = s.nextElementSibling; x != e; x = x.nextElementSibling) {
    // to handle nested <f:rowSet> correctly, don't overwrite the existing value
    if (x.getAttribute("nameRef") == null) {
      x.setAttribute("nameRef", id);
      if (x.classList.contains("tr")) {
        applyNameRefHelper(x.firstElementChild, null, id);
      }
    }
  }
}

// used by optionalBlock.jelly to update the form status
//   @param c     checkbox element
function updateOptionalBlock(c) {
  // find the start TR
  var s = c;
  while (!s.classList.contains("optional-block-start")) {
    s = s.parentNode;
  }

  // find the beginning of the rowvg
  var vg = s;
  while (!vg.classList.contains("rowvg-start")) {
    vg = vg.nextElementSibling;
  }

  var checked = xor(c.checked, c.classList.contains("negative"));

  vg.rowVisibilityGroup.makeInnerVisible(checked);

  if (c.name == "hudson-tools-InstallSourceProperty") {
    // Hack to hide tool home when "Install automatically" is checked.
    var homeField = findPreviousFormItem(c, "home");
    if (homeField != null && homeField.value == "") {
      const formItem = homeField.closest(".jenkins-form-item");
      if (formItem != null) {
        formItem.style.display = c.checked ? "none" : "";
        layoutUpdateCallback.call();
      }
    }
  }
}

//
// Auto-scroll support for progressive log output.
//   See http://radio.javaranch.com/pascarello/2006/08/17/1155837038219.html
//
// eslint-disable-next-line no-unused-vars
function AutoScroller(scrollContainer) {
  // get the height of the viewport.
  // See http://www.howtocreate.co.uk/tutorials/javascript/browserwindow
  function getViewportHeight() {
    if (typeof window.innerWidth == "number") {
      //Non-IE
      return window.innerHeight;
    } else if (
      document.documentElement &&
      (document.documentElement.clientWidth ||
        document.documentElement.clientHeight)
    ) {
      //IE 6+ in 'standards compliant mode'
      return document.documentElement.clientHeight;
    } else if (
      document.body &&
      (document.body.clientWidth || document.body.clientHeight)
    ) {
      //IE 4 compatible
      return document.body.clientHeight;
    }
    return null;
  }

  return {
    bottomThreshold: 25,
    scrollContainer: scrollContainer,

    getCurrentHeight: function () {
      var scrollDiv = this.scrollContainer;

      if (scrollDiv.scrollHeight > 0) {
        return scrollDiv.scrollHeight;
      } else if (scrollDiv.offsetHeight > 0) {
        return scrollDiv.offsetHeight;
      }

      return null; // huh?
    },

    // return true if we are in the "stick to bottom" mode
    isSticking: function () {
      var scrollDiv = this.scrollContainer;
      var currentHeight = this.getCurrentHeight();

      // when used with the BODY tag, the height needs to be the viewport height, instead of
      // the element height.
      //var height = ((scrollDiv.style.pixelHeight) ? scrollDiv.style.pixelHeight : scrollDiv.offsetHeight);
      var height = getViewportHeight();
      var scrollPos = Math.max(
        scrollDiv.scrollTop,
        document.documentElement.scrollTop,
      );
      var diff = currentHeight - scrollPos - height;
      // window.alert("currentHeight=" + currentHeight + ",scrollTop=" + scrollDiv.scrollTop + ",height=" + height);

      return diff < this.bottomThreshold;
    },

    scrollToBottom: function () {
      var scrollDiv = this.scrollContainer;
      var currentHeight = this.getCurrentHeight();

      if (scrollDiv === document.body) {
        window.scrollTo({
          top: currentHeight,
        });
      } else {
        scrollDiv.scrollTop = currentHeight;
      }
    },
  };
}

// refresh a part of the HTML specified by the given ID,
// by using the contents fetched from the given URL.
// eslint-disable-next-line no-unused-vars
function refreshPart(id, url) {
  var intervalID = null;
  var f = function () {
    if (isPageVisible()) {
      fetch(url, {
        headers: crumb.wrap({}),
        method: "post",
      }).then((rsp) => {
        if (rsp.ok) {
          rsp.text().then((responseText) => {
            var hist = document.getElementById(id);
            if (hist == null) {
              console.log("There's no element that has ID of " + id);
              if (intervalID !== null) {
                window.clearInterval(intervalID);
              }
              return;
            }
            if (!responseText) {
              console.log(
                "Failed to retrieve response for ID " +
                  id +
                  ", perhaps Jenkins is unavailable",
              );
              return;
            }
            var p = hist.parentNode;

            var div = document.createElement("div");
            div.innerHTML = responseText;

            var node = div.firstElementChild;
            p.replaceChild(node, hist);

            Behaviour.applySubtree(node);
            layoutUpdateCallback.call();
          });
        }
      });
    }
  };
  // if run as test, just do it once and do it now to make sure it's working,
  // but don't repeat.
  if (isRunAsTest) {
    f();
  } else {
    intervalID = window.setInterval(f, 5000);
  }
}

/*
    Perform URL encode.
    Taken from http://www.cresc.co.jp/tech/java/URLencoding/JavaScript_URLEncoding.htm
    @deprecated Use standard javascript method "encodeURIComponent" instead
*/
// eslint-disable-next-line no-unused-vars
function encode(str) {
  var s, u;
  var s0 = ""; // encoded str

  for (var i = 0; i < str.length; i++) {
    // scan the source
    s = str.charAt(i);
    u = str.charCodeAt(i); // get unicode of the char

    if (s == " ") {
      s0 += "+";
    } // SP should be converted to "+"
    else {
      if (
        u == 0x2a ||
        u == 0x2d ||
        u == 0x2e ||
        u == 0x5f ||
        (u >= 0x30 && u <= 0x39) ||
        (u >= 0x41 && u <= 0x5a) ||
        (u >= 0x61 && u <= 0x7a)
      ) {
        // check for escape
        s0 = s0 + s; // don't escape
      } else {
        // escape
        if (u >= 0x0 && u <= 0x7f) {
          // single byte format
          s = "0" + u.toString(16);
          s0 += "%" + s.substr(s.length - 2);
        } else if (u > 0x1fffff) {
          // quaternary byte format (extended)
          s0 += "%" + (0xf0 + ((u & 0x1c0000) >> 18)).toString(16);
          s0 += "%" + (0x80 + ((u & 0x3f000) >> 12)).toString(16);
          s0 += "%" + (0x80 + ((u & 0xfc0) >> 6)).toString(16);
          s0 += "%" + (0x80 + (u & 0x3f)).toString(16);
        } else if (u > 0x7ff) {
          // triple byte format
          s0 += "%" + (0xe0 + ((u & 0xf000) >> 12)).toString(16);
          s0 += "%" + (0x80 + ((u & 0xfc0) >> 6)).toString(16);
          s0 += "%" + (0x80 + (u & 0x3f)).toString(16);
        } else {
          // double byte format
          s0 += "%" + (0xc0 + ((u & 0x7c0) >> 6)).toString(16);
          s0 += "%" + (0x80 + (u & 0x3f)).toString(16);
        }
      }
    }
  }
  return s0;
}

// when there are multiple form elements of the same name,
// this method returns the input field of the given name that pairs up
// with the specified 'base' input element.
// eslint-disable-next-line no-unused-vars
function findMatchingFormInput(base, name) {
  // find the FORM element that owns us
  var f = base.closest("form");

  var bases = f.querySelectorAll(
    'input[name="' +
      base.name +
      '"], textarea[name="' +
      base.name +
      '"], select[name="' +
      base.name +
      '"]',
  );
  var targets = f.querySelectorAll(
    'input[name="' +
      name +
      '"], textarea[name="' +
      name +
      '"], select[name="' +
      name +
      '"]',
  );

  for (var i = 0; i < bases.length; i++) {
    if (bases[i] == base) {
      return targets[i];
    }
  }

  return null; // not found
}

// eslint-disable-next-line no-unused-vars
function toQueryString(params) {
  var query = "";
  if (params) {
    for (var paramName in params) {
      if (Object.prototype.hasOwnProperty.call(params, paramName)) {
        if (query === "") {
          query = "?";
        } else {
          query += "&";
        }
        query += paramName + "=" + encodeURIComponent(params[paramName]);
      }
    }
  }
  return query;
}

// get the cascaded computed style value. 'a' is the style name like 'backgroundColor'
// eslint-disable-next-line no-unused-vars
function getStyle(e, a) {
  if (document.defaultView && document.defaultView.getComputedStyle) {
    return document.defaultView
      .getComputedStyle(e, null)
      .getPropertyValue(a.replace(/([A-Z])/g, "-$1"));
  }
  if (e.currentStyle) {
    return e.currentStyle[a];
  }
  return null;
}

/**
 * Makes sure the given element is within the viewport.
 *
 * @param {HTMLElement} e
 *      The element to bring into the viewport.
 */
// eslint-disable-next-line no-unused-vars
function ensureVisible(e) {
  const scrollTop = document.documentElement.scrollTop;
  let Y = scrollTop;
  let H = window.innerHeight;
  let c = 0;

  function handleStickers(name, f) {
    const e = document.getElementById(name);
    if (e) {
      f(e);
    }
    document.getElementsBySelector("." + name).forEach(TryEach(f));
  }

  // if there are any stickers around, subtract them from the viewport
  handleStickers("jenkins-breadcrumbs", function (t) {
    t = t.clientHeight;
    Y += t;
    H -= t;
    c += t;
  });

  handleStickers("bottom-sticker", function (b) {
    b = b.clientHeight;
    H -= b;
  });

  const box = e.getBoundingClientRect();
  const y = Math.round(box.top + scrollTop);
  const h = e.offsetHeight;
  const d = y + h - (Y + H);

  if (h > H) {
    document.documentElement.scrollTop = y - c;
  } else if (d > 0) {
    document.documentElement.scrollTop += d;
  }
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
function findFormParent(e, form, isStatic) {
  isStatic = isStatic || false;

  if (form == null) {
    // caller can pass in null to have this method compute the owning form
    form = e.closest("FORM");
  }

  while (e != form) {
    // this is used to create a group where no single containing parent node exists,
    // like <optionalBlock>
    var nameRef = e.getAttribute("nameRef");
    if (nameRef != null) {
      e = document.getElementById(nameRef);
    } else {
      e = e.parentNode;
    }

    if (!isStatic && e.getAttribute("field-disabled") != null) {
      // this field shouldn't contribute to the final result
      return null;
    }

    var name = e.getAttribute("name");
    if (name != null && name.length > 0) {
      if (
        e.tagName == "INPUT" &&
        !isStatic &&
        !xor(e.checked, e.classList.contains("negative"))
      ) {
        // field is not active
        return null;
      }

      return e;
    }
  }

  return form;
}

// compute the form field name from the control name
function shortenName(name) {
  // [abc.def.ghi] -> abc.def.ghi
  if (name.startsWith("[")) {
    return name.substring(1, name.length - 1);
  }

  // abc.def.ghi -> ghi
  var idx = name.lastIndexOf(".");
  if (idx >= 0) {
    name = name.substring(idx + 1);
  }
  return name;
}

//
// structured form submission handling
//   see https://www.jenkins.io/redirect/developer/structured-form-submission
function buildFormTree(form) {
  try {
    // I initially tried to use an associative array with DOM elements as keys
    // but that doesn't seem to work neither on IE nor Firefox.
    // so I switch back to adding a dynamic property on DOM.
    form.formDom = {}; // root object

    var doms = []; // DOMs that we added 'formDom' for.
    doms.push(form);

    let addProperty = function (parent, name, value) {
      name = shortenName(name);
      if (parent[name] != null) {
        if (parent[name].push == null) {
          // is this array?
          parent[name] = [parent[name]];
        }
        parent[name].push(value);
      } else {
        parent[name] = value;
      }
    };

    // find the grouping parent node, which will have @name.
    // then return the corresponding object in the map
    let findParent = function (e) {
      var p = findFormParent(e, form);
      if (p == null) {
        return {};
      }

      var m = p.formDom;
      if (m == null) {
        // this is a new grouping node
        doms.push(p);
        p.formDom = m = {};
        addProperty(findParent(p), p.getAttribute("name"), m);
      }
      return m;
    };

    var jsonElement = null;

    for (var i = 0; i < form.elements.length; i++) {
      var e = form.elements[i];
      if (e.name == "json") {
        jsonElement = e;
        continue;
      }
      if (e.tagName == "FIELDSET") {
        continue;
      }
      if (e.tagName == "SELECT" && e.multiple) {
        var values = [];
        for (var o = 0; o < e.options.length; o++) {
          var opt = e.options.item(o);
          if (opt.selected) {
            values.push(opt.value);
          }
        }
        addProperty(findParent(e), e.name, values);
        continue;
      }

      var p;
      var r;
      var type = e.getAttribute("type");
      if (type == null) {
        type = "";
      }
      switch (type.toLowerCase()) {
        case "button":
        case "submit":
          break;
        case "checkbox":
          p = findParent(e);
          var checked = xor(e.checked, e.classList.contains("negative"));
          if (!e.groupingNode) {
            let v = e.getAttribute("json");
            if (v) {
              // if the special attribute is present, we'll either set the value or not. useful for an array of checkboxes
              // we can't use @value because IE6 sets the value to be "on" if it's left unspecified.
              if (checked) {
                addProperty(p, e.name, v);
              }
            } else {
              // otherwise it'll bind to boolean
              addProperty(p, e.name, checked);
            }
          } else {
            if (checked) {
              addProperty(p, e.name, (e.formDom = {}));
            }
          }
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
              addProperty(p, on, e.name);
            } else {
              var uniqName = "file" + iota++;
              addProperty(p, e.name, uniqName);
              e.setAttribute("originalName", e.name);
              e.name = uniqName;
            }
          }
          // switch to multipart/form-data to support file submission
          // @enctype is the standard, but IE needs @encoding.
          form.enctype = form.encoding = "multipart/form-data";
          crumb.appendToForm(form);
          break;
        case "radio":
          if (!e.checked) {
            break;
          }
          r = 0;
          while (e.name.substring(r, r + 8) == "removeme") {
            r = e.name.indexOf("_", r + 8) + 1;
          }
          p = findParent(e);
          if (e.groupingNode) {
            addProperty(
              p,
              e.name.substring(r),
              (e.formDom = { value: e.value }),
            );
          } else {
            addProperty(p, e.name.substring(r), e.value);
          }
          break;
        case "password":
          p = findParent(e);
          addProperty(p, e.name, e.value);
          // must be kept in sync with RedactSecretJsonForTraceSanitizer.REDACT_KEY
          addProperty(p, "$redact", shortenName(e.name));
          break;
        default:
          p = findParent(e);
          addProperty(p, e.name, e.value);
          if (e.classList.contains("complex-password-field")) {
            addProperty(p, "$redact", shortenName(e.name));
          }
          if (e.classList.contains("secretTextarea-redact")) {
            addProperty(p, "$redact", shortenName(e.name));
          }
          break;
      }
    }

    jsonElement.value = JSON.stringify(form.formDom);

    // clean up
    for (i = 0; i < doms.length; i++) {
      doms[i].formDom = null;
    }

    return true;
  } catch (e) {
    alert(e + "\n(form not submitted)");
    return false;
  }
}

// Decrease vertical padding for checkboxes
window.addEventListener("load", function () {
  document.querySelectorAll(".jenkins-form-item").forEach(function (element) {
    if (
      element.querySelector(
        ".optionalBlock-container > .row-group-start input[type='checkbox'], .optional-block-start input[type='checkbox'], div > .jenkins-checkbox",
      ) != null
    ) {
      element.classList.add("jenkins-form-item--tight");
    }
  });
});

/**
 * Loads the script specified by the URL.
 *
 * @param href
 *      The URL of the script to load.
 * @param callback
 *      If specified, this function will be invoked after the script is loaded.
 * @see http://stackoverflow.com/questions/4845762/onload-handler-for-script-tag-in-internet-explorer
 */
function loadScript(href, callback) {
  var head =
    document.getElementsByTagName("head")[0] || document.documentElement;
  var script = document.createElement("script");
  script.src = href;

  if (callback) {
    // Handle Script loading
    var done = false;

    // Attach handlers for all browsers
    script.onload = script.onreadystatechange = function () {
      if (
        !done &&
        (!this.readyState ||
          this.readyState === "loaded" ||
          this.readyState === "complete")
      ) {
        done = true;
        callback();

        // Handle memory leak in IE
        script.onload = script.onreadystatechange = null;
        if (head && script.parentNode) {
          head.removeChild(script);
        }
      }
    };
  }

  // Use insertBefore instead of appendChild  to circumvent an IE6 bug.
  // This arises when a base node is used (#2709 and #4378).
  head.insertBefore(script, head.firstElementChild);
}

// logic behind <f:validateButton />
// eslint-disable-next-line no-unused-vars
function safeValidateButton(button) {
  var descriptorUrl = button.getAttribute(
    "data-validate-button-descriptor-url",
  );
  var method = button.getAttribute("data-validate-button-method");
  var checkUrl = descriptorUrl + "/" + method;

  // optional, by default = empty string
  var paramList = button.getAttribute("data-validate-button-with") || "";

  validateButton(checkUrl, paramList, button);
}

// this method should not be called directly, only get called by safeValidateButton
// kept "public" for legacy compatibility
function validateButton(checkUrl, paramList, button) {
  var parameters = {};

  paramList.split(",").forEach(function (name) {
    var p = findPreviousFormItem(button, name);
    if (p != null) {
      if (p.type === "checkbox") {
        parameters[name] = p.checked;
      } else if (p.type === "radio") {
        while (p && !p.checked) {
          p = findPreviousFormItem(p, name);
        }
        parameters[name] = p.value;
      } else {
        parameters[name] = p.value;
      }
    }
  });

  var spinner = button.closest("DIV").children[0];
  var target = spinner.nextElementSibling.nextElementSibling;
  spinner.style.display = "block";

  fetch(checkUrl, {
    method: "post",
    body: objectToUrlFormEncoded(parameters),
    headers: crumb.wrap({
      "Content-Type": "application/x-www-form-urlencoded",
    }),
  }).then((rsp) => {
    rsp.text().then((responseText) => {
      spinner.style.display = "none";
      target.innerHTML = `<div class="validation-error-area" />`;
      updateValidationArea(target.children[0], responseText);
      layoutUpdateCallback.call();
      var s = rsp.headers.get("script");
      try {
        geval(s);
      } catch (e) {
        window.alert("failed to evaluate " + s + "\n" + e.message);
      }
    });
  });
}

// create a combobox.
// @param idOrField
//      ID of the <input type=text> element that becomes a combobox, or the field itself.
//      Passing an ID is @deprecated since 1.350; use <input class="combobox"/> instead.
// @param valueFunction
//      Function that returns all the candidates as an array
function createComboBox(idOrField, valueFunction) {
  var candidates = valueFunction();
  var creator = function () {
    if (typeof idOrField == "string") {
      idOrField = document.getElementById(idOrField);
    }
    if (!idOrField) {
      return;
    }
    new ComboBox(idOrField, function (value /*, comboBox*/) {
      var items = new Array();
      if (value.length > 0) {
        // if no value, we'll not provide anything
        value = value.toLowerCase();
        for (var i = 0; i < candidates.length; i++) {
          if (candidates[i].toLowerCase().indexOf(value) >= 0) {
            items.push(candidates[i]);
            if (items.length > 20) {
              // 20 items in the list should be enough
              break;
            }
          }
        }
      }
      return items; // equiv to: comboBox.setItems(items);
    });
  };
  // If an ID given, create when page has loaded (backward compatibility); otherwise now.
  if (typeof idOrField == "string") {
    Behaviour.addLoadEvent(creator);
  } else {
    creator();
  }
}

// event callback when layouts/visibility are updated and elements might have moved around
var layoutUpdateCallback = {
  callbacks: [],
  add: function (f) {
    this.callbacks.push(f);
  },
  call: function () {
    for (var i = 0, length = this.callbacks.length; i < length; i++) {
      this.callbacks[i]();
    }
  },
};
