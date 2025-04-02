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
Behaviour.specify(
  ".api-token-property-token-revoke",
  "api-token-property-token-revoke",
  0,
  function (button) {
    button.onclick = function () {
      revokeToken(button);
    };
  },
);

function revokeToken(anchorRevoke) {
  const tokenRow = anchorRevoke.closest("tr");
  const confirmMessage = anchorRevoke.getAttribute("data-confirm");
  const confirmTitle = anchorRevoke.getAttribute("data-confirm-title");
  const targetUrl = anchorRevoke.getAttribute("data-target-url");
  const tokenUuid = tokenRow.dataset.tokenUuid;

  dialog
    .confirm(confirmTitle, { message: confirmMessage, type: "destructive" })
    .then(
      () => {
        fetch(targetUrl, {
          body: new URLSearchParams({ tokenUuid: tokenUuid }),
          method: "post",
          headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
          }),
        }).then((rsp) => {
          if (rsp.ok) {
            if (tokenRow.classList.contains("legacy-token")) {
              // we are revoking the legacy token
              const messageIfLegacyRevoked = anchorRevoke.getAttribute(
                "data-message-if-legacy-revoked",
              );

              const legacyInput = document.getElementById("apiToken");
              legacyInput.value = messageIfLegacyRevoked;
            }
            tokenRow.remove();
            adjustTokenEmptyListMessage();
          }
        });
      },
      () => {},
    );
}

Behaviour.specify(
  "#api-token-property-add",
  "api-token-property-add",
  0,
  function (button) {
    button.onclick = function () {
      addToken(button);
    };
  },
);

function appendTokenToTable(data) {
  const rowTemplate = document.getElementById("api-token-row-template");
  const apiTokenRow = rowTemplate.content.firstElementChild.cloneNode(true);
  apiTokenRow.dataset.tokenUuid = data.tokenUuid;
  apiTokenRow.querySelector(".token-name").innerText = data.tokenName;
  const table = document.getElementById("api-token-table");
  table.tBodies[0].appendChild(apiTokenRow);
  adjustTokenEmptyListMessage();
  Behaviour.applySubtree(table);
}

function addToken(button) {
  const targetUrl = button.dataset.targetUrl;
  const promptMessage = button.dataset.promptMessage;
  const promptName = button.dataset.messageName;
  dialog
    .prompt(promptMessage, {
      message: promptName,
      okText: button.dataset.generate,
      cancelText: button.dataset.cancel,
      maxWidth: "400px",
      minWidth: "400px",
  })
    .then(
      (tokenName) => {
        fetch(targetUrl, {
          body: new URLSearchParams({ newTokenName: tokenName }),
          method: "post",
          headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
          }),
        }).then((rsp) => {
          if (rsp.ok) {
            rsp.json().then((json) => {
              if (json.status === "error") {
                dialog.alert(json.message, {
                  type: "destructive",
                });
              } else {
                const tokenTemplate =
                  document.getElementById("api-token-template");
                const form = document.createElement("form");
                const apiTokenFormInner =
                  tokenTemplate.firstElementChild.cloneNode(true);
                form.appendChild(apiTokenFormInner);

                const tokenValue = json.data.tokenValue;
                const tokenValueSpan = form.querySelector(
                  ".api-token-new-value",
                );
                1;
                tokenValueSpan.innerText = tokenValue;

                const tokenCopyButton = form.querySelector(
                  ".jenkins-copy-button",
                );
                tokenCopyButton.setAttribute("text", tokenValue);
                tokenCopyButton.classList.remove("jenkins-hidden");

                dialog
                  .form(form, {
                    title: json.data.tokenName,
                    submitButton: false,
                    cancel: false,
                    okText: "Ok",
                  })
                  .then(
                    () => {
                      appendTokenToTable(json.data);
                    },
                    () => {
                      appendTokenToTable(json.data);
                    },
                  );
              }
            });
          }
        });
      },
      () => {},
    );
}

Behaviour.specify(
  ".api-token-property-token-rename",
  "api-token-property-token-rename",
  0,
  function (button) {
    button.onclick = function () {
      renameToken(button);
    };
  },
);

function renameToken(button) {
  const targetUrl = button.dataset.targetUrl;
  const tokenRow = button.closest("tr");
  const promptValue = tokenRow.querySelector(".token-name").innerText;
  dialog
    .prompt(button.dataset.renameTitle, {
      message: button.dataset.renameMessage,
      promptValue: promptValue,
      maxWidth: "400px",
      minWidth: "400px",
    })
    .then(
      (newName) => {
        const tokenUuid = tokenRow.dataset.tokenUuid;
        fetch(targetUrl, {
          body: new URLSearchParams({ newName: newName, tokenUuid: tokenUuid }),
          method: "post",
          headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
          }),
        }).then((rsp) => {
          if (rsp.ok) {
            rsp.json().then((json) => {
              if (json.status === "error") {
                dialog.alert(json.message, {
                  type: "destructive",
                });
              } else {
                const tokenField = tokenRow.querySelector(".token-name");
                tokenField.innerText = newName;
              }
            });
          }
        });
      },
      () => {},
    );
}

function adjustTokenEmptyListMessage() {
  const tokenList = document.querySelector(".token-list");
  const emptyListMessage = tokenList.querySelector(".token-list-empty-item");
  const tokenTable = document.getElementById("api-token-table");

  // number of token that are already existing or freshly created
  const numOfToken = tokenTable.tBodies[0].rows.length;
  if (numOfToken >= 1) {
    emptyListMessage.classList.toggle("jenkins-hidden", true);
    tokenTable.classList.toggle("jenkins-hidden", false);
  } else {
    emptyListMessage.classList.toggle("jenkins-hidden", false);
    tokenTable.classList.toggle("jenkins-hidden", true);
  }
}
