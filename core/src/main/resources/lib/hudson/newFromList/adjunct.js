(function() {
    var okButton = makeButton($('ok'), null);

    function updateOk(form) {
        function state() {
            if ($('name').value.length == 0) {
                return true;
            }
            var radio = form.elements['mode'];
            if (radio.length == 2)  return false; // this means we only have dummy checkboxes
            for (i=0; i<radio.length; i++) {
                if (radio[i].checked) {
                    return false;
                }
            }
            return true;
        }
        okButton.set('disabled', state(), false);
    }

    function update() {
        updateOk(this.form);
    }

    $('name').onchange = update;
    $('name').onkeyup = update;

    var copy = $('copy')
    if (copy) {
        $('copy').onchange = update;
        $('copy').onclick = update;
    }

    Behaviour.specify(".mode-radio-button", 'form-scripts', 0, function(e) {
        e.onchange = update;
        e.onclick = update;
    });

    var from = $('from');
    if (from) {
        from.onfocus = function() { $('copy').click(); };
    }

    updateOk(okButton.getForm());
    $('name').focus();
})();
