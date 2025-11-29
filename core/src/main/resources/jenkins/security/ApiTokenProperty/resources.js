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

// eslint-disable-next-line no-unused-vars
function changeTokenCallback(newValue) {
  document.getElementById("apiToken").value = newValue;
}

function revokeToken(anchorRevoke) {
  const tokenRow = anchorRevoke.closest(".token-card");
  const confirmMessage = anchorRevoke.getAttribute("data-confirm");
  const confirmTitle = anchorRevoke.getAttribute("data-confirm-title");
  const targetUrl = anchorRevoke.getAttribute("data-target-url");
  const tokenUuid = tokenRow.id;

  dialog
    .confirm(confirmTitle, {
      message: confirmMessage,
      type: "destructive",
      okText: anchorRevoke.dataset.buttonText,
    })
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
  ".api-token-property-add",
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
  const apiTokenRow = rowTemplate.firstElementChild.cloneNode(true);
  const tokenList = document.getElementById("api-token-list");
  const tokenShowButton = apiTokenRow.querySelector(
    ".api-token-property-token-show",
  );
  apiTokenRow.id = data.tokenUuid;
  apiTokenRow.querySelector(".token-name").innerText = data.tokenName;
  tokenShowButton.dataset.tokenValue = data.tokenValue;
  tokenShowButton.dataset.title = data.tokenName;
  tokenShowButton.classList.remove("jenkins-hidden");
  tokenList.appendChild(apiTokenRow);
  adjustTokenEmptyListMessage();
  Behaviour.applySubtree(apiTokenRow);
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
                appendTokenToTable(json.data);
                showToken(
                  json.data.tokenName,
                  json.data.tokenValue,
                  button.dataset.buttonDone,
                );
              }
            });
          }
        });
      },
      () => {},
    );
}

function showToken(tokenName, tokenValue, doneText) {
  const tokenTemplate = document.getElementById("api-token-template");
  const apiTokenMessage = tokenTemplate.firstElementChild.cloneNode(true);

  const tokenValueSpan = apiTokenMessage.querySelector(".api-token-new-value");
  tokenValueSpan.innerText = tokenValue;

  if (isSecureContext) {
    const tokenCopyButton = apiTokenMessage.querySelector(
      ".jenkins-copy-button",
    );
    tokenCopyButton.setAttribute("text", tokenValue);
    tokenCopyButton.classList.remove("jenkins-hidden");
  }
  Behaviour.applySubtree(apiTokenMessage);

  dialog.alert(tokenName, {
    content: apiTokenMessage,
    okText: doneText,
    maxWidth: "500px",
  });
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
  const tokenRow = button.closest(".token-card");
  const promptValue = tokenRow.querySelector(".token-name").innerText;
  dialog
    .prompt(button.dataset.renameTitle, {
      message: button.dataset.renameMessage,
      okText: button.dataset.buttonText,
      promptValue: promptValue,
      maxWidth: "400px",
      minWidth: "400px",
    })
    .then(
      (newName) => {
        fetch(targetUrl, {
          body: new URLSearchParams({
            newName: newName,
            tokenUuid: tokenRow.id,
          }),
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
                const tokenShowButton = tokenRow.querySelector(
                  ".api-token-property-token-show",
                );
                tokenShowButton.dataset.title = newName;
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
  const apiTokenList = document.getElementById("api-token-list");
  const apiTokenContainer = document.getElementById("api-tokens");

  // number of token that are already existing or freshly created
  const numOfToken = apiTokenList.childElementCount;
  if (numOfToken >= 1) {
    emptyListMessage.classList.toggle("jenkins-hidden", true);
    apiTokenContainer.classList.toggle("jenkins-hidden", false);
  } else {
    emptyListMessage.classList.toggle("jenkins-hidden", false);
    apiTokenContainer.classList.toggle("jenkins-hidden", true);
  }
}

Behaviour.specify(
  ".api-token-property-token-show",
  "api-token-property-token-show",
  0,
  function (button) {
    button.onclick = function () {
      showToken(
        button.dataset.title,
        button.dataset.tokenValue,
        button.dataset.buttonDone,
      );
    };
  },
);
