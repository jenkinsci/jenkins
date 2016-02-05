(function() {
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

    function isModifyingButton(btn) {
      // TODO don't consider hetero list 'add' buttons
      // needs to handle the yui menus instead
      // if (btn.classList.contains("hetero-list-add")) {
      //   return false;
      // }

      if (btn.parentNode.parentNode.classList.contains("advanced-button")) {
        // don't consider 'advanced' buttons
        return false;
      }

      // default to true
      return true;
    }

    function initConfirm() {
      var configForm = document.getElementsByName("config");
      if (configForm.length > 0) {
        configForm = configForm[0]
      } else {
        configForm = document.getElementsByName("viewConfig")[0];
      }

      YAHOO.util.Event.on($(configForm), "submit", clearConfirm, this); 

      var buttons = configForm.getElementsByTagName("button");
      var name;
      for ( var i = 0; i < buttons.length; i++) {
        name = buttons[i].parentNode.parentNode.getAttribute('name');
        if (name == "Submit" || name == "Apply" || name == "OK") {
          $(buttons[i]).on('click', function() {
            needToConfirm = false;
          });
        } else {
          if (isModifyingButton(buttons[i])) {
            $(buttons[i]).on('click', confirm);
          }
        }
      }

      var inputs = configForm.getElementsByTagName("input");
      for ( var i = 0; i < inputs.length; i++) {
        if (inputs[i].type == 'checkbox' || inputs[i].type == 'radio') {
          $(inputs[i]).on('click', confirm);
        } else {
          $(inputs[i]).on('input', confirm);
        }
      }

      inputs = configForm.getElementsByTagName("select");
      for ( var i = 0; i < inputs.length; i++) {
        $(inputs[i]).on('change', confirm);
      }

      inputs = configForm.getElementsByTagName("textarea");
      for ( var i = 0; i < inputs.length; i++) {
        $(inputs[i]).on('input', confirm);
      }
    }

    window.onbeforeunload = confirmExit;
    Event.on(window,'load', initConfirm);

})();
