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
    
    function isIgnoringConfirm(element){
      if(element.hasClassName('force-dirty')){
        return false;
      }
      if(element.hasClassName('ignore-dirty')){
        return true;
      }
      // to allow sub-section of the form to ignore confirm
      // especially useful for "pure" JavaScript area
      // we try to gather the first parent with a marker, 
      var dirtyPanel = element.up('.ignore-dirty-panel,.force-dirty-panel');
      if(!dirtyPanel){
        return false;
      }
      
      if(dirtyPanel.hasClassName('force-dirty-panel')){
        return false;
      }
      if(dirtyPanel.hasClassName('ignore-dirty-panel')){
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

      if (btn.parentNode.parentNode.classList.contains("advanced-button")) {
        // don't consider 'advanced' buttons
        return false;
      }
      
      if(isIgnoringConfirm(btn)){
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
        var button = buttons[i];
        name = button.parentNode.parentNode.getAttribute('name');
        if (name == "Submit" || name == "Apply" || name == "OK") {
          $(button).on('click', function() {
            needToConfirm = false;
          });
        } else {
          if (isModifyingButton(button)) {
            $(button).on('click', confirm);
          }
        }
      }

      var inputs = configForm.getElementsByTagName("input");
      for ( var i = 0; i < inputs.length; i++) {
        var input = inputs[i];
        if(!isIgnoringConfirm(input)){
          if (input.type == 'checkbox' || input.type == 'radio') {
            $(input).on('click', confirm);
          } else {
            $(input).on('input', confirm);
          }
        }
      }

      inputs = configForm.getElementsByTagName("select");
      for ( var i = 0; i < inputs.length; i++) {
        var input = inputs[i];
        if(!isIgnoringConfirm(input)){
          $(input).on('change', confirm);
        }
      }

      inputs = configForm.getElementsByTagName("textarea");
      for ( var i = 0; i < inputs.length; i++) {
        var input = inputs[i];
        if(!isIgnoringConfirm(input)){
          $(input).on('input', confirm);
        }
      }
    }

    window.onbeforeunload = confirmExit;
    Event.on(window,'load', initConfirm);

})();
