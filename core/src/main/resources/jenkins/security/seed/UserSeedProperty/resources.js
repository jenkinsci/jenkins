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
function resetSeed(button) {
  var userSeedPanel = button.closest(".user-seed-panel");
  var confirmMessage = button.getAttribute("data-confirm");
  var confirmTitle = button.getAttribute("data-confirm-title");
  var targetUrl = button.getAttribute("data-target-url");
  var redirectAfterClick = button.getAttribute("data-redirect-url");

  var warningMessage = userSeedPanel.querySelector(".display-after-reset");
  if (warningMessage.classList.contains("visible")) {
    warningMessage.classList.remove("visible");
  }

  dialog
    .confirm(confirmTitle, { message: confirmMessage, type: "destructive" })
    .then(() => {
      fetch(targetUrl, {
        method: "post",
        headers: crumb.wrap({}),
      }).then((rsp) => {
        if (rsp.ok) {
          if (redirectAfterClick) {
            window.location.href = redirectAfterClick;
          } else {
            if (!warningMessage.classList.contains("visible")) {
              warningMessage.classList.add("visible");
            }
          }
        }
      });
    });
}

(function () {
  document.addEventListener("DOMContentLoaded", function () {
    document
      .getElementById("user-seed-property-reset-seed")
      .addEventListener("click", function (event) {
        resetSeed(event.target);
      });
  });
})();
