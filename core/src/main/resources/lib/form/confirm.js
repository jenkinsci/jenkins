(function() {
    var errorMessage = "You have modified configuration";
    var needToConfirm = false;

    function confirm() {
      needToConfirm = true;
    }

    function confirmExit() {
      if (needToConfirm) {
        return errorMessage;
      }
    }

    function initConfirm() {
      var configForm = document.getElementsByName("config")[0];

      var buttons = configForm.getElementsByTagName("button");
      var name;
      for ( var i = 0; i < buttons.length; i++) {
        name = buttons[i].parentNode.parentNode.getAttribute('name');
        if (name == "Submit" || name == "Apply") {
          $(buttons[i]).on('click', function() {
            needToConfirm = false;
          });
        } else {
          $(buttons[i]).on('click', confirm);
        }
      }

      var inputs = configForm.getElementsByTagName("input");
      for ( var i = 0; i < inputs.length; i++) {
        $(inputs[i]).on('change', confirm);
        if (inputs[i].type == 'checkbox' || inputs[i].type == 'radio') {
          $(inputs[i]).on('click', confirm);
        } else {
          $(inputs[i]).on('keydown', confirm);
        }
      }

      inputs = configForm.getElementsByTagName("select");
      for ( var i = 0; i < inputs.length; i++) {
        $(inputs[i]).on('keydown', confirm);
        $(inputs[i]).on('click', confirm);
      }

      inputs = configForm.getElementsByTagName("textarea");
      for ( var i = 0; i < inputs.length; i++) {
        $(inputs[i]).on('keydown', confirm);
      }
    }

    window.onbeforeunload = confirmExit;
    Event.on(window,'load', initConfirm);

})();
