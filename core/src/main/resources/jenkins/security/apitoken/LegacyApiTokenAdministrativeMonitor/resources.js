/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
function selectAll(anchor) {
  var parent = anchor.closest(".legacy-token-usage");
  var allCheckBoxes = parent.querySelectorAll(".token-to-revoke");
  var concernedCheckBoxes = allCheckBoxes;

  checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes);
}

function selectFresh(anchor) {
  var parent = anchor.closest(".legacy-token-usage");
  var allCheckBoxes = parent.querySelectorAll(".token-to-revoke");
  var concernedCheckBoxes = parent.querySelectorAll(
    ".token-to-revoke.fresh-token",
  );

  checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes);
}

function selectRecent(anchor) {
  var parent = anchor.closest(".legacy-token-usage");
  var allCheckBoxes = parent.querySelectorAll(".token-to-revoke");
  var concernedCheckBoxes = parent.querySelectorAll(
    ".token-to-revoke.recent-token",
  );

  checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes);
}

function checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes) {
  var mustCheck = false;
  for (let i = 0; i < concernedCheckBoxes.length && !mustCheck; i++) {
    let checkBox = concernedCheckBoxes[i];
    if (!checkBox.checked) {
      mustCheck = true;
    }
  }

  for (let i = 0; i < allCheckBoxes.length; i++) {
    let checkBox = allCheckBoxes[i];
    checkBox.checked = false;
  }

  for (let i = 0; i < concernedCheckBoxes.length; i++) {
    let checkBox = concernedCheckBoxes[i];
    checkBox.checked = mustCheck;
  }

  for (let i = 0; i < allCheckBoxes.length; i++) {
    let checkBox = allCheckBoxes[i];
    onCheckChanged(checkBox);
  }
}

function confirmAndRevokeAllSelected(button) {
  var parent = button.closest(".legacy-token-usage");
  var allCheckBoxes = parent.querySelectorAll(".token-to-revoke");
  var allCheckedCheckBoxes = [];
  for (let i = 0; i < allCheckBoxes.length; i++) {
    let checkBox = allCheckBoxes[i];
    if (checkBox.checked) {
      allCheckedCheckBoxes.push(checkBox);
    }
  }

  if (allCheckedCheckBoxes.length === 0) {
    var nothingSelected = button.getAttribute("data-nothing-selected");
    dialog.alert(nothingSelected);
  } else {
    var confirmTitle = button.getAttribute("data-confirm-title");
    var confirmMessageTemplate = button.getAttribute("data-confirm-template");
    var confirmMessage = confirmMessageTemplate.replace(
      "%num%",
      allCheckedCheckBoxes.length,
    );
    dialog
      .confirm(confirmTitle, { message: confirmMessage, type: "destructive" })
      .then(
        () => {
          var url = button.getAttribute("data-url");
          var selectedValues = [];

          for (var i = 0; i < allCheckedCheckBoxes.length; i++) {
            var checkBox = allCheckedCheckBoxes[i];
            var userId = checkBox.getAttribute("data-user-id");
            var uuid = checkBox.getAttribute("data-uuid");
            selectedValues.push({ userId: userId, uuid: uuid });
          }

          fetch(url, {
            method: "post",
            body: JSON.stringify({ values: selectedValues }),
            headers: crumb.wrap({ "Content-Type": "application/json" }),
          }).then(() => window.location.reload());
        },
        () => {},
      );
  }
}

function onLineClicked(event) {
  var line = this;
  var checkBox = line.querySelector(".token-to-revoke");
  // to allow click on checkbox to act normally
  if (event.target === checkBox) {
    return;
  }
  checkBox.checked = !checkBox.checked;
  onCheckChanged(checkBox);
}

function onCheckChanged(checkBox) {
  var line = checkBox.closest("tr");
  if (checkBox.checked) {
    line.classList.add("selected");
  } else {
    line.classList.remove("selected");
  }
}

(function () {
  document.addEventListener("DOMContentLoaded", function () {
    var allLines = document.querySelectorAll(".legacy-token-usage table tr");
    for (let i = 0; i < allLines.length; i++) {
      let line = allLines[i];
      if (!line.classList.contains("no-token-line")) {
        line.onclick = onLineClicked;
      }
    }

    var allCheckBoxes = document.querySelectorAll(".token-to-revoke");
    for (let i = 0; i < allCheckBoxes.length; i++) {
      let checkBox = allCheckBoxes[i];
      checkBox.onchange = function () {
        onCheckChanged(this);
      };
    }

    document
      .getElementById("legacy-api-token-monitor-select-all")
      .addEventListener("click", function (event) {
        event.preventDefault();
        selectAll(event.target);
      });

    document
      .getElementById("legacy-api-token-monitor-select-fresh")
      .addEventListener("click", function (event) {
        event.preventDefault();
        selectFresh(event.target);
      });

    document
      .getElementById("legacy-api-token-monitor-select-recent")
      .addEventListener("click", function (event) {
        event.preventDefault();
        selectRecent(event.target);
      });

    document
      .querySelector(".action-revoke-selected")
      .addEventListener("click", function (event) {
        confirmAndRevokeAllSelected(event.target);
      });
  });
})();
