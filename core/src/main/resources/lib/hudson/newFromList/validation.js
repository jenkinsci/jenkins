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