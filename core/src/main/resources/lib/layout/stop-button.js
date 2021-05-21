Behaviour.specify(".stop-button-link", 'stop-button', 0, function(e) {
  var url = e.getAttribute('href');
  var confirmMessage = e.getAttribute('data-confirm');
  e.onclick = function() {
    if (!confirmMessage || confirm(confirmMessage)) {
      new Ajax.Request(url);
    }
    return false;
  };
});
