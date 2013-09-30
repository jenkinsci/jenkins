var errorMessage = "Leave without saving?";
var needToConfirm = false;

window.onbeforeunload = confirmExit;
Event.observe(window, 'load', initConfirm);

function initConfirm() {
  var configForm = document.getElementsByName("config")[0];

  var buttons = configForm.getElementsByTagName("button");
  var name;
  for ( var i = 0; i < buttons.length; i++) {
    name = buttons[i].parentNode.parentNode.getAttribute('name');
    if (name == "Submit") {
      addEventToElement(buttons[i], 'click', function() {
        needToConfirm = false;
      });
    } else if (name == "Apply") {
    } else {
      addEventToElement(buttons[i], 'click', confirm);
    }
  }

  var inputs = configForm.getElementsByTagName("input");
  for ( var i = 0; i < inputs.length; i++) {
    addEventToElement(inputs[i], 'change', confirm);
    if (inputs[i].type == 'checkbox' || inputs[i].type == 'radio') {
      addEventToElement(inputs[i], 'click', confirm);
    } else {
      addEventToElement(inputs[i], 'keydown', confirm);
    }
  }

  inputs = configForm.getElementsByTagName("select");
  for ( var i = 0; i < inputs.length; i++) {
    addEventToElement(inputs[i], 'keydown', confirm);
    addEventToElement(inputs[i], 'click', confirm);
  }

  inputs = configForm.getElementsByTagName("textarea");
  for ( var i = 0; i < inputs.length; i++) {
    addEventToElement(inputs[i], 'keydown', confirm);
  }
}

function confirm() {
  needToConfirm = true;
}

function addEventToElement(obj, evType, fn) {
  if (obj.addEventListener) {
    obj.addEventListener(evType, fn, false);
    return true;
  } else if (obj.attachEvent) {
    return obj.attachEvent("on" + evType, fn);
  }
}

function confirmExit() {
  if (needToConfirm) {
    return errorMessage;
  }
}
