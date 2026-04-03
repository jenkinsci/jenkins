function postAndRemove(url, params, button) {
  fetch(url, {
    method: "POST",
    cache: "no-cache",
    headers: crumb.wrap({
      "Content-Type": "application/x-www-form-urlencoded",
    }),
    body: new URLSearchParams(params),
  })
    .then(function (response) {
      if (response.ok) {
        var parent = button.closest(".app-adminmonitor");
        if (parent) {
          parent.remove();
        }
      } else {
        console.warn(
          "AdminMonitor request failed with status " + response.status,
        );
      }
    })
    .catch(function (error) {
      console.warn("AdminMonitor request failed: " + error);
    });
}

function addDismissFormHandling(form) {
  var actionSelect = form.querySelector('select[name="dismissAction"]');
  var dateInput = form.querySelector('input[name="snoozeUntil"]');
  var scopeContainer = form.querySelector('[data-name="scopeSelect"]');
  var submitButton = form.querySelector('button[data-id="ok"]');

  actionSelect.addEventListener("change", function () {
    var value = actionSelect.value;
    if (value === "custom") {
      dateInput.classList.remove("jenkins-hidden");
      scopeContainer.classList.remove("jenkins-hidden");
      if (submitButton) {
        submitButton.disabled = !dateInput.value;
      }
    } else {
      dateInput.classList.add("jenkins-hidden");
      scopeContainer.classList.add("jenkins-hidden");
      if (submitButton) {
        submitButton.disabled = false;
      }
    }
  });

  dateInput.addEventListener("change", function () {
    if (submitButton) {
      submitButton.disabled = !dateInput.value;
    }
  });
}

function openDismissDialog(button) {
  var disableUrl = button.dataset.url;
  var snoozeUrl = button.dataset.snoozeUrl;
  var dialogTitle = button.dataset.dialogTitle || "Dismiss";
  var dialogOk = button.dataset.dialogOk || "Confirm";
  var dialogCancel = button.dataset.dialogCancel || "Cancel";

  var templateEl = document.getElementById("adminmonitor-dismiss-template");
  if (!templateEl) {
    // Fallback: direct dismiss if no dialog template
    postAndRemove(disableUrl, {}, button);
    return;
  }

  var formContent = templateEl.firstElementChild.cloneNode(true);
  var form = document.createElement("form");
  form.appendChild(formContent);

  // Set date constraints
  var dateInput = form.querySelector('input[name="snoozeUntil"]');
  var tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  var maxDate = new Date();
  maxDate.setDate(maxDate.getDate() + 365);
  dateInput.min = tomorrow.toISOString().split("T")[0];
  dateInput.max = maxDate.toISOString().split("T")[0];

  addDismissFormHandling(form);

  dialog
    .form(form, {
      title: dialogTitle,
      okText: dialogOk,
      cancelText: dialogCancel,
      submitButton: false,
      maxWidth: "450px",
      minWidth: "350px",
    })
    .then(
      function (formData) {
        var action = formData.get("dismissAction");

        if (action === "dismiss-all") {
          postAndRemove(disableUrl, {}, button);
        } else if (action === "custom") {
          var scope = formData.get("scope") || "all";
          postAndRemove(
            snoozeUrl,
            {
              durationPreset: "custom",
              snoozeUntil: formData.get("snoozeUntil"),
              scope: scope,
            },
            button,
          );
        } else {
          // Preset: "snooze-{scope}-{minutes}" e.g. "snooze-all-1440"
          var parts = action.split("-");
          if (parts.length < 3) {
            console.warn("Invalid snooze preset format: " + action);
            return;
          }
          var scope = parts[1];
          var minutes = parts[2];
          postAndRemove(
            snoozeUrl,
            {
              durationPreset: minutes,
              scope: scope,
            },
            button,
          );
        }
      },
      function () {
        // Dialog cancelled
      },
    );
}

Behaviour.specify(
  ".app-adminmonitor-dismiss-button",
  "admin-monitor",
  0,
  function (element) {
    element.addEventListener("click", function (event) {
      event.preventDefault();
      openDismissDialog(element);
    });
  },
);
