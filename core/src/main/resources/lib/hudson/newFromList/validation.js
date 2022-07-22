function updateOk() {
    function state() {
        let form = document.getElementById("createItemForm");
        let nameInput = document.getElementById("name");
        let radios = form.querySelectorAll('input[type="radio"]');

        if (nameInput.value.length === 0) {
            return true;
        }

        // this means we only have dummy checkboxes
        if (radios.length === 2) {
            return true;
        }

        for (i = 0; i < radios.length; i++) {
            if (radios[i].checked) {
                return false;
            }
        }

        return true;
    }

    document.getElementById("ok").disabled = state();
}

updateOk();

document.addEventListener('DOMContentLoaded', function() {
  let nameField = document.getElementById('name');
  nameField.focus();
  nameField.addEventListener('change', function () {
    updateOk();
  });
  nameField.addEventListener('keyup', function () {
    updateOk();
  });

  document.querySelectorAll('.mode-selection').forEach(function(el) {
    el.addEventListener('change', function () {
      updateOk();
    });
    el.addEventListener('click', function () {
      updateOk();
    });
  });
  let copyRadio = document.getElementById('copy');
  if (copyRadio !== null) {
    copyRadio.addEventListener('click', function () {
      window.setTimeout(function() {
        document.querySelector('.copy-field').focus();
      }, 100);
    });
  }
});
