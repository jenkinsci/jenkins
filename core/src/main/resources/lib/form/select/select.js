// send async request to the given URL (which will send back serialized ListBoxModel object),
// then use the result to fill the list box.
function updateListBox(listBox, url, config) {
  config = config || {};
  config = object(config);
  var originalOnSuccess = config.onSuccess;
  var l = listBox;

  // Hacky function to retrofit compatibility with tables-to-divs
  // If the <select> tag parent is a <td> element we can consider it's following a
  // form entry using tables-to-divs markup.
  function getStatusElement() {
    function getStatusForTabularForms() {
      return listBox.parentNode.querySelector(".validation-error-area");
    }
    function getStatusForDivBasedForms() {
      var settingMain = listBox.closest(".setting-main");
      if (!settingMain) {
        console.warn(
          "Couldn't find the expected validation element (.validation-error-area) for element",
          listBox.parentNode,
        );
        return;
      }

      return settingMain.nextElementSibling;
    }

    return listBox.parentNode.tagName === "TD"
      ? getStatusForTabularForms()
      : getStatusForDivBasedForms();
  }

  var status = getStatusElement();
  if (!status) {
    console.warn("Couldn't find the expected status element");
    return;
  }
  if (
    status.firstElementChild &&
    status.firstElementChild.getAttribute("data-select-ajax-error")
  ) {
    status.innerHTML = "";
  }
  config.onSuccess = function (rsp) {
    rsp.json().then((result) => {
      l.classList.remove("select-ajax-pending");
      var currentSelection = l.value;

      // clear the contents
      while (l.length > 0) {
        l.options[0] = null;
      }

      var selectionSet = false; // is the selection forced by the server?
      var possibleIndex = null; // if there's a new option that matches the current value, remember its index

      var opts = result.values;
      for (var i = 0; i < opts.length; i++) {
        l.options[i] = new Option(opts[i].name, opts[i].value);
        if (opts[i].selected) {
          l.selectedIndex = i;
          selectionSet = true;
        }
        if (opts[i].value === currentSelection) {
          possibleIndex = i;
        }
      }

      // if no value is explicitly selected by the server, try to select the same value
      if (!selectionSet && possibleIndex != null) {
        l.selectedIndex = possibleIndex;
      }

      if (originalOnSuccess !== undefined) {
        originalOnSuccess(rsp);
      }
    });
  };
  config.onFailure = function (rsp) {
    rsp.text().then((responseText) => {
      l.classList.remove("select-ajax-pending");
      status.innerHTML = responseText;
      if (status.firstElementChild) {
        status.firstElementChild.setAttribute("data-select-ajax-error", "true");
      }
      Behaviour.applySubtree(status);
      // deleting values can result in the data loss, so let's not do that unless instructed
      var header = rsp.headers.get("X-Jenkins-Select-Error");
      if (header && "clear" === header.toLowerCase()) {
        // clear the contents
        while (l.length > 0) {
          l.options[0] = null;
        }
      }
    });
  };

  l.classList.add("select-ajax-pending");
  fetch(url, {
    method: "post",
    headers: crumb.wrap({
      "Content-Type": "application/x-www-form-urlencoded",
    }),
    body: objectToUrlFormEncoded(config.parameters),
  }).then((response) => {
    if (response.ok) {
      config.onSuccess(response);
    } else {
      config.onFailure(response);
    }
  });
}

Behaviour.specify("SELECT.select", "select", 1000, function (e) {
  function hasChanged(selectEl, originalValue) {
    // seems like a race condition allows this to fire before the 'selectEl' is defined. If that happens, exit..
    if (!selectEl || !selectEl.options || !selectEl.options.length > 0) {
      return false;
    }
    var firstValue = selectEl.options[0].value;
    var selectedValue = selectEl.value;
    if (originalValue == "" && selectedValue == firstValue) {
      // There was no value pre-selected but after the call to updateListBox the first value is selected by
      // default. This must not be considered a change.
      return false;
    } else {
      return originalValue != selectedValue;
    }
  }

  let parentDiv = e.closest(".jenkins-select");

  function handleFilled(event) {
    // ignore events for other elements
    if (event.detail === e) {
      let pre = document.createElement("pre");
      if (e.selectedIndex != -1) {
        pre.innerText = e.options[e.selectedIndex].text;
      } else {
        pre.innerText = "N/A";
      }
      e.remove();
      pre.classList.add("jenkins-readonly");
      parentDiv.classList.remove("jenkins-select");
      parentDiv.appendChild(pre);
    }
  }

  // handle readonly mode, the actually selected option is only filled asynchronously so we have
  // to wait until the data is filled by registering to the filled event.
  if (
    parentDiv != null &&
    parentDiv.dataset.readonly === "true" &&
    !parentDiv.hasAttribute("data-listener-added")
  ) {
    // need to avoid duplicate eventListeners so mark that we already added it
    parentDiv.setAttribute("data-listener-added", "true");
    parentDiv.addEventListener("filled", handleFilled);
  }

  // controls that this SELECT box depends on
  refillOnChange(e, function (params) {
    var value = e.value;
    updateListBox(e, e.getAttribute("fillUrl"), {
      parameters: params,
      onSuccess: function () {
        if (value == "") {
          // reflect the initial value. if the control depends on several other SELECT.select,
          // it may take several updates before we get the right items, which is why all these precautions.
          var v = e.getAttribute("value");
          if (v) {
            e.value = v;
            // we were able to apply our initial value
            if (e.value == v) {
              e.removeAttribute("value");
            }
          }
        }

        fireEvent(e, "filled"); // let other interested parties know that the items have changed

        // if the update changed the current selection, others listening to this control needs to be notified.
        if (hasChanged(e, value)) {
          fireEvent(e, "change");
        }
      },
    });
  });
});
