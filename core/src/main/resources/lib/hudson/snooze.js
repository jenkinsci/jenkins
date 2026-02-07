/*
 * The MIT License
 *
 * Copyright (c) 2025 Jenkins Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

Behaviour.specify(".snooze-button", "snooze-button", 0, function (button) {
  button.onclick = function () {
    openSnoozeDialog(button);
  };
});

function addSnoozeFormHandling(form) {
  var durationSelect = form.querySelector('select[name="durationPreset"]');
  var customDateInput = form.querySelector('input[name="snoozeUntil"]');
  var submitButton = form.querySelector('button[data-id="ok"]');

  durationSelect.addEventListener("change", function () {
    if (durationSelect.value === "custom") {
      customDateInput.classList.remove("jenkins-hidden");
      if (customDateInput.value !== "") {
        submitButton.disabled = false;
      } else {
        submitButton.disabled = true;
      }
    } else {
      submitButton.disabled = false;
      customDateInput.classList.add("jenkins-hidden");
    }
  });

  customDateInput.addEventListener("change", function () {
    if (customDateInput.value !== "") {
      submitButton.disabled = false;
    } else {
      submitButton.disabled = true;
    }
  });
}

function openSnoozeDialog(button) {
  var snoozeUrl = button.dataset.snoozeUrl;
  var templateId = button.dataset.templateId;
  var dialogTitle = button.dataset.title;
  var snoozeText = button.dataset.buttonSnooze;
  var cancelText = button.dataset.buttonCancel;

  var formTemplate = document
    .getElementById(templateId)
    .firstElementChild.cloneNode(true);
  var form = document.createElement("form");

  // Set date constraints
  var dateInput = formTemplate.querySelector('input[name="snoozeUntil"]');
  var now = new Date();
  var tomorrow = new Date();
  var nextYear = new Date();
  var presetDate = new Date();
  tomorrow.setDate(now.getDate() + 1);
  presetDate.setDate(now.getDate() + 1);
  nextYear.setDate(now.getDate() + 365);
  dateInput.min = tomorrow.toISOString().split("T")[0];
  dateInput.max = nextYear.toISOString().split("T")[0];
  dateInput.value = presetDate.toISOString().split("T")[0];

  form.appendChild(formTemplate);
  addSnoozeFormHandling(form);

  dialog
    .form(form, {
      title: dialogTitle,
      okText: snoozeText,
      cancelText: cancelText,
      submitButton: false,
      maxWidth: "450px",
      minWidth: "350px",
    })
    .then(
      function (formData) {
        // Get crumb directly from document head
        var crumbHeaderName = document.head.getAttribute("data-crumb-header");
        var crumbValue = document.head.getAttribute("data-crumb-value");

        var params = {
          durationPreset: formData.get("durationPreset"),
          snoozeUntil: formData.get("snoozeUntil"),
        };
        // Add crumb to body
        if (crumbHeaderName && crumbValue) {
          params[crumbHeaderName] = crumbValue;
        }

        var headers = {
          "Content-Type": "application/x-www-form-urlencoded",
        };
        // Add crumb to headers
        if (crumbHeaderName && crumbValue) {
          headers[crumbHeaderName] = crumbValue;
        }

        fetch(snoozeUrl, {
          body: new URLSearchParams(params),
          method: "post",
          headers: headers,
        }).then(function (rsp) {
          if (rsp.ok) {
            // Find and hide the monitor's parent element
            var monitorElement = button.closest(".jenkins-alert");
            if (monitorElement) {
              monitorElement.style.display = "none";
            } else {
              // Fallback: reload the page
              window.location.reload();
            }
          } else {
            rsp.text().then(function (msg) {
              dialog.alert("Error", {
                message: msg || "Failed to snooze monitor",
                type: "destructive",
              });
            });
          }
        });
      },
      function () {
        // Dialog cancelled - do nothing
      },
    );
}
