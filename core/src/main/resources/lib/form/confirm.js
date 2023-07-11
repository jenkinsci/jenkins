(function () {
  var errorMessage = "You have modified configuration";
  var needToConfirm = false;

  function confirm() {
    needToConfirm = true;
  }

  function clearConfirm() {
    needToConfirm = false;
  }

  function confirmExit() {
    if (needToConfirm) {
      return errorMessage;
    }
  }

  function isIgnoringConfirm(element) {
    if (element.classList.contains("force-dirty")) {
      return false;
    }
    if (element.classList.contains("ignore-dirty")) {
      return true;
    }
    // to allow sub-section of the form to ignore confirm
    // especially useful for "pure" JavaScript area
    // we try to gather the first parent with a marker,
    var dirtyPanel = element.closest(".ignore-dirty-panel,.force-dirty-panel");
    if (!dirtyPanel) {
      return false;
    }

    if (dirtyPanel.classList.contains("force-dirty-panel")) {
      return false;
    }
    if (dirtyPanel.classList.contains("ignore-dirty-panel")) {
      return true;
    }

    return false;
  }

  function isModifyingButton(btn) {
    // TODO don't consider hetero list 'add' buttons
    // needs to handle the yui menus instead
    // if (btn.classList.contains("hetero-list-add")) {
    //   return false;
    // }

    if (btn.classList.contains("advanced-button")) {
      // don't consider 'advanced' buttons
      return false;
    }

    if (isIgnoringConfirm(btn)) {
      return false;
    }

    // default to true
    return true;
  }

  function initConfirm() {
    var configForm = document.getElementsByName("config");
    if (configForm.length > 0) {
      configForm = configForm[0];
    } else {
      configForm = document.getElementsByName("viewConfig")[0];
    }

    configForm.addEventListener("submit", clearConfirm);

    var buttons = configForm.getElementsByTagName("button");
    var name;
    for (let i = 0; i < buttons.length; i++) {
      var button = buttons[i];
      name = button.getAttribute("name");
      if (name == "Submit" || name == "Apply" || name == "OK") {
        button.addEventListener("click", function () {
          needToConfirm = false;
        });
      } else {
        if (isModifyingButton(button)) {
          button.addEventListener("click", confirm);
        }
      }
    }

    var inputs = configForm.getElementsByTagName("input");
    for (let i = 0; i < inputs.length; i++) {
      var input = inputs[i];
      if (!isIgnoringConfirm(input)) {
        if (input.type == "checkbox" || input.type == "radio") {
          input.addEventListener("click", confirm);
        } else {
          input.addEventListener("input", confirm);
        }
      }
    }

    inputs = configForm.getElementsByTagName("select");
    for (let i = 0; i < inputs.length; i++) {
      let input = inputs[i];
      if (!isIgnoringConfirm(input)) {
        input.addEventListener("change", confirm);
      }
    }

    inputs = configForm.getElementsByTagName("textarea");
    for (let i = 0; i < inputs.length; i++) {
      let input = inputs[i];
      if (!isIgnoringConfirm(input)) {
        input.addEventListener("input", confirm);
      }
    }
  }

  window.onbeforeunload = confirmExit;
  window.addEventListener("load", initConfirm);
})();
