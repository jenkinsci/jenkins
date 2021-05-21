(function() {
  // function to toggle the enable/disable state
  function flip(o) {
    btn = Event.element(o);

    // trigger
    new Ajax.Request(btn.getAttribute('url')+"/make"+(btn.checked?"Enable":"Disable")+"d", {
      method: "POST",
      onFailure : function(req,o) {
        $('needRestart').innerHTML = req.responseText;
      }
    });

    updateMsg();
  }

  function updateMsg() {
    // is anything changed since its original state?
    var e = $A(Form.getInputs('plugins', 'checkbox')).find(function(e) {
      return String(e.checked) != e.getAttribute('original');
    });

    if ("true" === document.getElementById('installed-adjunct-data').getAttribute('data-restart-required')) {
      e = true;
    }
    $('needRestart').style.display = (e != null ? "block" : "none");
  }

  updateMsg(); // set the initial state


  Behaviour.specify(".installed-flip", 'installed-adjunct', 0, function(e) {
    e.onclick = function(e) {
      flip(e);
    }
  });

})();
